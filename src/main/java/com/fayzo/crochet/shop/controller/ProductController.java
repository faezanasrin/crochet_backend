package com.fayzo.crochet.shop.controller;

import java.util.List;
import java.util.Map;

import com.fayzo.crochet.shop.service.CustomerSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fayzo.crochet.shop.model.Product;
import com.fayzo.crochet.shop.repository.ProductRepository;
import com.fayzo.crochet.shop.repository.ProductReviewRepository;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;
    private final CustomerSessionService sessionService;

    public ProductController(
            ProductRepository productRepository,
            ProductReviewRepository reviewRepository,
            CustomerSessionService sessionService) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.sessionService = sessionService;
    }

    @GetMapping
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> addProduct(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Product product) {
        if (!isAdmin(authorization)) {
            return unauthorized();
        }
        String validationError = validate(product);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }
        product.setId(null);
        normalize(product);
        return ResponseEntity.ok(productRepository.save(product));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Product updates) {
        if (!isAdmin(authorization)) {
            return unauthorized();
        }
        String validationError = validate(updates);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }
        normalize(updates);

        return productRepository.findById(id).map(product -> {
            product.setName(updates.getName());
            product.setCategory(updates.getCategory());
            product.setPrice(updates.getPrice());
            product.setYarnType(updates.getYarnType());
            product.setImageUrl(updates.getImageUrl());
            product.setInStock(updates.isInStock());
            return ResponseEntity.ok(productRepository.save(product));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!isAdmin(authorization)) {
            return unauthorized();
        }
        if (!productRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        reviewRepository.deleteByProductId(id);
        productRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String validate(Product product) {
        if (product == null
                || isBlank(product.getName())
                || isBlank(product.getCategory())
                || product.getPrice() == null
                || !Double.isFinite(product.getPrice())
                || product.getPrice() <= 0
                || isBlank(product.getYarnType())
                || isBlank(product.getImageUrl())
                || product.getName().trim().length() > 255
                || product.getCategory().trim().length() > 255
                || product.getYarnType().trim().length() > 255
                || product.getImageUrl().trim().length() > 255) {
            return "Name, category, a positive price, yarn type, and image URL are required.";
        }

        String imageUrl = product.getImageUrl().trim();
        if (!(imageUrl.startsWith("https://")
                || imageUrl.startsWith("http://")
                || imageUrl.startsWith("/"))) {
            return "Image URL must start with http://, https://, or /.";
        }
        return null;
    }

    private void normalize(Product product) {
        product.setName(product.getName().trim());
        product.setCategory(product.getCategory().trim());
        product.setYarnType(product.getYarnType().trim());
        product.setImageUrl(product.getImageUrl().trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isAdmin(String authorization) {
        return sessionService.authenticate(authorization)
                .map(CustomerSessionService.SessionPrincipal::admin)
                .orElse(false);
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(401)
                .body(Map.of("error", "Admin sign-in is required to manage products."));
    }
}