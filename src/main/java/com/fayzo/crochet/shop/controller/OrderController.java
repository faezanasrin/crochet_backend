package com.fayzo.crochet.shop.controller;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fayzo.crochet.shop.model.Customer;
import com.fayzo.crochet.shop.model.CustomerOrder;
import com.fayzo.crochet.shop.repository.CustomerRepository;
import com.fayzo.crochet.shop.repository.OrderRepository;
import com.fayzo.crochet.shop.service.CustomerSessionService;
import com.fayzo.crochet.shop.service.CustomerSessionService.SessionPrincipal;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private static final Set<String> ALLOWED_ORDER_STATUSES = Set.of(
            "PENDING", "CONFIRMED", "CROCHETING", "DISPATCHED",
            "COMPLETED", "REJECTED", "CANCELLED");

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CustomerSessionService sessionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderController(
            OrderRepository orderRepository,
            CustomerRepository customerRepository,
            CustomerSessionService sessionService) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<?> placeOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> orderData) {
        Optional<SessionPrincipal> principal = customerSession(authorization);
        if (principal.isEmpty()) {
            return unauthorized();
        }

        Optional<Customer> customer =
                customerRepository.findByEmailIgnoreCase(principal.get().email());
        if (customer.isEmpty()) {
            return unauthorized();
        }

        CustomerOrder order = new CustomerOrder();
        order.setCustomerName((String) orderData.getOrDefault(
                "customerName", customer.get().getName()));
        order.setCustomerEmail(customer.get().getEmail());
        order.setPhone((String) orderData.getOrDefault(
                "phone", customer.get().getPhone()));
        order.setAddress((String) orderData.getOrDefault(
                "address", customer.get().getAddress()));
        order.setPincode((String) orderData.getOrDefault(
                "pincode", customer.get().getPincode()));
        order.setCustomNotes((String) orderData.get("customNotes"));
        String paymentMethod = (String) orderData.get("paymentMethod");
        String paymentReference = (String) orderData.get("paymentReference");
        if (!"UPI / GPay".equals(paymentMethod)
                && !"Cash on Delivery".equals(paymentMethod)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Select a valid payment method."));
        }
        if ("UPI / GPay".equals(paymentMethod)
                && (paymentReference == null || paymentReference.isBlank()
                || paymentReference.length() > 100)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "A valid UPI transaction ID is required."));
        }
        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus("UPI / GPay".equals(paymentMethod) ? "PENDING" : "NOT_REQUIRED");
        order.setPaymentReference(paymentReference);

        Object total = orderData.get("totalAmount");
        if (total != null) {
            order.setTotalAmount(Double.valueOf(total.toString()));
        }

        Object items = orderData.get("items");
        if (items != null) {
            try {
                order.setOrderedItemsSummary(objectMapper.writeValueAsString(items));
            } catch (JsonProcessingException exception) {
                return ResponseEntity.badRequest().body(Map.of("error", "Order items are invalid."));
            }
        }

        return ResponseEntity.ok(orderRepository.save(order));
    }

    @GetMapping("/user")
    public ResponseEntity<?> getOrdersForCurrentCustomer(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<SessionPrincipal> principal = customerSession(authorization);
        if (principal.isEmpty()) {
            return unauthorized();
        }

        return ResponseEntity.ok(
                orderRepository.findByCustomerEmailOrderByIdDesc(principal.get().email()));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<SessionPrincipal> principal = customerSession(authorization);
        if (principal.isEmpty()) {
            return unauthorized();
        }

        return orderRepository.findById(id)
                .filter(order -> order.getCustomerEmail() != null
                        && order.getCustomerEmail().equalsIgnoreCase(principal.get().email()))
                .map(order -> {
                    if (!"PENDING".equals(order.getStatus())) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(Map.of(
                                        "error",
                                        "Only pending orders can be cancelled."));
                    }

                    order.setStatus("CANCELLED");
                    return ResponseEntity.ok(orderRepository.save(order));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<?> getAllOrders(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!isAdmin(authorization)) {
            return unauthorized();
        }
        return ResponseEntity.ok(orderRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrderById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<SessionPrincipal> principal = sessionService.authenticate(authorization);
        if (principal.isEmpty()) {
            return unauthorized();
        }

        return orderRepository.findById(id)
                .filter(order -> principal.get().admin()
                        || order.getCustomerEmail() != null
                        && order.getCustomerEmail().equalsIgnoreCase(principal.get().email()))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, String> body) {
        if (!isAdmin(authorization)) {
            return unauthorized();
        }

        return orderRepository.findById(id).map(order -> {
            String newStatus = body.get("status");
            if (newStatus == null || !ALLOWED_ORDER_STATUSES.contains(newStatus)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Select a valid order status."));
            }
            order.setStatus(newStatus);
            return ResponseEntity.ok(orderRepository.save(order));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/payment")
    public ResponseEntity<?> confirmOrderPayment(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!isAdmin(authorization)) {
            return unauthorized();
        }

        return orderRepository.findById(id).map(order -> {
            if (!"UPI / GPay".equals(order.getPaymentMethod())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Only UPI orders can be marked as paid."));
            }
            order.setPaymentStatus("PAID");
            return ResponseEntity.ok(orderRepository.save(order));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Optional<SessionPrincipal> customerSession(String authorization) {
        return sessionService.authenticate(authorization)
                .filter(principal -> !principal.admin());
    }

    private boolean isAdmin(String authorization) {
        return sessionService.authenticate(authorization)
                .map(SessionPrincipal::admin)
                .orElse(false);
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Please sign in to access these orders."));
    }
}
