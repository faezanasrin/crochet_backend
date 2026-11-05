package com.fayzo.crochet.shop.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fayzo.crochet.shop.model.Customer;
import com.fayzo.crochet.shop.model.CustomerOrder;
import com.fayzo.crochet.shop.model.ProductReview;
import com.fayzo.crochet.shop.repository.CustomerRepository;
import com.fayzo.crochet.shop.repository.OrderRepository;
import com.fayzo.crochet.shop.repository.ProductRepository;
import com.fayzo.crochet.shop.repository.ProductReviewRepository;
import com.fayzo.crochet.shop.service.CustomerSessionService;
import com.fayzo.crochet.shop.service.CustomerSessionService.SessionPrincipal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
@CrossOrigin(origins = "*")
public class ProductReviewController {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CustomerSessionService sessionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProductReviewController(
            ProductReviewRepository reviewRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            CustomerRepository customerRepository,
            CustomerSessionService sessionService) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.sessionService = sessionService;
    }

    @GetMapping
    public ResponseEntity<?> getReviews(@PathVariable Long productId) {
        if (!productRepository.existsById(productId)) {
            return ResponseEntity.notFound().build();
        }
        List<ReviewResponse> reviews = reviewRepository
                .findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(ProductReviewController::toResponse)
                .toList();
        double averageRating = reviews.stream()
                .mapToInt(ReviewResponse::rating)
                .average()
                .orElse(0);
        return ResponseEntity.ok(new ReviewsResponse(reviews, averageRating, reviews.size()));
    }

    @GetMapping("/eligibility")
    public ResponseEntity<?> getEligibility(
            @PathVariable Long productId,
            @RequestHeader(value = "Authorization", required = false) String authorization)
            throws JsonProcessingException {
        Optional<SessionPrincipal> principal = customerSession(authorization);
        if (principal.isEmpty()) {
            return ResponseEntity.ok(new EligibilityResponse(false, false));
        }
        if (!productRepository.existsById(productId)) {
            return ResponseEntity.notFound().build();
        }

        String email = principal.get().email().toLowerCase(Locale.ROOT);
        boolean hasReviewed = reviewRepository
                .existsByProductIdAndCustomerEmailIgnoreCase(productId, email);
        return ResponseEntity.ok(new EligibilityResponse(
                hasCompletedOrder(email, productId) && !hasReviewed,
                hasReviewed));
    }

    @PostMapping
    public ResponseEntity<?> addReview(
            @PathVariable Long productId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ReviewRequest request) throws JsonProcessingException {
        Optional<SessionPrincipal> principal = customerSession(authorization);
        if (principal.isEmpty()) {
            return unauthorized("Please sign in to leave a review.");
        }
        if (!productRepository.existsById(productId)) {
            return ResponseEntity.notFound().build();
        }
        if (request == null || request.rating() < 1 || request.rating() > 5
                || request.comment() == null || request.comment().isBlank()
                || request.comment().trim().length() > 1000) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Choose 1–5 stars and write a review (up to 1000 characters)."));
        }

        String email = principal.get().email().toLowerCase(Locale.ROOT);
        if (!hasCompletedOrder(email, productId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Reviews are available after your order for this item is marked completed."));
        }
        if (reviewRepository.existsByProductIdAndCustomerEmailIgnoreCase(productId, email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "You have already reviewed this item."));
        }

        Optional<Customer> customer = customerRepository.findByEmailIgnoreCase(email);
        if (customer.isEmpty()) {
            return unauthorized("Please sign in again to leave a review.");
        }
        ProductReview review = new ProductReview();
        review.setProductId(productId);
        review.setCustomerEmail(email);
        review.setReviewerName(customer.get().getName());
        review.setRating(request.rating());
        review.setComment(request.comment().trim());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(reviewRepository.save(review)));
    }

    private boolean hasCompletedOrder(String email, Long productId)
            throws JsonProcessingException {
        for (CustomerOrder order : orderRepository
                .findByCustomerEmailIgnoreCaseOrderByIdDesc(email)) {
            if ("COMPLETED".equalsIgnoreCase(order.getStatus())
                    && includesProduct(order, productId)) {
                return true;
            }
        }
        return false;
    }

    private boolean includesProduct(CustomerOrder order, Long productId)
            throws JsonProcessingException {
        if (order.getOrderedItemsSummary() == null) {
            return false;
        }
        JsonNode items = objectMapper.readTree(order.getOrderedItemsSummary());
        if (items == null || !items.isArray()) {
            return false;
        }
        for (JsonNode item : items) {
            JsonNode orderedProductId = item.get("productId");
            if (orderedProductId != null
                    && productId.toString().equals(orderedProductId.asText())) {
                return true;
            }
        }
        return false;
    }

    private Optional<SessionPrincipal> customerSession(String authorization) {
        return sessionService.authenticate(authorization)
                .filter(principal -> !principal.admin());
    }

    private ResponseEntity<Map<String, String>> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", message));
    }

    private static ReviewResponse toResponse(ProductReview review) {
        return new ReviewResponse(
                review.getId(),
                review.getReviewerName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt());
    }

    public record ReviewRequest(int rating, String comment) {}
    public record ReviewResponse(
            Long id, String reviewerName, int rating, String comment,
            java.time.LocalDateTime createdAt) {}
    public record ReviewsResponse(
            List<ReviewResponse> reviews, double averageRating, int reviewCount) {}
    public record EligibilityResponse(boolean canReview, boolean hasReviewed) {}
}
