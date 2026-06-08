package com.gymapp.gym.metrics;

import com.gymapp.gym.repository.MembershipRepository;
import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registra las métricas personalizadas del gym-service:
 *  - Counter  : total de consultas de membresías
 *  - Counter  : total de membresías creadas
 *  - Gauge    : número de membresías activas en memoria
 *  - Gauge    : delay artificial configurado (para casuística 2)
 *  - Timer    : latencia de las búsquedas de membresía por ID
 */
@Component
public class GymMetrics {

    public final Counter membershipLookups;
    public final Counter membershipsCreated;
    public final Timer   membershipLookupTimer;

    // Gauge: referencia mutable al delay artificial (ms)
    private final AtomicInteger artificialDelayMs = new AtomicInteger(0);

    public GymMetrics(MeterRegistry registry, MembershipRepository repo) {

        // Counter — cuántas veces se consulta el catálogo
        this.membershipLookups = Counter.builder("gym_service_membership_lookups_total")
                .description("Total de consultas al catálogo de membresías")
                .register(registry);

        // Counter — cuántas membresías se han creado
        this.membershipsCreated = Counter.builder("gym_service_memberships_created_total")
                .description("Total de membresías creadas")
                .register(registry);

        // Gauge — membresías activas en tiempo real
        Gauge.builder("gym_service_active_memberships", repo, MembershipRepository::countActive)
                .description("Número de membresías activas en memoria")
                .register(registry);

        // Gauge — delay artificial para casuística de servicio lento
        Gauge.builder("gym_service_artificial_delay_ms", artificialDelayMs, AtomicInteger::get)
                .description("Delay artificial configurado en ms (casuística 2)")
                .register(registry);

        // Timer — latencia de búsqueda por ID
        this.membershipLookupTimer = Timer.builder("gym_service_lookup_duration_seconds")
                .description("Latencia de búsqueda de membresía por ID")
                .register(registry);
    }

    public int getArtificialDelayMs() {
        return artificialDelayMs.get();
    }

    public void setArtificialDelayMs(int ms) {
        artificialDelayMs.set(ms);
    }
}
