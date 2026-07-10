package com.ecopower.backend.client;

import com.ecopower.backend.model.external.GenerationResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CarbonIntensityClient {

    private final RestClient restClient;

    public CarbonIntensityClient() {
        // Konfigurujemy bazowy adres API oraz nagłówek Accept, ponieważ brytyjskie API tego wymaga
        this.restClient = RestClient.builder()
                .baseUrl("https://api.carbonintensity.org.uk")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Pobiera miks energetyczny dla określonego przedziału czasowego.
     * Daty 'from' oraz 'to' muszą być w formacie ISO 8601 UTC (np. 2026-07-08T00:00Z)
     */
    public GenerationResponse getGenerationMix(String from, String to) {
        return this.restClient.get()
                .uri("/generation/{from}/{to}", from, to)
                .retrieve()
                .body(GenerationResponse.class);
    }
}