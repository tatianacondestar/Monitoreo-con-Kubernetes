package com.gymapp.gym.repository;

import com.gymapp.gym.model.Membership;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class MembershipRepository {

    private final Map<String, Membership> memberships = new ConcurrentHashMap<>();

    public MembershipRepository() {
        // Datos iniciales en memoria
        memberships.put("M001", new Membership("M001", "Plan Básico",    "BASIC",   29.99,  8,  true));
        memberships.put("M002", new Membership("M002", "Plan Premium",   "PREMIUM", 59.99,  20, true));
        memberships.put("M003", new Membership("M003", "Plan VIP",       "VIP",     99.99,  -1, true));
        memberships.put("M004", new Membership("M004", "Plan Estudiantil","BASIC",  19.99,  6,  true));
        memberships.put("M005", new Membership("M005", "Plan Inactivo",  "BASIC",   9.99,   4,  false));
    }

    public List<Membership> findAll() {
        return new ArrayList<>(memberships.values());
    }

    public Optional<Membership> findById(String id) {
        return Optional.ofNullable(memberships.get(id));
    }

    public Membership save(Membership m) {
        memberships.put(m.getId(), m);
        return m;
    }

    public boolean deleteById(String id) {
        return memberships.remove(id) != null;
    }

    public int count() {
        return memberships.size();
    }

    public long countActive() {
        return memberships.values().stream().filter(Membership::isActive).count();
    }
}
