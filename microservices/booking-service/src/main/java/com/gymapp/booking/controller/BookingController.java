package com.gymapp.booking.controller;

import com.gymapp.booking.client.GymServiceClient;
import com.gymapp.booking.metrics.BookingMetrics;
import com.gymapp.booking.model.Booking;
import com.gymapp.booking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private static final Logger log = LoggerFactory.getLogger(BookingController.class);

    private final BookingRepository repo;
    private final GymServiceClient gymClient;
    private final BookingMetrics metrics;

    public BookingController(BookingRepository repo, GymServiceClient gymClient, BookingMetrics metrics) {
        this.repo = repo;
        this.gymClient = gymClient;
        this.metrics = metrics;
    }

    // GET /api/bookings — lista todas las reservas
    @GetMapping
    public List<Booking> getAll() {
        log.info("Listando todas las reservas");
        return repo.findAll();
    }

    // GET /api/bookings/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Booking> getById(@PathVariable String id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/bookings — crea reserva validando membresía contra gym-service
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Booking booking) {
        return metrics.bookingCreationTimer.record(() -> {
            String membershipId = booking.getMembershipId();
            log.info("Intentando crear reserva para membresía: {}", membershipId);

            // Consulta al Servicio A (gym-service) — decisión de negocio
            boolean valid;
            try {
                valid = gymClient.isMembershipValid(membershipId);
            } catch (Exception ex) {
                log.error("Error de comunicación con gym-service: {}", ex.getMessage());
                metrics.gymServiceErrors.increment();
                metrics.bookingsRejected.increment();
                return ResponseEntity.status(503).body(Map.of(
                        "error", "gym-service no disponible",
                        "membershipId", membershipId
                ));
            }

            if (!valid) {
                log.warn("Membresía inválida o inactiva: {}", membershipId);
                metrics.bookingsRejected.increment();
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "La membresía no existe o está inactiva",
                        "membershipId", membershipId
                ));
            }

            // Membresía válida — se crea la reserva
            if (booking.getId() == null || booking.getId().isBlank()) {
                booking.setId(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }
            if (booking.getScheduledAt() == null) {
                booking.setScheduledAt(LocalDateTime.now().plusDays(1));
            }
            if (booking.getStatus() == null) {
                booking.setStatus("CONFIRMED");
            }

            Booking saved = repo.save(booking);
            metrics.bookingsCreated.increment();
            log.info("Reserva creada: {} para membresía {}", saved.getId(), membershipId);
            return ResponseEntity.status(201).body(saved);
        });
    }

    // PUT /api/bookings/{id}/cancel — cancela una reserva
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String id) {
        return repo.findById(id)
                .map(b -> {
                    b.setStatus("CANCELLED");
                    repo.save(b);
                    log.info("Reserva cancelada: {}", id);
                    return ResponseEntity.ok(b);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DELETE /api/bookings/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (repo.deleteById(id)) {
            log.info("Reserva eliminada: {}", id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // GET /api/bookings/status/{status} — filtra por estado
    @GetMapping("/status/{status}")
    public ResponseEntity<Map<String, Object>> countByStatus(@PathVariable String status) {
        long count = repo.countByStatus(status);
        return ResponseEntity.ok(Map.of("status", status, "count", count));
    }
}
