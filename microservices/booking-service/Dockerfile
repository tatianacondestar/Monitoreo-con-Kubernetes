# ── Etapa 1: Build ──────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Etapa 2: Imagen final liviana ────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=builder /app/target/booking-service-1.0.0.jar app.jar

ENV GYM_SERVICE_URL=http://gym-service:8080
ENV GYM_SERVICE_TIMEOUT=3

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
