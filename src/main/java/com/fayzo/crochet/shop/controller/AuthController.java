package com.fayzo.crochet.shop.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.fayzo.crochet.shop.model.Customer;
import com.fayzo.crochet.shop.repository.CustomerRepository;
import com.fayzo.crochet.shop.service.CustomerSessionService;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final CustomerRepository customerRepository;
    private final CustomerSessionService sessionService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String adminPassword;

    public AuthController(
            CustomerRepository customerRepository,
            CustomerSessionService sessionService,
            @Value("${app.admin.password:}") String adminPassword) {
        this.customerRepository = customerRepository;
        this.sessionService = sessionService;
        this.adminPassword = adminPassword;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> registration) {
        String emailInput = registration.get("email");
        String password = registration.get("password");
        if (emailInput == null || emailInput.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "Email is required.");
        }
        if (password == null || password.length() < 8) {
            return error(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters.");
        }

        String email = emailInput.trim().toLowerCase(Locale.ROOT);
        if (customerRepository.findByEmailIgnoreCase(email).isPresent()) {
            return error(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        Customer customer = new Customer();
        customer.setName(registration.get("name"));
        customer.setEmail(email);
        customer.setPassword(passwordEncoder.encode(password));
        customer.setPhone(registration.get("phone"));
        customer.setAddress(registration.get("address"));
        customer.setPincode(registration.get("pincode"));
        Customer saved = customerRepository.save(customer);
        return ResponseEntity.status(HttpStatus.CREATED).body(createAuthResponse(saved));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String email = credentials.get("email");
        String password = credentials.get("password");
        if (email == null || password == null) {
            return error(HttpStatus.BAD_REQUEST, "Email and password are required.");
        }

        Optional<Customer> optionalCustomer =
                customerRepository.findByEmailIgnoreCase(email.trim());
        if (optionalCustomer.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        Customer customer = optionalCustomer.get();
        String storedPassword = customer.getPassword();
        if (storedPassword == null || !passwordMatches(password, storedPassword)) {
            return error(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        if (!isBcryptHash(storedPassword)) {
            customer.setPassword(passwordEncoder.encode(password));
            customerRepository.save(customer);
        }

        return ResponseEntity.ok(createAuthResponse(customer));
    }

    @PostMapping("/admin/login")
    public ResponseEntity<?> adminLogin(@RequestBody Map<String, String> credentials) {
        if (adminPassword.isBlank()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Admin access is not configured.");
        }

        String password = credentials.get("password");
        if (password == null || !MessageDigest.isEqual(
                password.getBytes(StandardCharsets.UTF_8),
                adminPassword.getBytes(StandardCharsets.UTF_8))) {
            return error(HttpStatus.UNAUTHORIZED, "Invalid admin password.");
        }

        return ResponseEntity.ok(Map.of("token", sessionService.createAdminSession()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        sessionService.revoke(authorization);
        return ResponseEntity.noContent().build();
    }

    private AuthResponse createAuthResponse(Customer customer) {
        UserResponse user = new UserResponse(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getPincode());
        return new AuthResponse(sessionService.createCustomerSession(customer.getEmail()), user);
    }

    private boolean passwordMatches(String password, String storedPassword) {
        return isBcryptHash(storedPassword)
                ? passwordEncoder.matches(password, storedPassword)
                : MessageDigest.isEqual(
                        password.getBytes(StandardCharsets.UTF_8),
                        storedPassword.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    public record AuthResponse(String token, UserResponse user) {}

    public record UserResponse(
            Long id,
            String name,
            String email,
            String phone,
            String address,
            String pincode) {}
}
