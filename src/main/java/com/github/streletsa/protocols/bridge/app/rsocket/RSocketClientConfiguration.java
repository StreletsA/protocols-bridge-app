package com.github.streletsa.protocols.bridge.app.rsocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.MimeTypeUtils;
import reactor.util.retry.Retry;

import java.time.Duration;

@Configuration
public class RSocketClientConfiguration {
    @Bean("json-rsocket-requester")
    public RSocketRequester getRSocketRequester(RSocketStrategies strategies,
                                                @Value("${bridge.rsocket.server.host}") String rsocketServerHost,
                                                @Value("${bridge.rsocket.server.port}") int rsocketServerPort){
        RSocketRequester.Builder builder = RSocketRequester.builder();

        return builder
                .rsocketConnector(
                        rSocketConnector ->
                                rSocketConnector.reconnect(Retry.fixedDelay(2, Duration.ofSeconds(2)))
                )
                .rsocketStrategies(strategies)
                .dataMimeType(MimeTypeUtils.APPLICATION_JSON)
                .tcp(rsocketServerHost, rsocketServerPort);
    }

    @Bean
    public RSocketStrategiesCustomizer protobufRSocketStrategyCustomizer() {
        return (strategy) -> {
            strategy.decoder(new Jackson2JsonDecoder());
            strategy.encoder(new Jackson2JsonEncoder());
        };
    }
}
