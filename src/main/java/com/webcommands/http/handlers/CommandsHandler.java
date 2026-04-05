package com.webcommands.http.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.webcommands.http.AuthManager;
import com.webcommands.http.CorsHelper;
import com.webcommands.scanner.CommandScanner;

import java.io.IOException;

/**
 * GET /api/commands — returns the full Brigadier command tree as JSON.
 *
 * The response is the pre-built volatile String from CommandScanner, so this
 * handler never touches the game thread and is always fast.
 */
public class CommandsHandler implements HttpHandler {

    private final String allowedOrigin;

    public CommandsHandler(String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (CorsHelper.handlePreflight(exchange, allowedOrigin)) return;
        CorsHelper.addCorsHeaders(exchange, allowedOrigin);

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            CorsHelper.sendError(exchange, 405, "Method Not Allowed");
            return;
        }

        String token = CorsHelper.extractBearerToken(exchange);
        if (!AuthManager.validateToken(token)) {
            CorsHelper.sendError(exchange, 401, "Unauthorized");
            return;
        }

        CorsHelper.sendJson(exchange, 200, CommandScanner.cachedCommandsJson);
    }
}
