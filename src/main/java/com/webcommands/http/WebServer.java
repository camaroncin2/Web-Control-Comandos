package com.webcommands.http;

import com.sun.net.httpserver.HttpServer;
import com.webcommands.WebCommandsMod;
import com.webcommands.http.handlers.CommandsHandler;
import com.webcommands.http.handlers.LoginHandler;
import com.webcommands.http.handlers.LogoutHandler;
import com.webcommands.http.handlers.RedeemHandler;
import com.webcommands.http.handlers.RulesHandler;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Manages the embedded HTTP server lifecycle.
 *
 * Uses com.sun.net.httpserver.HttpServer from the JDK — no extra dependencies needed.
 * The server binds to all interfaces on the configured port so it is reachable from
 * the internet (ensure the port is open in the server's firewall/security group).
 */
public class WebServer {

    private static HttpServer server;

    private WebServer() {}

    public static void start(int port, String allowedOrigin) {
        if (server != null) {
            WebCommandsMod.LOGGER.warn("[webcommands] HTTP server already running — skipping start.");
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(4));

            server.createContext("/api/login",    new LoginHandler(allowedOrigin));
            server.createContext("/api/logout",   new LogoutHandler(allowedOrigin));
            server.createContext("/api/commands", new CommandsHandler(allowedOrigin));
            server.createContext("/api/rules",    new RulesHandler(allowedOrigin));
            server.createContext("/api/redeem",   new RedeemHandler(allowedOrigin));

            server.start();
            WebCommandsMod.LOGGER.info("[webcommands] HTTP API started on port {}. Allowed origin: {}", port, allowedOrigin);
        } catch (Exception e) {
            WebCommandsMod.LOGGER.error("[webcommands] Failed to start HTTP API server.", e);
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(2); // 2-second graceful shutdown
            server = null;
            WebCommandsMod.LOGGER.info("[webcommands] HTTP API stopped.");
        }
    }
}
