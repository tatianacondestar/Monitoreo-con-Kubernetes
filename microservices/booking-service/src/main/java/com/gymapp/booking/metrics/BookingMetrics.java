package com.gymapp.booking.metrics;

import com.gymapp.booking.repository.BookingRepository;
import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

/**
 * Métricas del booking-service:
 *  - Counter : reservas creadas exitosamente
 *  - Counter : reservas rechazadas (membresía inválida o gym-service caído)
 *  - Counter : errores de comunicación con gym-service
 *  - Gauge   : total de reservas confirmadas en memoria
 *  - Timer   : latencia de creación de una reserva (incluye llamada a gym-service)
 */
@Component
public class BookingMetrics {

    public final Counter bookingsCreated;
    public final Counter bookingsRejected;
    public final Counter gymServiceErrors;
    public final Timer   bookingCreationTimer;

    public BookingMetrics(MeterRegistry registry, BookingRepository repo) {

        this.bookingsCreated = Counter.builder("booking_service_bookings_created_total")
                .description("Total de reservas creadas exitosamente")
                .register(registry);

        this.bookingsRejected = Counter.builder("booking_service_bookings_rejected_total")
                .description("Total de reservas rechazadas por membresía inválida")
                .register(registry);

        this.gymServiceErrors = Counter.builder("booking_service_gym_service_errors_total")
                .description("Total de errores de comunicación con gym-service")
                .register(registry);

        // Gauge — reservas confirmadas en tiempo real
        Gauge.builder("booking_service_confirmed_bookings", repo,
                        r -> r.countByStatus("CONFIRMED"))
                .description("Número de reservas en estado CONFIRMED")
                .register(registry);

        this.bookingCreationTimer = Timer.builder("booking_service_creation_duration_seconds")
                .description("Latencia de creación de reserva (incluye validación con gym-service)")
                .register(registry);
    }
}
