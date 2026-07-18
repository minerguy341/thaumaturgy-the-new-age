package io.github.minerguy341.new_age_thaum.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Cross-loader config at {@code config/new_age_thaum.toml} with real comments. The
 * format is flat {@code key = value} TOML, read and written by a tiny hand-rolled
 * parser (no extra dependency; PLAN §5 forbids loader-specific config APIs).
 * The file is rewritten after every load so docs stay current, and
 * {@link #maybeReload()} re-reads it when it changes on disk — edit and reopen the
 * orrery, no restart. A legacy new_age_thaum.json is migrated once, then removed.
 */
public final class NewAgeThaumConfig {
    /** Research sphere size scales with paper tier (false: everything on GP(3,0)=92). */
    public static boolean tierScaledSpheres = true;

    /** Multipliers for the energy-current visuals on the research sphere. */
    public static double currentAmplitude = 1.0;
    public static double currentSpeed = 1.0;
    public static double currentWidth = 1.0;

    /** Cell border thickness on the research sphere (1.0 = default, 0 = none). */
    public static double cellBorderWidth = 1.0;

    /** "aspects" = ribbon blends the linked aspects; "custom" = fixed base + pulse gradient. */
    public static String currentColorMode = "aspects";
    public static int currentBaseColor = 0x8A6BB5;
    public static int currentPulseFrom = 0x7FE8D8;
    public static int currentPulseTo = 0xFFFFFF;

    private static long loadedModified = -1;

    private NewAgeThaumConfig() {
    }

    public static boolean customCurrentColors() {
        return "custom".equalsIgnoreCase(currentColorMode);
    }

    private static Path file() {
        return Platform.getConfigFolder().resolve("new_age_thaum.toml");
    }

    public static void load() {
        Path path = file();
        try {
            migrateLegacyJson();
            if (Files.exists(path)) {
                apply(parseFlatToml(Files.readString(path)));
            }
            write(path);
            loadedModified = Files.getLastModifiedTime(path).toMillis();
            NewAgeThaum.LOGGER.info(
                    "Config loaded: tierScaledSpheres={}, currentAmplitude={}, currentSpeed={}, currentWidth={}, cellBorderWidth={}, currentColorMode={}",
                    tierScaledSpheres, currentAmplitude, currentSpeed, currentWidth, cellBorderWidth, currentColorMode);
        } catch (Exception e) {
            NewAgeThaum.LOGGER.warn("Could not read config {}; using defaults", path, e);
        }
    }

    /** Cheap mtime check; reloads when the file was edited since the last load. */
    public static void maybeReload() {
        try {
            Path path = file();
            if (Files.exists(path) && Files.getLastModifiedTime(path).toMillis() != loadedModified) {
                load();
            }
        } catch (IOException ignored) {
        }
    }

    private static void apply(Map<String, String> values) {
        tierScaledSpheres = parseBool(values.get("tierScaledSpheres"), tierScaledSpheres);
        currentAmplitude = parseDouble(values.get("currentAmplitude"), currentAmplitude);
        currentSpeed = parseDouble(values.get("currentSpeed"), currentSpeed);
        currentWidth = parseDouble(values.get("currentWidth"), currentWidth);
        cellBorderWidth = parseDouble(values.get("cellBorderWidth"), cellBorderWidth);
        if (values.containsKey("currentColorMode")) {
            currentColorMode = values.get("currentColorMode");
        }
        currentBaseColor = parseHex(values.get("currentBaseColor"), currentBaseColor);
        currentPulseFrom = parseHex(values.get("currentPulseFrom"), currentPulseFrom);
        currentPulseTo = parseHex(values.get("currentPulseTo"), currentPulseTo);
    }

    /** Flat {@code key = value} TOML: comments (#), quoted strings, booleans, numbers. */
    private static Map<String, String> parseFlatToml(String content) {
        Map<String, String> values = new HashMap<>();
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (value.startsWith("\"")) {
                int close = value.indexOf('"', 1);
                if (close > 0) {
                    value = value.substring(1, close);
                }
            } else {
                int hash = value.indexOf('#');
                if (hash >= 0) {
                    value = value.substring(0, hash).trim();
                }
            }
            values.put(key, value);
        }
        return values;
    }

    private static void write(Path path) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# Thaumaturgy: The New Age — config.\n");
        out.append("# Hot-reloads when the Arcane Orrery screen is reopened; no restart needed.\n\n");

        out.append("# Research sphere size scales with paper tier (Fledgling/Apprentice: 42 cells,\n");
        out.append("# Scholar/Master: 92, Grandmaster: 162). false = every paper uses 92.\n");
        out.append("# Affects newly generated papers only. Default: true. Accepted: true, false.\n");
        out.append("tierScaledSpheres = ").append(tierScaledSpheres).append("\n\n");

        out.append("# Wiggle strength of the energy currents. 0 = straight lines.\n");
        out.append("# Default: 1.0. Accepted: 0.0 or higher.\n");
        out.append("currentAmplitude = ").append(currentAmplitude).append("\n\n");

        out.append("# Animation speed of the currents (waves and pulse).\n");
        out.append("# Default: 1.0. Accepted: 0.0 or higher.\n");
        out.append("currentSpeed = ").append(currentSpeed).append("\n\n");

        out.append("# Thickness of the current ribbons. Default: 1.0. Accepted: roughly 0.1 to 4.0.\n");
        out.append("currentWidth = ").append(currentWidth).append("\n\n");

        out.append("# Gap between sphere cells. 0 = no borders, larger = chunkier.\n");
        out.append("# Default: 1.0. Accepted: 0.0 to about 3.5 (clamped).\n");
        out.append("cellBorderWidth = ").append(cellBorderWidth).append("\n\n");

        out.append("# \"aspects\" = each current blends the colors of its two linked aspects.\n");
        out.append("# \"custom\"  = currents use currentBaseColor and the pulse grades\n");
        out.append("#             currentPulseFrom -> currentPulseTo. Default: \"aspects\".\n");
        out.append("currentColorMode = \"").append(currentColorMode).append("\"\n\n");

        out.append("# Ribbon color in custom mode. Default: \"#8A6BB5\" (aetherium purple).\n");
        out.append("currentBaseColor = \"").append(toHex(currentBaseColor)).append("\"\n\n");

        out.append("# Pulse color at low intensity in custom mode. Default: \"#7FE8D8\" (teal glint).\n");
        out.append("currentPulseFrom = \"").append(toHex(currentPulseFrom)).append("\"\n\n");

        out.append("# Pulse color at peak intensity in custom mode. Default: \"#FFFFFF\".\n");
        out.append("currentPulseTo = \"").append(toHex(currentPulseTo)).append("\"\n");

        Files.createDirectories(path.getParent());
        Files.writeString(path, out.toString());
    }

    /** One-time migration from the short-lived JSON config. */
    private static void migrateLegacyJson() {
        Path legacy = Platform.getConfigFolder().resolve("new_age_thaum.json");
        if (!Files.exists(legacy) || Files.exists(file())) {
            return;
        }
        try {
            JsonObject json = new Gson().fromJson(Files.readString(legacy), JsonObject.class);
            if (json != null) {
                Map<String, String> values = new HashMap<>();
                for (String key : json.keySet()) {
                    if (!key.startsWith("_")) {
                        values.put(key, json.get(key).getAsString());
                    }
                }
                apply(values);
            }
            Files.delete(legacy);
            NewAgeThaum.LOGGER.info("Migrated legacy new_age_thaum.json to new_age_thaum.toml");
        } catch (Exception e) {
            NewAgeThaum.LOGGER.warn("Could not migrate legacy JSON config; starting from defaults", e);
        }
    }

    private static boolean parseBool(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseHex(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String toHex(int rgb) {
        return String.format("#%06X", rgb);
    }
}
