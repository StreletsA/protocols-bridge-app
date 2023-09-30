package com.github.streletsa.protocols.bridge.app.rest;

import com.github.streletsa.protocols.bridge.app.dto.TestDto;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

@Component
public class RestClient {
    public TestDto execute(String route) throws URISyntaxException {
        RestTemplate restTemplate = new RestTemplate();

        return restTemplate.exchange(new RequestEntity<>(HttpMethod.GET, new URI(route)), TestDto.class).getBody();
    }
}
