package com.ecopower.backend.service;

import com.ecopower.backend.client.CarbonIntensityClient;
import com.ecopower.backend.model.DailyEnergyMix;
import com.ecopower.backend.model.OptimalChargingWindow;
import com.ecopower.backend.model.external.FuelMix;
import com.ecopower.backend.model.external.GenerationResponse;
import com.ecopower.backend.model.external.IntervalData;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EnergyService {

    private final CarbonIntensityClient carbonIntensityClient;

    // Define which fuel sources are considered "clean" as per requirements
    private static final Set<String> CLEAN_FUELS = Set.of("biomass", "nuclear", "hydro", "wind", "solar");
    private static final DateTimeFormatter API_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'");

    public EnergyService(CarbonIntensityClient carbonIntensityClient) {
        this.carbonIntensityClient = carbonIntensityClient;
    }

    /**
     * Fetches and calculates the averaged energy mix for 3 days (today, tomorrow, day after tomorrow).
     */
    public List<DailyEnergyMix> getDailyAverages() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate today = now.toLocalDate();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate dayAfterTomorrow = today.plusDays(2);

        GenerationResponse response = fetchGenerationData(today, dayAfterTomorrow);

        // Group 30-minute intervals by date (LocalDate)
        Map<LocalDate, List<IntervalData>> groupedByDate = response.data().stream()
                .collect(Collectors.groupingBy(interval -> OffsetDateTime.parse(interval.from()).toLocalDate()));

        List<DailyEnergyMix> dailyMixes = new ArrayList<>();

        // Calculate averages for each of the 3 days
        for (LocalDate date : List.of(today, tomorrow, dayAfterTomorrow)) {
            List<IntervalData> intervals = groupedByDate.getOrDefault(date, Collections.emptyList());

            if (intervals.isEmpty()) {
                dailyMixes.add(new DailyEnergyMix(date, Map.of(), 0.0));
                continue;
            }

            // Sum up percentages for each fuel source
            Map<String, Double> fuelSums = new HashMap<>();
            for (IntervalData interval : intervals) {
                for (FuelMix mix : interval.generationMix()) {
                    fuelSums.put(mix.fuel(), fuelSums.getOrDefault(mix.fuel(), 0.0) + mix.perc());
                }
            }

            int count = intervals.size();
            Map<String, Double> fuelAverages = new HashMap<>();
            double cleanEnergySum = 0.0;

            // Calculate averages and round to 2 decimal places
            for (Map.Entry<String, Double> entry : fuelSums.entrySet()) {
                double avg = entry.getValue() / count;
                double roundedAvg = Math.round(avg * 100.0) / 100.0;
                fuelAverages.put(entry.getKey(), roundedAvg);

                if (CLEAN_FUELS.contains(entry.getKey())) {
                    cleanEnergySum += roundedAvg;
                }
            }

            double roundedCleanEnergy = Math.round(cleanEnergySum * 100.0) / 100.0;
            dailyMixes.add(new DailyEnergyMix(date, fuelAverages, roundedCleanEnergy));
        }

        return dailyMixes;
    }

    /**
     * Sliding Window algorithm to find the optimal charging window.
     * Looks for a window of a given duration in the next two days (tomorrow and day after tomorrow).
     */
    public OptimalChargingWindow findOptimalChargingWindow(int durationHours) {
        if (durationHours < 1 || durationHours > 6) {
            throw new IllegalArgumentException("Charging duration must be between 1 and 6 hours.");
        }

        // 1 hour = 2 intervals (30-minute blocks)
        int requiredIntervalsCount = durationHours * 2;

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate today = now.toLocalDate();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate dayAfterTomorrow = today.plusDays(2);

        // Fetch forecast data
        GenerationResponse response = fetchGenerationData(today, dayAfterTomorrow);

        // Filter intervals to only include tomorrow and the day after, sorted chronologically
        List<IntervalData> forecastIntervals = response.data().stream()
                .filter(interval -> {
                    LocalDate date = OffsetDateTime.parse(interval.from()).toLocalDate();
                    return date.equals(tomorrow) || date.equals(dayAfterTomorrow);
                })
                .sorted(Comparator.comparing(interval -> OffsetDateTime.parse(interval.from())))
                .collect(Collectors.toList());

        if (forecastIntervals.size() < requiredIntervalsCount) {
            throw new IllegalStateException("Insufficient forecast data to calculate charging window.");
        }

        double maxAverageCleanEnergy = -1.0;
        int bestStartIndex = 0;

        // Sliding Window algorithm: shift the window of fixed size by 1 interval (30 min) at a time
        for (int i = 0; i <= forecastIntervals.size() - requiredIntervalsCount; i++) {
            double currentSum = 0.0;

            for (int j = i; j < i + requiredIntervalsCount; j++) {
                currentSum += calculateCleanEnergyPercentage(forecastIntervals.get(j));
            }

            double currentAverage = currentSum / requiredIntervalsCount;

            if (currentAverage > maxAverageCleanEnergy) {
                maxAverageCleanEnergy = currentAverage;
                bestStartIndex = i;
            }
        }

        // Retrieve start and end intervals based on the best index
        IntervalData startInterval = forecastIntervals.get(bestStartIndex);
        IntervalData endInterval = forecastIntervals.get(bestStartIndex + requiredIntervalsCount - 1);

        double roundedAverage = Math.round(maxAverageCleanEnergy * 100.0) / 100.0;

        return new OptimalChargingWindow(
                OffsetDateTime.parse(startInterval.from()),
                OffsetDateTime.parse(endInterval.to()),
                roundedAverage
        );
    }

    /**
     * Helper method to fetch generation mix data for a given date range.
     */
    private GenerationResponse fetchGenerationData(LocalDate fromDate, LocalDate toDate) {
        // Formatujemy daty stricte według formatu API_DATE_FORMATTER (bez sekund!)
        String fromStr = fromDate.atStartOfDay(ZoneOffset.UTC).format(API_DATE_FORMATTER);
        String toStr = toDate.atTime(23, 59).atOffset(ZoneOffset.UTC).format(API_DATE_FORMATTER);

        GenerationResponse response = carbonIntensityClient.getGenerationMix(fromStr, toStr);

        if (response == null || response.data() == null) {
            throw new IllegalStateException("Failed to fetch data from the external API.");
        }
        return response;
    }

    /**
     * Helper method to calculate the clean energy percentage in a single 30-minute interval.
     */
    private double calculateCleanEnergyPercentage(IntervalData interval) {
        return interval.generationMix().stream()
                .filter(mix -> CLEAN_FUELS.contains(mix.fuel()))
                .mapToDouble(FuelMix::perc)
                .sum();
    }
}