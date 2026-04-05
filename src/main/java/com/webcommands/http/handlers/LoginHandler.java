package com.webcommands.http.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.webcommands.http.AuthManager;
import com.webcommands.http.CorsHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** POST /api/login — validates credentials and returns a session token. */
public class LoginHandler implements HttpHandler {

    private final String allowedOrigin;

    public LoginHandler(String allowedOrigin) {
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

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String username = json.get("username").getAsString();
            String password = json.get("password").getAsString();

            String token = AuthManager.authenticate(username, password);
            if (token != null) {
                CorsHelper.sendJson(exchange, 200, "{\"token\":\"" + token + "\"}");
            } else {
                CorsHelper.sendError(exchange, 401, "Invalid credentials");
            }
        } catch (Exception e) {
            CorsHelper.sendError(exchange, 400, "Invalid request body");
        }
    }
}
