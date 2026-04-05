package com.webcommands.scanner;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.webcommands.WebCommandsMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Traverses the Brigadier command tree and caches the result as a JSON string.
 *
 * Thread-safety: scanAndCache() is called from the game thread (ServerStartedEvent /
 * RegisterCommandsEvent). The HTTP thread reads cachedCommandsJson via the volatile
 * field, which guarantees visibility without locking.
 *
 * Critical guard: redirect nodes (e.g. /execute run → root) are NEVER recursed into,
 * preventing infinite recursion / StackOverflowError.
 */
public class CommandScanner {

    /** Volatile: game-thread writes, HTTP-thread reads — safe without synchronization. */
    public static volatile String cachedCommandsJson = "{\"total\":0,\"scannedAt\":\"never\",\"commands\":[]}";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private CommandScanner() {}

    /** Called from RegisterCommandsEvent (LOW priority). Scans only if server is ready. */
    public static void tryUpdate(CommandDispatcher<CommandSourceStack> dispatcher) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            scanAndCache(dispatcher, server);
        }
        // If server is null here, ensureUpdated() will call scanAndCache once ServerStartedEvent fires.
    }

    /** Called from ServerStartedEvent — server is guaranteed non-null. */
    public static void ensureUpdated(CommandDispatcher<CommandSourceStack> dispatcher, MinecraftServer server) {
        if (dispatcher != null && server != null) {
            scanAndCache(dispatcher, server);
        }
    }

    // -------------------------------------------------------------------------

    private static void scanAndCache(CommandDispatcher<CommandSourceStack> dispatcher, MinecraftServer server) {
        try {
            RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();
            StringBuilder sb = new StringBuilder();
            sb.append("{\"total\":0,\"scannedAt\":\"")
              .append(LocalDateTime.now().format(FMT))
              .append("\",\"commands\":[");

            int count = 0;
            boolean first = true;
            for (CommandNode<CommandSourceStack> node : root.getChildren()) {
                // Skip root-level redirect/alias nodes — they just point elsewhere
                if (node.getRedirect() != null) continue;

                String nodeJson = serializeNode(node, node.getName(), server, 0);
                if (nodeJson != null) {
                    if (!first) sb.append(",");
                    sb.append(nodeJson);
                    first = false;
                    count++;
                }
            }

            sb.append("]}");
            // Patch total into the JSON
            String json = sb.toString().replace("\"total\":0", "\"total\":" + count);
            cachedCommandsJson = json;
            WebCommandsMod.LOGGER.info("[webcommands] Scanned {} root commands", count);
        } catch (Exception e) {
            WebCommandsMod.LOGGER.error("[webcommands] Error scanning command tree", e);
        }
    }

    /**
     * Recursively serializes a command node and all its children to JSON.
     *
     * @param node   the current node
     * @param path   accumulated full path string (e.g. "gamemode survival")
     * @param server needed to create fake permission sources
     * @param depth  current recursion depth (safety limit at 32)
     */
    private static String serializeNode(CommandNode<CommandSourceStack> node,
                                        String path,
                                        MinecraftServer server,
                                        int depth) {
        if (depth > 32) return null; // safety limit for extremely deep trees

        String name = node.getName();
        boolean isArg = node instanceof ArgumentCommandNode;
        String displayName = isArg ? "<" + name + ">" : name;

        // Determine arg type class name for ArgumentCommandNodes
        String argType = "";
        if (node instanceof ArgumentCommandNode<?, ?> argNode) {
            argType = argNode.getType().getClass().getSimpleName();
        }

        // Find the minimum OP level that satisfies the node's .requires() predicate
        int minLevel = -1;
        for (int level = 0; level <= 4; level++) {
            try {
                CommandSourceStack fakeSource = server.createCommandSourceStack().withPermission(level);
                if (node.canUse(fakeSource)) {
                    minLevel = level;
                    break;
                }
            } catch (Exception ignored) {
                // Some nodes may throw during canUse — treat as inaccessible at this level
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"name\":").append(jsonStr(displayName)).append(",");
        sb.append("\"path\":").append(jsonStr(path)).append(",");
        sb.append("\"type\":\"").append(isArg ? "argument" : "literal").append("\",");
        if (!argType.isEmpty()) {
            sb.append("\"argType\":").append(jsonStr(argType)).append(",");
        }
        sb.append("\"minLevel\":").append(minLevel).append(",");
        sb.append("\"children\":[");

        // Only recurse if this node is NOT a redirect — redirect nodes (e.g. /execute run)
        // point back to the root causing infinite recursion if followed.
        if (node.getRedirect() == null) {
            boolean firstChild = true;
            for (CommandNode<CommandSourceStack> child : node.getChildren()) {
                String childPath = path + " " + child.getName();
                String childJson = serializeNode(child, childPath, server, depth + 1);
                if (childJson != null) {
                    if (!firstChild) sb.append(",");
                    sb.append(childJson);
                    firstChild = false;
                }
            }
        }

        sb.append("]}");
        return sb.toString();
    }

    /** Minimal JSON string escaper — handles the most common special characters. */
    private static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            + "\"";
    }
}
