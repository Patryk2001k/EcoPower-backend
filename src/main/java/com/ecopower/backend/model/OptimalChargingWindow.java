package com.ecopower.backend.model;

import java.time.OffsetDateTime;

public record OptimalChargingWindow(
    OffsetDateTime start,
    OffsetDateTime end,
    Double averageCleanEnergyPercentage
) {}