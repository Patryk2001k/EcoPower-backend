package com.ecopower.backend.model.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record IntervalData(
    String from,
    String to,
    @JsonProperty("generationmix") List<FuelMix> generationMix
) {}