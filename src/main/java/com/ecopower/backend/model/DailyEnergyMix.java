package com.ecopower.backend.model;

import java.time.LocalDate;
import java.util.Map;

public record DailyEnergyMix(
    LocalDate date,
    Map<String, Double> fuelAverages,
    Double cleanEnergyPercentage
) {}