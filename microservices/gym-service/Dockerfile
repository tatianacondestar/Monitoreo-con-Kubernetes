# ── Etapa 1: Build ──────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
# Descarga dependencias en cache primero
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Etapa 2: Imagen final liviana ────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copia solo el JAR generado
COPY --from=builder /app/target/gym-service-1.0.0.jar app.jar

# Variable de entorno para delay artificial (casuística 2)
ENV ARTIFICIAL_DELAY_MS=0

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
