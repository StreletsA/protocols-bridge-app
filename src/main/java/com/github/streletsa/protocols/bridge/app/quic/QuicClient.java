package com.github.streletsa.protocols.bridge.app.quic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.streletsa.protocols.bridge.app.common.RequestBody;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.http3.DefaultHttp3DataFrame;
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3ClientConnectionHandler;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import jakarta.annotation.Nullable;
import com.github.streletsa.protocols.bridge.app.common.Result;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
public class QuicClient {
    private final String quicServerHost;
    private final int quicServerPort;
    private final int quicClientPort;

    private Channel channel;

    public QuicClient(@Value("${bridge.quic.server.host}") String quicServerHost,
                      @Value("${bridge.quic.server.port}") int quicServerPort,
                      @Value("${bridge.quic.client.port}") int quicClientPort) throws InterruptedException {
        this.quicServerHost = quicServerHost;
        this.quicServerPort = quicServerPort;
        this.quicClientPort = quicClientPort;

        openChannel();
    }

    private void openChannel() throws InterruptedException {
        QuicSslContext context = QuicSslContextBuilder.forClient()
                                                      .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                                      .applicationProtocols(Http3.supportedApplicationProtocols()).build();
        ChannelHandler codec = Http3.newQuicClientCodecBuilder()
                                    .sslContext(context)
                                    .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                                    .initialMaxData(10000000)
                                    .initialMaxStreamDataBidirectionalLocal(1000000)
                                    .build();
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        Bootstrap bs = new Bootstrap();

        this.channel = bs.group(group)
                         .channel(NioDatagramChannel.class)
                         .handler(codec)
                         .bind(quicClientPort).sync().channel();
    }

    public <T> Result<T> sendRequest(HttpMethod method,
                                     String path,
                                     @Nullable RequestBody<?> requestBody,
                                     @Nullable Class<T> responseType) throws InterruptedException {
        try {
            if (!this.channel.isOpen()) {
                openChannel();
            }

            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                                          .handler(new Http3ClientConnectionHandler())
                                          .remoteAddress(new InetSocketAddress(this.quicServerHost, this.quicServerPort))
                                          .connect()
                                          .get();
            CustomHttp3ClientHandler<T> handler = new CustomHttp3ClientHandler<>(responseType);
            QuicStreamChannel streamChannel =
                    Http3.newRequestStream(quicChannel, handler).sync().getNow();

            Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
            headersFrame.headers().method(method.name()).path(path).scheme("").authority("");

            if (requestBody == null) {
                streamChannel.writeAndFlush(headersFrame)
                             .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync();
            } else {
                byte[] contentBytes = requestBody.getContent();
                int byteToWriteIndex = 0;
                ChannelFuture channelFuture = null;

                while (byteToWriteIndex < contentBytes.length) {
                    ByteBuf buffer = Unpooled.buffer();
                    boolean bytesWrote = false;

                    for (int j = 0; j < buffer.capacity(); j++) {
                        if (byteToWriteIndex >= contentBytes.length) {
                            break;
                        }

                        buffer.writeByte(contentBytes[byteToWriteIndex]);
                        byteToWriteIndex++;
                        bytesWrote = true;
                    }

                    if (bytesWrote) {
                        Http3DataFrame dataFrame = new DefaultHttp3DataFrame(buffer);
                        channelFuture = streamChannel.write(dataFrame);
                    }
                }

                if (channelFuture != null) {
                    streamChannel.flush();
                    channelFuture.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync();
                }
            }

            streamChannel.closeFuture().sync();
            quicChannel.close().sync();
            channel.close();

            return handler.getResult();
        } catch (ExecutionException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class CustomHttp3ClientHandler<T> extends Http3RequestStreamInboundHandler {
        private final List<Byte> contentBytes = new ArrayList<>();
        private final Class<T> resultContentType;

        public CustomHttp3ClientHandler(Class<T> resultContentType) {
            this.resultContentType = resultContentType;
        }

        @Override
        protected void channelRead(
                ChannelHandlerContext ctx, Http3HeadersFrame frame, boolean isLast) {
            releaseFrameAndCloseIfLast(ctx, frame, isLast);
        }

        @Override
        protected void channelRead(
                ChannelHandlerContext ctx, Http3DataFrame frame, boolean isLast) {
            frame.content().forEachByte(contentBytes::add);
            releaseFrameAndCloseIfLast(ctx, frame, isLast);
        }

        private void releaseFrameAndCloseIfLast(
                ChannelHandlerContext ctx, Http3RequestStreamFrame frame, boolean isLast) {
            ReferenceCountUtil.release(frame);
            if (isLast) {
                ctx.close();
            }
        }

        public Result<T> getResult() throws IOException {
            try {
                if (this.resultContentType == null) {
                    return Result.success(null, null);
                }

                byte[] bytes = new byte[contentBytes.size()];

                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = contentBytes.get(i);
                }

                ObjectMapper objectMapper = new ObjectMapper();
                T contentObject = objectMapper.readValue(bytes, this.resultContentType);

                return Result.success(this.resultContentType, contentObject);
            } catch (Exception e) {
                return Result.error(this.resultContentType, e.getMessage());
            }
        }
    }

    @PreDestroy
    public void preDestroy() {
        try {
            channel.close();
        } catch (Exception e) {
        }
    }
}
