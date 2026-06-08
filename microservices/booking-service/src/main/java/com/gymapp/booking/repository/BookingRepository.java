package com.gymapp.booking.repository;

import com.gymapp.booking.model.Booking;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class BookingRepository {

    private final Map<String, Booking> bookings = new ConcurrentHashMap<>();

    public BookingRepository() {
        // Datos iniciales en memoria
        bookings.put("B001", new Booking("B001", "M001", "Carlos Pérez",
                "Yoga", LocalDateTime.now().plusDays(1), "CONFIRMED"));
        bookings.put("B002", new Booking("B002", "M002", "Ana García",
                "Spinning", LocalDateTime.now().plusDays(2), "CONFIRMED"));
        bookings.put("B003", new Booking("B003", "M003", "Luis Torres",
                "CrossFit", LocalDateTime.now().plusHours(3), "PENDING"));
    }

    public List<Booking> findAll() {
        return new ArrayList<>(bookings.values());
    }

    public Optional<Booking> findById(String id) {
        return Optional.ofNullable(bookings.get(id));
    }

    public Booking save(Booking b) {
        bookings.put(b.getId(), b);
        return b;
    }

    public boolean deleteById(String id) {
        return bookings.remove(id) != null;
    }

    public long countByStatus(String status) {
        return bookings.values().stream()
                .filter(b -> status.equalsIgnoreCase(b.getStatus()))
                .count();
    }

    public int count() {
        return bookings.size();
    }
}
