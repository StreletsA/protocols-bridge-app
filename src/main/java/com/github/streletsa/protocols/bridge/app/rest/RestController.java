package com.github.streletsa.protocols.bridge.app.rest;

import com.github.streletsa.protocols.bridge.app.common.Constants;
import com.github.streletsa.protocols.bridge.app.dto.TestDto;
import com.github.streletsa.protocols.bridge.app.quic.QuicClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@org.springframework.web.bind.annotation.RestController
@RequestMapping(path = Constants.API)
public class RestController {
    private final RSocketRequester rSocketRequester;
    private final QuicClient quicClient;

    @Value("${bridge.protocol}")
    private String bridgeProtocol;

    @Value("${bridge.rsocket.channel}")
    private String rsocketChannel;

    @Autowired
    public RestController(RSocketRequester rSocketRequester, QuicClient quicClient) {
        this.rSocketRequester = rSocketRequester;
        this.quicClient = quicClient;
    }

    @GetMapping(path = Constants.EXTERNAL)
    TestDto external() throws InterruptedException {
        switch (bridgeProtocol) {
            case Constants.RSOCKET -> {
                return rSocketRequester.route(rsocketChannel)
                                                       .retrieveMono(TestDto.class)
                                                       .block();
            }
            case Constants.QUIC -> {
                return quicClient.sendRequest(HttpMethod.GET, "/", null, TestDto.class)
                                 .getReceivedContent();
            }
            default -> throw new RuntimeException("Incorrect value of property 'bridge.protocol'");
        }
    }

    @GetMapping(path = Constants.INTERNAL)
    TestDto internal() {
        return TestDto.from(UUID.randomUUID().toString());
    }
}
