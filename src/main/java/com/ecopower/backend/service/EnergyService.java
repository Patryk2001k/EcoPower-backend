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

    // Definiujemy, które źródła energii uznajemy za "czyste" zgodnie z zadaniem
    private static final Set<String> CLEAN_FUELS = Set.of("biomass", "nuclear", "hydro", "wind", "solar");

    public EnergyService(CarbonIntensityClient carbonIntensityClient) {
        this.carbonIntensityClient = carbonIntensityClient;
    }

    /**
     * Pobiera i oblicza uśredniony miks energetyczny dla 3 dni (dzisiaj, jutro, pojutrze).
     */
    public List<DailyEnergyMix> getDailyAverages() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate today = now.toLocalDate();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate dayAfterTomorrow = today.plusDays(2);

        GenerationResponse response = fetchGenerationData(today, dayAfterTomorrow);

        // Grupujemy otrzymane 30-minutowe interwały według daty (LocalDate)
        Map<LocalDate, List<IntervalData>> groupedByDate = response.data().stream()
                .collect(Collectors.groupingBy(interval -> OffsetDateTime.parse(interval.from()).toLocalDate()));

        List<DailyEnergyMix> dailyMixes = new ArrayList<>();

        // Dla każdego z 3 dni obliczamy uśrednione wartości
        for (LocalDate date : List.of(today, tomorrow, dayAfterTomorrow)) {
            List<IntervalData> intervals = groupedByDate.getOrDefault(date, Collections.emptyList());

            if (intervals.isEmpty()) {
                dailyMixes.add(new DailyEnergyMix(date, Map.of(), 0.0));
                continue;
            }

            // Sumujemy procenty dla każdego paliwa
            Map<String, Double> fuelSums = new HashMap<>();
            for (IntervalData interval : intervals) {
                for (FuelMix mix : interval.generationMix()) {
                    fuelSums.put(mix.fuel(), fuelSums.getOrDefault(mix.fuel(), 0.0) + mix.perc());
                }
            }

            int count = intervals.size();
            Map<String, Double> fuelAverages = new HashMap<>();
            double cleanEnergySum = 0.0;

            // Wyliczamy średnie i zaokrąglamy je do 2 miejsc po przecinku
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
     * Implementacja algorytmu Sliding Window do znajdowania optymalnego okna ładowania.
     * Szuka okna o podanej długości w najbliższych dwóch dniach (jutro i pojutrze).
     */
    public OptimalChargingWindow findOptimalChargingWindow(int durationHours) {
        if (durationHours < 1 || durationHours > 6) {
            throw new IllegalArgumentException("Czas ładowania musi wynosić od 1 do 6 godzin.");
        }

        // 1 godzina = 2 interwały (30-minutowe)
        int requiredIntervalsCount = durationHours * 2;

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate today = now.toLocalDate();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate dayAfterTomorrow = today.plusDays(2);

        // Pobieramy dane (potrzebujemy jutro + pojutrze)
        GenerationResponse response = fetchGenerationData(today, dayAfterTomorrow);

        // Filtrujemy dane, zostawiając wyłącznie interwały z jutra i pojutrza, a następnie sortujemy je chronologicznie
        List<IntervalData> forecastIntervals = response.data().stream()
                .filter(interval -> {
                    LocalDate date = OffsetDateTime.parse(interval.from()).toLocalDate();
                    return date.equals(tomorrow) || date.equals(dayAfterTomorrow);
                })
                .sorted(Comparator.comparing(interval -> OffsetDateTime.parse(interval.from())))
                .collect(Collectors.toList());

        if (forecastIntervals.size() < requiredIntervalsCount) {
            throw new IllegalStateException("Niewystarczająca ilość danych prognozowanych do obliczenia okna.");
        }

        double maxAverageCleanEnergy = -1.0;
        int bestStartIndex = 0;

        // Algorytm Sliding Window: przesuwamy okno o stałej szerokości co 1 interwał (30 min)
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

        // Wyciągamy interwał startowy i końcowy na podstawie najlepszego indeksu
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
     * Pomocnicza metoda pobierająca dane dla zadanego zakresu dat.
     */
    private GenerationResponse fetchGenerationData(LocalDate fromDate, LocalDate toDate) {
        String fromStr = fromDate.atStartOfDay(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        // Do daty końcowej dodajemy 23:59:59, aby objąć cały ostatni dzień
        String toStr = toDate.atTime(23, 59, 59).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);

        GenerationResponse response = carbonIntensityClient.getGenerationMix(fromStr, toStr);

        if (response == null || response.data() == null) {
            throw new IllegalStateException("Nie udało się pobrać danych z zewnętrznego API.");
        }
        return response;
    }

    /**
     * Pomocnicza metoda sumująca udział czystej energii w jednym 30-minutowym interwale.
     */
    private double calculateCleanEnergyPercentage(IntervalData interval) {
        return interval.generationMix().stream()
                .filter(mix -> CLEAN_FUELS.contains(mix.fuel()))
                .mapToDouble(FuelMix::perc)
                .sum();
    }
}