package com.BayFlow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@CrossOrigin(origins = "*")
public class BookingController {

    @Autowired
    private RepairOrderRepository bookingRepository;

    // State machine allowed transitions map
    private static final Map<String, List<String>> ALLOWED_TRANSITIONS = new HashMap<>();

    static {
        ALLOWED_TRANSITIONS.put("PENDING", Arrays.asList("CONFIRMED", "CANCELLED"));
        ALLOWED_TRANSITIONS.put("CONFIRMED", Arrays.asList("ASSIGNED"));
        ALLOWED_TRANSITIONS.put("ASSIGNED", Arrays.asList("INSPECTING"));
        ALLOWED_TRANSITIONS.put("INSPECTING", Arrays.asList("ESTIMATE_REVIEW"));
        ALLOWED_TRANSITIONS.put("ESTIMATE_REVIEW", Arrays.asList("AWAITING_CUSTOMER"));
        ALLOWED_TRANSITIONS.put("AWAITING_CUSTOMER", Arrays.asList("ESTIMATE_APPROVED", "ESTIMATE_REJECTED"));
        ALLOWED_TRANSITIONS.put("ESTIMATE_APPROVED", Arrays.asList("PARTS_PENDING"));
        ALLOWED_TRANSITIONS.put("PARTS_PENDING", Arrays.asList("PARTS_ORDERED", "PARTS_READY"));
        ALLOWED_TRANSITIONS.put("PARTS_ORDERED", Arrays.asList("PARTS_READY"));
        ALLOWED_TRANSITIONS.put("PARTS_READY", Arrays.asList("IN_REPAIR"));
        ALLOWED_TRANSITIONS.put("IN_REPAIR", Arrays.asList("QC_PENDING"));
        ALLOWED_TRANSITIONS.put("QC_PENDING", Arrays.asList("QC_IN_PROGRESS"));
        ALLOWED_TRANSITIONS.put("QC_IN_PROGRESS", Arrays.asList("READY_FOR_PICKUP", "IN_REPAIR"));
        ALLOWED_TRANSITIONS.put("READY_FOR_PICKUP", Arrays.asList("COMPLETED"));
        ALLOWED_TRANSITIONS.put("COMPLETED", Arrays.asList());
    }

    private boolean isValidTransition(String currentStatus, String nextStatus) {
        List<String> allowed = ALLOWED_TRANSITIONS.get(currentStatus);
        return allowed != null && allowed.contains(nextStatus);
    }

    // Get all bookings, optionally filtered by shopId
    @GetMapping
    public List<RepairOrder> getAllBookings(@RequestParam(required = false) Long shopId) {
        if (shopId != null) {
            return bookingRepository.findAll().stream()
                    .filter(order -> shopId.equals(order.getShopId()))
                    .toList();
        }
        return bookingRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<RepairOrder> createBooking(@RequestBody RepairOrder booking) {
        if (booking.getStatus() == null || booking.getStatus().isEmpty()) {
            booking.setStatus("PENDING");
        }
        RepairOrder savedBooking = bookingRepository.save(booking);
        return ResponseEntity.ok(savedBooking);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long id, @RequestParam String status) {
        RepairOrder order = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Booking not found with id: " + id));
        
        String currentStatus = order.getStatus() != null ? order.getStatus() : "PENDING";
        if (!isValidTransition(currentStatus, status)) {
            return ResponseEntity.badRequest().body("Invalid status transition from " + currentStatus + " to " + status);
        }
        
        order.setStatus(status);
        RepairOrder updatedOrder = bookingRepository.save(order);
        return ResponseEntity.ok(updatedOrder);
    }
}