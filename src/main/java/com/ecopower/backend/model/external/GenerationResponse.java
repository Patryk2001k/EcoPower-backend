package com.ecopower.backend.model.external;

import java.util.List;

public record GenerationResponse(
    List<IntervalData> data
) {}