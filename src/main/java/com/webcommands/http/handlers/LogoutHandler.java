package com.webcommands.http.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.webcommands.http.AuthManager;
import com.webcommands.http.CorsHelper;

import java.io.IOException;

/** POST /api/logout — invalidates the caller's session token. */
public class LogoutHandler implements HttpHandler {

    private final String allowedOrigin;

    public LogoutHandler(String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (CorsHelper.handlePreflight(exchange, allowedOrigin)) return;
        CorsHelper.addCorsHeaders(exchange, allowedOrigin);

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            CorsHelper.sendError(exchange, 405, "Method Not Allowed");
            return;
        }

        String token = CorsHelper.extractBearerToken(exchange);
        if (!AuthManager.validateToken(token)) {
            CorsHelper.sendError(exchange, 401, "Unauthorized");
            return;
        }

        AuthManager.invalidateToken(token);
        CorsHelper.sendJson(exchange, 200, "{\"ok\":true}");
    }
}
