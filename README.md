# EcoPower Backend

This repository contains the backend service for the **EcoPower** application, built as a fullstack recruitment task. It provides endpoints to analyze the UK energy grid mix and calculate the most ecological time window to charge electric vehicles.

## Features
- **Daily Energy Mix Aggregator**: Fetch and calculate 3-day daily averages for fuel sources, summing up total clean energy percentage (biomass, nuclear, hydro, wind, solar).
- **Optimal Charging Window**: A sliding-window algorithm that determines the cleanest $N$-hour window within the next 48 hours to charge an EV, successfully crossing the midnight boundary.
- **Robust Test Coverage**: Mockito and JUnit 5 tests covering complex edge cases (such as crossing midnight), mathematically verified.

## Tech Stack
- **Java 17** & **Spring Boot 4.1.0**
- **Spring RestClient** for external API integration (with native URI bypass to prevent percent-encoding issues)
- **JUnit 5** & **Mockito** for unit testing

## Running Locally
Ensure you are inside the `/frontend` directory:
```bash
# Build the backend image
docker build -t ecopower-backend .

# Run the backend container
docker run -p 8080:8080 ecopower-backend
