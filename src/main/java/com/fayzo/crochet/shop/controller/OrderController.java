package com.fayzo.crochet.shop.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import com.fayzo.crochet.shop.model.Product;
import com.fayzo.crochet.shop.repository.CustomerRepository;
import com.fayzo.crochet.shop.repository.OrderRepository;
import com.fayzo.crochet.shop.repository.ProductRepository;
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
    private final ProductRepository productRepository;
    private final CustomerSessionService sessionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderController(
            OrderRepository orderRepository,
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            CustomerSessionService sessionService) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
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
        Object utrValue = orderData.getOrDefault(
                "utrNumber", orderData.get("paymentReference"));
        String utrNumber = utrValue instanceof String value ? value.trim() : "";
        if (!"UPI".equals(paymentMethod)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only UPI payment is accepted."));
        }
        if (!utrNumber.matches("\\d{12}")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "A valid 12-digit UPI reference / UTR is required."));
        }
        order.setPaymentMethod("UPI");
        order.setPaymentStatus("PENDING");
        order.setPaymentReference(utrNumber);
        order.setUtrNumber(utrNumber);

        Object itemsValue = orderData.get("items");
        if (!(itemsValue instanceof List<?> items) || items.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Add at least one valid product to the order."));
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<Map<String, Object>> verifiedItems = new ArrayList<>();
        for (Object itemValue : items) {
            if (!(itemValue instanceof Map<?, ?> item)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Order items are invalid."));
            }

            Long productId = parseLong(item.get("productId"));
            Integer quantity = parsePositiveInteger(item.get("quantity"));
            if (productId == null || quantity == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Each order item needs a valid product and quantity."));
            }

            Optional<Product> productResult = productRepository.findById(productId);
            if (productResult.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "A product in your basket is no longer available."));
            }
            Product product = productResult.get();
            if (!product.isInStock()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", product.getName() + " is out of stock."));
            }
            if (product.getPrice() == null || product.getPrice() < 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "A product in your basket has an invalid price."));
            }

            BigDecimal itemTotal = BigDecimal.valueOf(product.getPrice())
                    .multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(itemTotal);
            Map<String, Object> verifiedItem = new HashMap<>();
            verifiedItem.put("productId", product.getId());
            verifiedItem.put("name", product.getName());
            verifiedItem.put("price", product.getPrice());
            verifiedItem.put("quantity", quantity);
            verifiedItems.add(verifiedItem);
        }

        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal deliveryCharge = subtotal.compareTo(BigDecimal.valueOf(499)) >= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(49);
        order.setSubtotal(subtotal.doubleValue());
        order.setDeliveryCharge(deliveryCharge.doubleValue());
        order.setTotalAmount(subtotal.add(deliveryCharge).doubleValue());

        try {
            order.setOrderedItemsSummary(objectMapper.writeValueAsString(verifiedItems));
        } catch (JsonProcessingException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", "Order items are invalid."));
        }

        return ResponseEntity.ok(orderRepository.save(order));
    }

    private Long parseLong(Object value) {
        try {
            return new BigDecimal(value.toString()).longValueExact();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Integer parsePositiveInteger(Object value) {
        try {
            int quantity = new BigDecimal(value.toString()).intValueExact();
            return quantity > 0 ? quantity : null;
        } catch (RuntimeException exception) {
            return null;
        }
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

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteOrder(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!isAdmin(authorization)) {
            return unauthorized();
        }

        return orderRepository.findById(id)
                .map(order -> {
                    orderRepository.delete(order);
                    return ResponseEntity.noContent().build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/user")
    public ResponseEntity<?> deleteOwnFinishedOrder(
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
                    if (!Set.of("COMPLETED", "REJECTED", "CANCELLED")
                            .contains(order.getStatus())) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(Map.of(
                                        "error",
                                        "Only completed, rejected, or cancelled orders can be removed."));
                    }

                    orderRepository.delete(order);
                    return ResponseEntity.noContent().build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/payment")
    public ResponseEntity<?> confirmOrderPayment(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!isAdmin(authorization)) {
            return unauthorized();
        }

        return orderRepository.findById(id).map(order -> {
            if (!"UPI".equals(order.getPaymentMethod())
                    && !"UPI / GPay".equals(order.getPaymentMethod())) {
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
