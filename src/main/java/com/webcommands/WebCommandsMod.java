package com.webcommands;

import com.mojang.brigadier.CommandDispatcher;
import com.webcommands.config.ModConfig;
import com.webcommands.http.WebServer;
import com.webcommands.rules.RulesManager;
import com.webcommands.scanner.CommandScanner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(WebCommandsMod.MODID)
public class WebCommandsMod {

    public static final String MODID = "webcommands";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    // Holds the latest dispatcher to guarantee scan after server full start
    private CommandDispatcher<CommandSourceStack> lastDispatcher = null;

    public WebCommandsMod() {
        ModLoadingContext.get().registerConfig(Type.COMMON, ModConfig.SPEC, "webcommands-common.toml");
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("[webcommands] Mod loaded — waiting for server start.");
    }

    /**
     * Fired every time commands are (re)built — on server start and on /reload.
     * Priority LOW ensures all other mods have already registered their commands.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRegisterCommands(RegisterCommandsEvent event) {
        lastDispatcher = event.getDispatcher();
        // Try to scan now; if server not ready yet, ensureUpdated in onServerStarted will finish the job
        CommandScanner.tryUpdate(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        RulesManager.load();
        // Always rescan on full server start — guarantees fresh data even if tryUpdate ran too early
        CommandScanner.ensureUpdated(lastDispatcher, event.getServer());
        WebServer.start(ModConfig.PORT.get(), ModConfig.ALLOWED_ORIGIN.get());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        WebServer.stop();
    }

    /** Intercepts every executed command and applies block rules. */
    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        RulesManager.enforce(event);
    }
}
