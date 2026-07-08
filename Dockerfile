# ETAP 1: Budowanie aplikacji (Maven + JDK)
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Kopiujemy plik konfiguracyjny Mavena i pobieramy zależności (dla lepszego cache'owania warstw)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Kopiujemy kod źródłowy i budujemy paczkę .jar (pomijamy testy na etapie budowania obrazu, testy uruchomimy w CI/CD)
COPY src ./src
RUN mvn clean package -DskipTests

# ETAP 2: Uruchomienie aplikacji (Lekkie JRE)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Kopiujemy zbudowany plik .jar z poprzedniego etapu
COPY --from=build /app/target/*.jar app.jar

# Spring Boot domyślnie nasłuchuje na porcie 8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]