package com.gymapp.gym.controller;

import com.gymapp.gym.metrics.GymMetrics;
import com.gymapp.gym.model.Membership;
import com.gymapp.gym.repository.MembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {

    private static final Logger log = LoggerFactory.getLogger(MembershipController.class);

    private final MembershipRepository repo;
    private final GymMetrics metrics;

    public MembershipController(MembershipRepository repo, GymMetrics metrics) {
        this.repo = repo;
        this.metrics = metrics;
    }

    // GET /api/memberships — lista todas
    @GetMapping
    public List<Membership> getAll() {
        metrics.membershipLookups.increment();
        log.info("Listando todas las membresías");
        return repo.findAll();
    }

    // GET /api/memberships/{id} — busca por ID con delay artificial (casuística 2)
    @GetMapping("/{id}")
    public ResponseEntity<Membership> getById(@PathVariable String id) {
        return metrics.membershipLookupTimer.record(() -> {
            metrics.membershipLookups.increment();

            int delay = metrics.getArtificialDelayMs();
            if (delay > 0) {
                log.warn("Aplicando delay artificial de {}ms", delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            return repo.findById(id)
                    .map(m -> {
                        log.info("Membresía encontrada: {}", id);
                        return ResponseEntity.ok(m);
                    })
                    .orElseGet(() -> {
                        log.warn("Membresía no encontrada: {}", id);
                        return ResponseEntity.notFound().build();
                    });
        });
    }

    // POST /api/memberships — crea nueva
    @PostMapping
    public ResponseEntity<Membership> create(@RequestBody Membership m) {
        if (m.getId() == null || m.getId().isBlank()) {
            m.setId(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        Membership saved = repo.save(m);
        metrics.membershipsCreated.increment();
        log.info("Membresía creada: {}", saved.getId());
        return ResponseEntity.status(201).body(saved);
    }

    // PUT /api/memberships/{id} — actualiza
    @PutMapping("/{id}")
    public ResponseEntity<Membership> update(@PathVariable String id, @RequestBody Membership m) {
        return repo.findById(id)
                .map(existing -> {
                    m.setId(id);
                    repo.save(m);
                    log.info("Membresía actualizada: {}", id);
                    return ResponseEntity.ok(m);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DELETE /api/memberships/{id} — elimina
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (repo.deleteById(id)) {
            log.info("Membresía eliminada: {}", id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // POST /api/memberships/delay — configura delay artificial (casuística 2)
    @PostMapping("/delay")
    public ResponseEntity<Map<String, Object>> setDelay(@RequestBody Map<String, Integer> body) {
        int ms = body.getOrDefault("delayMs", 0);
        metrics.setArtificialDelayMs(ms);
        log.warn("Delay artificial configurado a {}ms", ms);
        return ResponseEntity.ok(Map.of("delayMs", ms, "message", "Delay configurado"));
    }
}
