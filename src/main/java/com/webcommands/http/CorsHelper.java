package com.webcommands.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Centralised CORS and response helper.
 *
 * Every handler must call handlePreflight() first. If it returns true, the method
 * should return immediately (the OPTIONS preflight has already been responded to).
 *
 * CORS note: since the token is sent in the Authorization header (not a cookie),
 * the request is NOT "credentialed" in the CORS sense, so Access-Control-Allow-Origin: *
 * is safe even for requests with Authorization headers.
 */
public class CorsHelper {

    private CorsHelper() {}

    /** Adds CORS headers to outgoing response. Must be called before sendResponseHeaders(). */
    public static void addCorsHeaders(HttpExchange exchange, String allowedOrigin) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
    }

    /**
     * Handles an OPTIONS preflight request.
     *
     * @return true if the request was OPTIONS and has been fully handled (caller must return).
     *         false if normal processing should continue.
     */
    public static boolean handlePreflight(HttpExchange exchange, String allowedOrigin) throws IOException {
        addCorsHeaders(exchange, allowedOrigin);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            // -1 means no response body — MUST use -1 for 204, not 0
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    /** Sends a UTF-8 JSON response with exact Content-Length. */
    public static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    /** Convenience wrapper for error JSON responses. */
    public static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
    }

    /** Extracts the Bearer token from the Authorization header, or returns null. */
    public static String extractBearerToken(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return null;
    }
}
