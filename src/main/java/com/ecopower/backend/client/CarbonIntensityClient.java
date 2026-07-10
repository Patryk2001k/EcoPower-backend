package com.ecopower.backend.client;

import com.ecopower.backend.model.external.GenerationResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Component
public class CarbonIntensityClient {

    private final RestClient restClient;
    private static final String BASE_URL = "https://api.carbonintensity.org.uk";

    public CarbonIntensityClient() {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
    public GenerationResponse getGenerationMix(String from, String to) {
        URI uri = URI.create(BASE_URL + "/generation/" + from + "/" + to);
        return this.restClient.get()
                .uri(uri)
                .retrieve()
                .body(GenerationResponse.class);
    }
}