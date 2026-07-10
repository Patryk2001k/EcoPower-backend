package com.ecopower.backend.controller;

import com.ecopower.backend.model.DailyEnergyMix;
import com.ecopower.backend.model.OptimalChargingWindow;
import com.ecopower.backend.service.EnergyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/energy")
public class EnergyController {

    // Profesjonalny logger do wypisywania błędów w logach kontenera
    private static final Logger log = LoggerFactory.getLogger(EnergyController.class);

    private final EnergyService energyService;

    public EnergyController(EnergyService energyService) {
        this.energyService = energyService;
    }

    /**
     * Endpoint returning the averaged daily energy mix for 3 days.
     * URL: GET http://localhost:8080/api/energy/daily-mix
     */
    @GetMapping("/daily-mix")
    public ResponseEntity<List<DailyEnergyMix>> getDailyMix() {
        try {
            List<DailyEnergyMix> dailyMixes = energyService.getDailyAverages();
            return ResponseEntity.ok(dailyMixes);
        } catch (IllegalStateException e) {
            log.error("External service error while fetching daily mix: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (Exception e) {
            log.error("Unexpected error occurred in getDailyMix endpoint", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint calculating the optimal green charging window.
     * URL: GET http://localhost:8080/api/energy/optimal-window?duration=3
     */
    @GetMapping("/optimal-window")
    public ResponseEntity<OptimalChargingWindow> getOptimalWindow(@RequestParam int duration) {
        try {
            OptimalChargingWindow window = energyService.findOptimalChargingWindow(duration);
            return ResponseEntity.ok(window);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid charging duration provided: {}", duration, e);
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            log.error("External service error while calculating optimal window: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (Exception e) {
            log.error("Unexpected error occurred in getOptimalWindow endpoint", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}