package com.ecopower.backend.controller;

import com.ecopower.backend.model.DailyEnergyMix;
import com.ecopower.backend.model.OptimalChargingWindow;
import com.ecopower.backend.service.EnergyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/energy")
public class EnergyController {

    private final EnergyService energyService;

    public EnergyController(EnergyService energyService) {
        this.energyService = energyService;
    }

    /**
     * Endpoint zwracający uśredniony miks energetyczn dla 3 dni (dzisiaj, jutro, pojutrze)
     * Adres: GET http://localhost:8080/api/energy/daily-mix
     */y
    @GetMapping("/daily-mix")
    public ResponseEntity<List<DailyEnergyMix>> getDailyMix() {
        try {
            List<DailyEnergyMix> dailyMixes = energyService.getDailyAverages();
            return ResponseEntity.ok(dailyMixes);
        } catch (IllegalStateException e) {
            // Obsługa błędu, gdy zewnętrzne API nie odpowie poprawnie
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint wyliczający najlepsze okno czasowe do ładowania pojazdu
     * Adres: GET http://localhost:8080/api/energy/optimal-window?duration=3
     */
    @GetMapping("/optimal-window")
    public ResponseEntity<OptimalChargingWindow> getOptimalWindow(@RequestParam int duration) {
        try {
            OptimalChargingWindow window = energyService.findOptimalChargingWindow(duration);
            return ResponseEntity.ok(window);
        } catch (IllegalArgumentException e) {
            // Obsługa błędu, jeśli podano czas spoza przedziału 1-6h
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}