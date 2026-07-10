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
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        today = now.toLocalDate();
        tomorrow = today.plusDays(1);
        dayAfterTomorrow = today.plusDays(2);
    }

    @Test
    void getDailyAverages_ShouldCalculateCorrectlyAndRoundToTwoDecimals() {
        OffsetDateTime t1 = today.atTime(12, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime t2 = today.atTime(12, 30).atOffset(ZoneOffset.UTC);
        IntervalData interval1 = createMockInterval(t1, t1.plusMinutes(30), 40.0, 60.0);
        IntervalData interval2 = createMockInterval(t2, t2.plusMinutes(30), 50.5, 49.5);

        GenerationResponse response = new GenerationResponse(List.of(interval1, interval2));
        when(carbonIntensityClient.getGenerationMix(anyString(), anyString())).thenReturn(response);

        List<DailyEnergyMix> result = energyService.getDailyAverages();

        assertNotNull(result);
        assertEquals(3, result.size());

        DailyEnergyMix todayMix = result.stream()
                .filter(mix -> mix.date().equals(today))
                .findFirst()
                .orElseThrow();

        Map<String, Double> averages = todayMix.fuelAverages();
        assertEquals(45.25, averages.get("wind"));
        assertEquals(54.75, averages.get("gas"));
        assertEquals(45.25, todayMix.cleanEnergyPercentage()); 
    }

    @Test
    void findOptimalChargingWindow_ShouldFindCleanestWindow() {
        OffsetDateTime t1 = tomorrow.atTime(12, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime t2 = tomorrow.atTime(12, 30).atOffset(ZoneOffset.UTC);
        OffsetDateTime t3 = tomorrow.atTime(13, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime t4 = tomorrow.atTime(13, 30).atOffset(ZoneOffset.UTC);
        OffsetDateTime t5 = tomorrow.atTime(14, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime t6 = tomorrow.atTime(14, 30).atOffset(ZoneOffset.UTC);

        IntervalData i1 = createMockInterval(t1, t1.plusMinutes(30), 10.0, 90.0); 
        IntervalData i2 = createMockInterval(t2, t2.plusMinutes(30), 80.0, 20.0); 
        IntervalData i3 = createMockInterval(t3, t3.plusMinutes(30), 90.0, 10.0); 
        IntervalData i4 = createMockInterval(t4, t4.plusMinutes(30), 85.0, 15.0); 
        IntervalData i5 = createMockInterval(t5, t5.plusMinutes(30), 75.0, 25.0); 
        IntervalData i6 = createMockInterval(t6, t6.plusMinutes(30), 10.0, 90.0); 

        GenerationResponse response = new GenerationResponse(List.of(i1, i2, i3, i4, i5, i6));
        when(carbonIntensityClient.getGenerationMix(anyString(), anyString())).thenReturn(response);

        OptimalChargingWindow optimalWindow = energyService.findOptimalChargingWindow(2);

        assertNotNull(optimalWindow);
        assertEquals(t2, optimalWindow.start());
        assertEquals(t5.plusMinutes(30), optimalWindow.end());
        assertEquals(82.5, optimalWindow.averageCleanEnergyPercentage());
    }

    @Test
    void findOptimalChargingWindow_ShouldCorrectlyCrossMidnight() {
        OffsetDateTime t1 = tomorrow.atTime(23, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime t2 = tomorrow.atTime(23, 30).atOffset(ZoneOffset.UTC);
        OffsetDateTime t3 = dayAfterTomorrow.atTime(0, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime t4 = dayAfterTomorrow.atTime(0, 30).atOffset(ZoneOffset.UTC);

        IntervalData i1 = createMockInterval(t1, t1.plusMinutes(30), 80.0, 20.0);
        IntervalData i2 = createMockInterval(t2, t2.plusMinutes(30), 90.0, 10.0);
        IntervalData i3 = createMockInterval(t3, t3.plusMinutes(30), 85.0, 15.0);
        IntervalData i4 = createMockInterval(t4, t4.plusMinutes(30), 85.0, 15.0);

        GenerationResponse response = new GenerationResponse(List.of(i1, i2, i3, i4));
        when(carbonIntensityClient.getGenerationMix(anyString(), anyString())).thenReturn(response);

        OptimalChargingWindow optimalWindow = energyService.findOptimalChargingWindow(2);

        assertNotNull(optimalWindow);
        assertEquals(t1, optimalWindow.start());
        assertEquals(t4.plusMinutes(30), optimalWindow.end());
        assertEquals(85.0, optimalWindow.averageCleanEnergyPercentage());
    }

    @Test
    void findOptimalChargingWindow_ShouldThrowException_WhenDurationIsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> energyService.findOptimalChargingWindow(0));
        assertThrows(IllegalArgumentException.class, () -> energyService.findOptimalChargingWindow(7));
    }

    @Test
    void findOptimalChargingWindow_ShouldThrowException_WhenNotEnoughData() {
        OffsetDateTime t1 = tomorrow.atTime(12, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime t2 = tomorrow.atTime(12, 30).atOffset(ZoneOffset.UTC);

        IntervalData i1 = createMockInterval(t1, t1.plusMinutes(30), 50.0, 50.0);
        IntervalData i2 = createMockInterval(t2, t2.plusMinutes(30), 50.0, 50.0);

        GenerationResponse response = new GenerationResponse(List.of(i1, i2));
        when(carbonIntensityClient.getGenerationMix(anyString(), anyString())).thenReturn(response);

        assertThrows(IllegalStateException.class, () -> energyService.findOptimalChargingWindow(2));
    }

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