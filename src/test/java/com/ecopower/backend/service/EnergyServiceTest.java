package com.ecopower.backend.service;

import com.ecopower.backend.client.CarbonIntensityClient;
import com.ecopower.backend.model.DailyEnergyMix;
import com.ecopower.backend.model.OptimalChargingWindow;
import com.ecopower.backend.model.external.FuelMix;
import com.ecopower.backend.model.external.GenerationResponse;
import com.ecopower.backend.model.external.IntervalData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnergyServiceTest {

    @Mock
    private CarbonIntensityClient carbonIntensityClient;

    @InjectMocks
    private EnergyService energyService;

    private LocalDate today;
    private LocalDate tomorrow;
    private LocalDate dayAfterTomorrow;

    @BeforeEach
    void setUp() {
        // Wyznaczamy daty dynamicznie względem uruchomienia testu
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        today = now.toLocalDate();
        tomorrow = today.plusDays(1);
        dayAfterTomorrow = today.plusDays(2);
    }

    @Test
    void getDailyAverages_ShouldCalculateCorrectlyAndRoundToTwoDecimals() {
        // Given - Przygotowujemy dane testowe dla "dzisiaj" z różnymi udziałami energii
        OffsetDateTime t1 = today.atTime(12, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime t2 = today.atTime(12, 30).atOffset(ZoneOffset.UTC);

        // Interwał 1: Wiatr = 40.0%, Gaz = 60.0% (Czysta energia = 40%)
        IntervalData interval1 = createMockInterval(t1, t1.plusMinutes(30), 40.0, 60.0);
        // Interwał 2: Wiatr = 50.5%, Gaz = 49.5% (Czysta energia = 50.5%)
        IntervalData interval2 = createMockInterval(t2, t2.plusMinutes(30), 50.5, 49.5);

        GenerationResponse response = new GenerationResponse(List.of(interval1, interval2));
        when(carbonIntensityClient.getGenerationMix(anyString(), anyString())).thenReturn(response);

        // When - Uruchamiamy testowaną metodę
        List<DailyEnergyMix> result = energyService.getDailyAverages();

        // Then - Weryfikujemy poprawność obliczeń
        assertNotNull(result);
        assertEquals(3, result.size()); // dzisiaj, jutro, pojutrze

        // Średnia dla dzisiaj: Wiatr = (40 + 50.5)/2 = 45.25%, Gaz = (60 + 49.5)/2 = 54.75%
        DailyEnergyMix todayMix = result.stream()
                .filter(mix -> mix.date().equals(today))
                .findFirst()
                .orElseThrow();

        Map<String, Double> averages = todayMix.fuelAverages();
        assertEquals(45.25, averages.get("wind"));
        assertEquals(54.75, averages.get("gas"));
        assertEquals(45.25, todayMix.cleanEnergyPercentage()); // tylko wiatr był czysty w tej próbce
    }

    @Test
    void findOptimalChargingWindow_ShouldFindCleanestWindow() {
        // Given - Szukamy okna 2-godzinnego (4 interwały) w danych dla jutra
        OffsetDateTime t1 = tomorrow.atTime(12, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime t2 = tomorrow.atTime(12, 30).atOffset(ZoneOffset.UTC);
        OffsetDateTime t3 = tomorrow.atTime(13, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime t4 = tomorrow.atTime(13, 30).atOffset(ZoneOffset.UTC);
        OffsetDateTime t5 = tomorrow.atTime(14, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime t6 = tomorrow.atTime(14, 30).atOffset(ZoneOffset.UTC);

        // Tworzymy ciąg interwałów, gdzie najczystsze 2h (4 interwały) zaczynają się od t2 (12:30) do t6 (14:30)
        IntervalData i1 = createMockInterval(t1, t1.plusMinutes(30), 10.0, 90.0); // 12:00 -> 10% czystej
        IntervalData i2 = createMockInterval(t2, t2.plusMinutes(30), 80.0, 20.0); // 12:30 -> 80% czystej [START OKNA]
        IntervalData i3 = createMockInterval(t3, t3.plusMinutes(30), 90.0, 10.0); // 13:00 -> 90% czystej
        IntervalData i4 = createMockInterval(t4, t4.plusMinutes(30), 85.0, 15.0); // 13:30 -> 85% czystej
        IntervalData i5 = createMockInterval(t5, t5.plusMinutes(30), 75.0, 25.0); // 14:00 -> 75% czystej [KONIEC OKNA]
        IntervalData i6 = createMockInterval(t6, t6.plusMinutes(30), 10.0, 90.0); // 14:30 -> 10% czystej

        GenerationResponse response = new GenerationResponse(List.of(i1, i2, i3, i4, i5, i6));
        when(carbonIntensityClient.getGenerationMix(anyString(), anyString())).thenReturn(response);

        // When - Szukamy okna o długości 2 godzin
        OptimalChargingWindow optimalWindow = energyService.findOptimalChargingWindow(2);

        // Then - Średnia z najlepszego okna: (80 + 90 + 85 + 75) / 4 = 82.5%
        assertNotNull(optimalWindow);
        assertEquals(t2, optimalWindow.start());
        assertEquals(t5.plusMinutes(30), optimalWindow.end()); // koniec ostatniego interwału (14:30)
        assertEquals(82.5, optimalWindow.averageCleanEnergyPercentage());
    }

    @Test
    void findOptimalChargingWindow_ShouldCorrectlyCrossMidnight() {
        // Given - Testujemy najtrudniejszy przypadek: okno przechodzące przez północ (jutro -> pojutrze)
        OffsetDateTime t1 = tomorrow.atTime(23, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime t2 = tomorrow.atTime(23, 30).atOffset(ZoneOffset.UTC);
        OffsetDateTime t3 = dayAfterTomorrow.atTime(0, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime t4 = dayAfterTomorrow.atTime(0, 30).atOffset(ZoneOffset.UTC);

        // Wszystkie 4 interwały dają średnio 85% czystej energii i przechodzą przez granicę dni
        IntervalData i1 = createMockInterval(t1, t1.plusMinutes(30), 80.0, 20.0);
        IntervalData i2 = createMockInterval(t2, t2.plusMinutes(30), 90.0, 10.0);
        IntervalData i3 = createMockInterval(t3, t3.plusMinutes(30), 85.0, 15.0);
        IntervalData i4 = createMockInterval(t4, t4.plusMinutes(30), 85.0, 15.0);

        GenerationResponse response = new GenerationResponse(List.of(i1, i2, i3, i4));
        when(carbonIntensityClient.getGenerationMix(anyString(), anyString())).thenReturn(response);

        // When - Szukamy okna o długości 2 godzin
        OptimalChargingWindow optimalWindow = energyService.findOptimalChargingWindow(2);

        // Then
        assertNotNull(optimalWindow);
        assertEquals(t1, optimalWindow.start());
        assertEquals(t4.plusMinutes(30), optimalWindow.end());
        assertEquals(85.0, optimalWindow.averageCleanEnergyPercentage());
    }

    @Test
    void findOptimalChargingWindow_ShouldThrowException_WhenDurationIsInvalid() {
        // When & Then - Walidacja wejścia (< 1h oraz > 6h)
        assertThrows(IllegalArgumentException.class, () -> energyService.findOptimalChargingWindow(0));
        assertThrows(IllegalArgumentException.class, () -> energyService.findOptimalChargingWindow(7));
    }

    @Test
    void findOptimalChargingWindow_ShouldThrowException_WhenNotEnoughData() {
        // Given - Zwracamy tylko 2 interwały z API, a żądamy 2 godzin (wymagane 4 interwały)
        OffsetDateTime t1 = tomorrow.atTime(12, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime t2 = tomorrow.atTime(12, 30).atOffset(ZoneOffset.UTC);

        IntervalData i1 = createMockInterval(t1, t1.plusMinutes(30), 50.0, 50.0);
        IntervalData i2 = createMockInterval(t2, t2.plusMinutes(30), 50.0, 50.0);

        GenerationResponse response = new GenerationResponse(List.of(i1, i2));
        when(carbonIntensityClient.getGenerationMix(anyString(), anyString())).thenReturn(response);

        // When & Then - Sprawdzamy czy rzuci błędem o braku danych
        assertThrows(IllegalStateException.class, () -> energyService.findOptimalChargingWindow(2));
    }

    // Pomocnicza metoda tworząca uproszczony interwał testowy
    private IntervalData createMockInterval(OffsetDateTime from, OffsetDateTime to, double windPerc, double gasPerc) {
        return new IntervalData(
                from.toString(),
                to.toString(),
                List.of(
                        new FuelMix("wind", windPerc),
                        new FuelMix("gas", gasPerc)
                )
        );
    }
}