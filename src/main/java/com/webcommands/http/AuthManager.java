package com.webcommands.http;

import com.webcommands.WebCommandsMod;
import com.webcommands.config.ModConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages web-panel authentication tokens.
 *
 * Credentials are stored in the mod config as "username:sha256hex" entries.
 * Tokens are UUID strings kept in memory; they expire after 8 hours.
 * ConcurrentHashMap lets the HTTP thread pool read/write without external locking.
 */
public class AuthManager {

    private static final long TOKEN_EXPIRY_MS = 8L * 60 * 60 * 1000; // 8 hours

    /** token → creation timestamp */
    private static final ConcurrentHashMap<String, Instant> tokens = new ConcurrentHashMap<>();

    private AuthManager() {}

    // -------------------------------------------------------------------------

    /**
     * Validates username + plaintext password against config credentials.
     * @return a fresh token string, or null if credentials are wrong.
     */
    public static String authenticate(String username, String password) {
        if (username == null || password == null) return null;

        String passHash = sha256(password);
        List<? extends String> creds = ModConfig.CREDENTIALS.get();

        for (String entry : creds) {
            int colonIdx = entry.indexOf(':');
            if (colonIdx < 1) continue;
            String cfgUser = entry.substring(0, colonIdx);
            String cfgHash = entry.substring(colonIdx + 1);
            if (cfgUser.equals(username) && cfgHash.equalsIgnoreCase(passHash)) {
                String token = UUID.randomUUID().toString();
                tokens.put(token, Instant.now());
                WebCommandsMod.LOGGER.info("[webcommands] User '{}' authenticated.", username);
                return token;
            }
        }

        WebCommandsMod.LOGGER.warn("[webcommands] Failed login attempt for user '{}'.", username);
        return null;
    }

    /** Returns true if the token exists and has not expired. */
    public static boolean validateToken(String token) {
        if (token == null) return false;
        Instant created = tokens.get(token);
        if (created == null) return false;
        if (Instant.now().toEpochMilli() - created.toEpochMilli() > TOKEN_EXPIRY_MS) {
            tokens.remove(token);
            return false;
        }
        return true;
    }

    /** Removes a token (logout). */
    public static void invalidateToken(String token) {
        if (token != null) tokens.remove(token);
    }

    // -------------------------------------------------------------------------

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            // SHA-256 is guaranteed by the Java spec — this should never happen
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
