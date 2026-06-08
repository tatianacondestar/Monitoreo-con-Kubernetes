package com.gymapp.booking.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Cliente HTTP que consulta al gym-service para validar membresías.
 * Usa Spring WebClient con timeout configurado.
 * Si el gym-service no responde a tiempo, maneja el error de forma controlada.
 */
@Component
public class GymServiceClient {

    private static final Logger log = LoggerFactory.getLogger(GymServiceClient.class);

    private final WebClient webClient;
    private final int timeoutSeconds;

    public GymServiceClient(
            @Value("${gym.service.url:http://gym-service:8080}") String gymServiceUrl,
            @Value("${gym.service.timeout-seconds:3}") int timeoutSeconds) {

        this.timeoutSeconds = timeoutSeconds;
        this.webClient = WebClient.builder()
                .baseUrl(gymServiceUrl)
                .build();

        log.info("GymServiceClient configurado -> url={}, timeout={}s", gymServiceUrl, timeoutSeconds);
    }

    /**
     * Verifica si una membresía existe y está activa.
     * @return true si existe y está activa, false si no existe o gym-service falla
     */
    public boolean isMembershipValid(String membershipId) {
        try {
            Map<?, ?> result = webClient.get()
                    .uri("/api/memberships/{id}", membershipId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .onErrorResume(ex -> {
                        log.error("Error consultando gym-service para membresía {}: {}", membershipId, ex.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (result == null) {
                log.warn("gym-service no respondió o membresía {} no encontrada", membershipId);
                return false;
            }

            Object activeObj = result.get("active");

            if (activeObj instanceof Boolean active) {
                return active;
            }

            return false;

        } catch (Exception ex) {
            log.error("Fallo al contactar gym-service: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Obtiene el tipo de membresía (BASIC, PREMIUM, VIP).
     * Retorna "UNKNOWN" si no se puede consultar.
     */
    public String getMembershipType(String membershipId) {
        try {
            Map<?, ?> result = webClient.get()
                    .uri("/api/memberships/{id}", membershipId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .onErrorResume(ex -> {
                        log.error("Timeout/error obteniendo tipo de membresía {}: {}", membershipId, ex.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (result == null) {
                return "UNKNOWN";
            }

            Object type = result.get("type");
            return type != null ? type.toString() : "UNKNOWN";

        } catch (Exception ex) {
            log.error("Fallo al obtener tipo de membresía: {}", ex.getMessage());
            return "UNKNOWN";
        }
    }
}