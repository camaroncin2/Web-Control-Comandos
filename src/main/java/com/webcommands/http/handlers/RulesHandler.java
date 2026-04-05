package com.webcommands.http.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.webcommands.http.AuthManager;
import com.webcommands.http.CorsHelper;
import com.webcommands.rules.CommandRule;
import com.webcommands.rules.RulesManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GET  /api/rules — returns all active blocking rules as a JSON object.
 * PUT  /api/rules — creates or updates a rule for a specific command.
 *                   Body: { command, blockedForAll, blockedForOp, blockedUsernames[] }
 *                   Sending an empty rule (all false, no users) removes the rule entry.
 */
public class RulesHandler implements HttpHandler {

    private static final Gson GSON = new Gson();
    private final String allowedOrigin;

    public RulesHandler(String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (CorsHelper.handlePreflight(exchange, allowedOrigin)) return;
        CorsHelper.addCorsHeaders(exchange, allowedOrigin);

        String token = CorsHelper.extractBearerToken(exchange);
        if (!AuthManager.validateToken(token)) {
            CorsHelper.sendError(exchange, 401, "Unauthorized");
            return;
        }

        switch (exchange.getRequestMethod().toUpperCase()) {
            case "GET" -> handleGet(exchange);
            case "PUT" -> handlePut(exchange);
            default    -> CorsHelper.sendError(exchange, 405, "Method Not Allowed");
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        Map<String, CommandRule> rules = RulesManager.getRules();
        CorsHelper.sendJson(exchange, 200, GSON.toJson(rules));
    }

    private void handlePut(HttpExchange exchange) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            String command = json.get("command").getAsString().trim().toLowerCase();
            if (command.isEmpty()) {
                CorsHelper.sendError(exchange, 400, "Field 'command' is required");
                return;
            }

            boolean blockedForAll = json.has("blockedForAll") && json.get("blockedForAll").getAsBoolean();
            boolean blockedForOp  = json.has("blockedForOp")  && json.get("blockedForOp").getAsBoolean();

            List<String> usernames = new ArrayList<>();
            if (json.has("blockedUsernames") && json.get("blockedUsernames").isJsonArray()) {
                JsonArray arr = json.getAsJsonArray("blockedUsernames");
                arr.forEach(e -> {
                    String name = e.getAsString().trim();
                    if (!name.isEmpty()) usernames.add(name);
                });
            }

            CommandRule rule = new CommandRule(blockedForAll, blockedForOp, usernames);
            RulesManager.setRule(command, rule);

            CorsHelper.sendJson(exchange, 200, "{\"ok\":true}");
        } catch (Exception e) {
            CorsHelper.sendError(exchange, 400, "Invalid request body");
        }
    }
}
