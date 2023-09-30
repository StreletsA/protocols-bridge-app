package com.github.streletsa.protocols.bridge.app.rsocket;

import com.github.streletsa.protocols.bridge.app.rest.RestClient;
import com.github.streletsa.protocols.bridge.app.common.Constants;
import com.github.streletsa.protocols.bridge.app.dto.TestDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

import java.net.URISyntaxException;

@Controller
public class RSocketController {
    private final RestClient restClient;

    @Value("${bridge.http.test-path}")
    private String testPath;

    public RSocketController(RestClient restClient) {
        this.restClient = restClient;
    }

    @MessageMapping("${bridge.rsocket.channel}")
    public Mono<TestDto> bridge() throws URISyntaxException {
        return Mono.just(restClient.execute(testPath + Constants.API + Constants.INTERNAL));
    }
}
