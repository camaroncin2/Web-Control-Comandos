package com.webcommands.rules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.webcommands.WebCommandsMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages command blocking rules.
 *
 * The in-memory map is a ConcurrentHashMap so the HTTP server thread (PUT /api/rules)
 * and the game thread (CommandEvent enforcement) can access it safely.
 *
 * Persistence uses an atomic file swap (write tmp → move) to avoid corrupt reads
 * if the server dies mid-write.
 */
public class RulesManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ConcurrentHashMap<String, CommandRule> rules = new ConcurrentHashMap<>();

    private RulesManager() {}

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private static Path getRulesFile() {
        return FMLPaths.GAMEDIR.get().resolve("config").resolve("webcommands-rules.json");
    }

    public static void load() {
        Path file = getRulesFile();
        if (!Files.exists(file)) {
            WebCommandsMod.LOGGER.info("[webcommands] No rules file found — starting with empty rule set.");
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, CommandRule>>() {}.getType();
            Map<String, CommandRule> loaded = GSON.fromJson(json, type);
            if (loaded != null) {
                rules.clear();
                rules.putAll(loaded);
                WebCommandsMod.LOGGER.info("[webcommands] Loaded {} command rules.", rules.size());
            }
        } catch (Exception e) {
            WebCommandsMod.LOGGER.error("[webcommands] Failed to load rules file.", e);
        }
    }

    private static void save() {
        try {
            Path file = getRulesFile();
            Files.createDirectories(file.getParent());
            String json = GSON.toJson(new HashMap<>(rules));
            Path tmp = file.resolveSibling("webcommands-rules.json.tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            WebCommandsMod.LOGGER.error("[webcommands] Failed to save rules file.", e);
        }
    }

    // -------------------------------------------------------------------------
    // Public API (called by RulesHandler on HTTP thread)
    // -------------------------------------------------------------------------

    public static void setRule(String command, CommandRule rule) {
        if (command == null || command.isBlank()) return;
        String key = command.toLowerCase().trim();
        if (rule == null || rule.isEmpty()) {
            rules.remove(key);
        } else {
            rules.put(key, rule);
        }
        save();
    }

    public static Map<String, CommandRule> getRules() {
        return new HashMap<>(rules);
    }

    // -------------------------------------------------------------------------
    // Enforcement (called from CommandEvent on the game thread)
    // -------------------------------------------------------------------------

    public static void enforce(CommandEvent event) {
        String input = event.getParseResults().getReader().getString().trim();
        if (input.startsWith("/")) input = input.substring(1);
        if (input.isEmpty()) return;

        String rootCommand = input.split("\\s+")[0].toLowerCase();
        CommandRule rule = rules.get(rootCommand);
        if (rule == null) return;

        CommandSourceStack source = event.getParseResults().getContext().getSource();

        if (rule.blockedForAll) {
            cancelCommand(event, source, rootCommand);
            return;
        }

        if (rule.blockedForOp && source.hasPermission(1)) {
            cancelCommand(event, source, rootCommand);
            return;
        }

        if (rule.blockedUsernames != null && !rule.blockedUsernames.isEmpty()) {
            String playerName = source.getTextName();
            for (String blocked : rule.blockedUsernames) {
                if (blocked.equalsIgnoreCase(playerName)) {
                    cancelCommand(event, source, rootCommand);
                    return;
                }
            }
        }
    }

    private static void cancelCommand(CommandEvent event, CommandSourceStack source, String command) {
        event.setCanceled(true);
        try {
            source.sendFailure(Component.literal("§cEl comando §f/" + command + "§c está bloqueado para ti."));
        } catch (Exception ignored) {}
    }
}
