package com.fayzo.crochet.shop.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class CustomerSessionService {

    private static final long SESSION_LIFETIME_SECONDS = 12 * 60 * 60;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentMap<String, Session> sessions =
            new ConcurrentHashMap<>();

    public String createCustomerSession(String email) {
        return createSession(new SessionPrincipal(email, false));
    }

    public String createAdminSession() {
        return createSession(new SessionPrincipal(null, true));
    }

    public Optional<SessionPrincipal> authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }

        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }

        if (Instant.now().isAfter(session.expiresAt())) {
            sessions.remove(token, session);
            return Optional.empty();
        }

        return Optional.of(session.principal());
    }

    public void revoke(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            sessions.remove(authorizationHeader.substring(7).trim());
        }
    }

    private String createSession(SessionPrincipal principal) {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        sessions.put(
                token,
                new Session(principal, Instant.now().plusSeconds(SESSION_LIFETIME_SECONDS)));
        return token;
    }

    public record SessionPrincipal(String email, boolean admin) {}

    private record Session(SessionPrincipal principal, Instant expiresAt) {}
}
