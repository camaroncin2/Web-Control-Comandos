package com.webcommands.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class ModConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue PORT;
    public static final ForgeConfigSpec.ConfigValue<String> ALLOWED_ORIGIN;
    public static final ForgeConfigSpec.ConfigValue<String> PUBLIC_API_URL;
    public static final ForgeConfigSpec.ConfigValue<String> FRONTEND_URL;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CREDENTIALS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("webserver");

        PORT = builder
            .comment("HTTP port where the REST API will listen")
            .defineInRange("port", 8080, 1, 65535);

        ALLOWED_ORIGIN = builder
            .comment(
                "CORS allowed origin. Use * to allow all origins,",
                "or set your Render URL e.g. https://my-panel.onrender.com"
            )
            .define("allowedOrigin", "*");

        PUBLIC_API_URL = builder
            .comment(
                "Public URL of this API, used by /webcommands genlink.",
                "Example: http://123.45.67.89:8080"
            )
            .define("publicApiUrl", "");

        FRONTEND_URL = builder
            .comment(
                "URL of your Render frontend, used by /webcommands genlink.",
                "Example: https://web-control-comands.onrender.com"
            )
            .define("frontendUrl", "");

        CREDENTIALS = builder
            .comment(
                "Web panel user credentials. Format: \"username:sha256_of_password\"",
                "Generate SHA-256 hash (PowerShell):",
                "  $p = \"MiClave\"; $b = [System.Text.Encoding]::UTF8.GetBytes($p)",
                "  ([System.Security.Cryptography.SHA256]::Create().ComputeHash($b) | ForEach-Object { $_.ToString(\"x2\") }) -join \"\"",
                "Credentials defined below — change passwords after first login."
            )
            .defineList(
                "credentials",
                List.of(
                    "admin1:252f6c8d75110ca51a5d118daf743eacf1295cc50d6783f4f6bab42a106c903e",
                    "admin2:4eb8d846778a9caaeb86d9662d74b14082a8a6b866beb21ea1e5b6d30ba08dee",
                    "admin3:8a1d688eb740cf9f6d186326a11f560812ac68c70f75b70e4fa4822ad4de690d"
                ),
                e -> e instanceof String
            );

        builder.pop();

        SPEC = builder.build();
    }

    private ModConfig() {}
}
