package com.github.streletsa.protocols.bridge.app.quic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.streletsa.protocols.bridge.app.common.Constants;
import com.github.streletsa.protocols.bridge.app.rest.RestClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.http3.DefaultHttp3DataFrame;
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import jakarta.annotation.PostConstruct;
import com.github.streletsa.protocols.bridge.app.dto.TestDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

@Component
public class QuicServer {
    private final RestClient restClient;
    private final boolean quicServerEnabled;
    private final int port;
    private final URI internalTestURI;

    @Autowired
    public QuicServer(RestClient restClient,
                      @Value("${bridge.quic.server.enabled}") boolean quicServerEnabled,
                      @Value("${bridge.quic.server.port}") int port,
                      @Value("${bridge.http.test-path}") String testPath) {
        this.restClient = restClient;
        this.quicServerEnabled = quicServerEnabled;
        this.port = port;
        this.internalTestURI = URI.create(testPath + Constants.API + Constants.INTERNAL);
    }

    @PostConstruct
    public void init() {
        if (quicServerEnabled) {
            startServerThread();
        }
    }

    private void startServerThread() {
        new Thread(() -> {
            NioEventLoopGroup group = new NioEventLoopGroup(1);
            SelfSignedCertificate cert;

            try {
                cert = new SelfSignedCertificate();
            } catch (CertificateException e) {
                throw new RuntimeException(e);
            }

            QuicSslContext sslContext = QuicSslContextBuilder.forServer(cert.key(), null, cert.cert())
                                                             .applicationProtocols(Http3.supportedApplicationProtocols())
                                                             .build();
            ChannelHandler codec = Http3.newQuicServerCodecBuilder()
                                        .sslContext(sslContext)
                                        .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                                        .initialMaxData(10000000)
                                        .initialMaxStreamDataBidirectionalLocal(1000000)
                                        .initialMaxStreamDataBidirectionalRemote(1000000)
                                        .initialMaxStreamsBidirectional(100)
                                        .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                                        .handler(new ChannelInitializer<QuicChannel>() {
                                            @Override
                                            protected void initChannel(QuicChannel ch) {
                                                // Called for each connection
                                                ch.pipeline().addLast(new Http3ServerConnectionHandler(
                                                        new ChannelInitializer<QuicStreamChannel>() {
                                                            // Called for each request-stream,
                                                            @Override
                                                            protected void initChannel(QuicStreamChannel ch) {
                                                                ch.pipeline().addLast(new TestHttp3ServerHandler(restClient,
                                                                                                                 internalTestURI));
                                                            }
                                                        }));
                                            }
                                        }).build();
            try {
                Bootstrap bs = new Bootstrap();
                Channel channel = bs.group(group)
                                    .channel(NioDatagramChannel.class)
                                    .handler(codec)
                                    .bind(new InetSocketAddress(port)).sync().channel();

                channel.closeFuture().sync();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                group.shutdownGracefully();
            }
        }).start();
    }

    public static class TestHttp3ServerHandler extends Http3RequestStreamInboundHandler {
        private final RestClient restClient;
        private final URI uri;

        public TestHttp3ServerHandler(RestClient restClient,
                                      URI uri) {
            this.restClient = restClient;
            this.uri = uri;
        }

        @Override
        protected void channelRead(ChannelHandlerContext channelHandlerContext,
                                   Http3HeadersFrame http3HeadersFrame,
                                   boolean isLast) {
            if (isLast) {
                writeResponse(channelHandlerContext);
            }

            ReferenceCountUtil.release(http3HeadersFrame);
        }

        @Override
        protected void channelRead(ChannelHandlerContext channelHandlerContext,
                                   Http3DataFrame http3DataFrame,
                                   boolean isLast) {
            if (isLast) {
                writeResponse(channelHandlerContext);
            }

            ReferenceCountUtil.release(http3DataFrame);
        }

        private void writeResponse(ChannelHandlerContext ctx) {
            Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
            headersFrame.headers().status("200");
            headersFrame.headers().set("server", "netty");

            try {
                TestDto testDto = restClient.execute(uri.toString());
                ObjectMapper objectMapper = new ObjectMapper();
                byte[] testDtoBytes = objectMapper.writeValueAsBytes(
                        testDto);

                ctx.write(headersFrame);
                ctx.writeAndFlush(new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(testDtoBytes)))
                   .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
            } catch (Exception e) {
            }
        }
    }
}
