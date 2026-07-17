package io.github.minerguy341.new_age_thaum.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Hand-rolled cross-loader JSON config (PLAN §5: no loader-specific config APIs in
 * common code). Lives at {@code config/new_age_thaum.json}; missing keys are filled
 * with defaults and the file is rewritten, so it self-documents. {@link #maybeReload()}
 * re-reads when the file changes on disk — edit and reopen the orrery, no restart.
 */
public final class NewAgeThaumConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Research sphere size scales with paper tier (false: everything on GP(3,0)=92). */
    public static boolean tierScaledSpheres = true;

    /** Multipliers for the energy-current visuals on the research sphere. */
    public static double currentAmplitude = 1.0;
    public static double currentSpeed = 1.0;
    public static double currentWidth = 1.0;

    /** Cell border thickness on the research sphere (1.0 = default, 0 = none). */
    public static double cellBorderWidth = 1.0;

    private static long loadedModified = -1;

    private NewAgeThaumConfig() {
    }

    private static Path file() {
        return Platform.getConfigFolder().resolve("new_age_thaum.json");
    }

    public static void load() {
        Path path = file();
        try {
            if (Files.exists(path)) {
                JsonObject json = GSON.fromJson(Files.readString(path), JsonObject.class);
                if (json != null) {
                    if (json.has("tierScaledSpheres")) {
                        tierScaledSpheres = json.get("tierScaledSpheres").getAsBoolean();
                    }
                    if (json.has("currentAmplitude")) {
                        currentAmplitude = json.get("currentAmplitude").getAsDouble();
                    }
                    if (json.has("currentSpeed")) {
                        currentSpeed = json.get("currentSpeed").getAsDouble();
                    }
                    if (json.has("currentWidth")) {
                        currentWidth = json.get("currentWidth").getAsDouble();
                    }
                    if (json.has("cellBorderWidth")) {
                        cellBorderWidth = json.get("cellBorderWidth").getAsDouble();
                    }
                }
            }
            write(path);
            loadedModified = Files.getLastModifiedTime(path).toMillis();
            NewAgeThaum.LOGGER.info("Config loaded: tierScaledSpheres={}, currentAmplitude={}, currentSpeed={}, currentWidth={}",
                    tierScaledSpheres, currentAmplitude, currentSpeed, currentWidth);
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

    private static void write(Path path) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("tierScaledSpheres", tierScaledSpheres);
        json.addProperty("currentAmplitude", currentAmplitude);
        json.addProperty("currentSpeed", currentSpeed);
        json.addProperty("currentWidth", currentWidth);
        json.addProperty("cellBorderWidth", cellBorderWidth);
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(json));
    }
}
