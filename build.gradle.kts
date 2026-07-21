import gg.meza.stonecraft.mod

plugins {
    id("gg.meza.stonecraft")
}

modSettings {
    clientOptions {
        fov = 90
        guiScale = 3
        narrator = false
        darkBackground = true
        musicVolume = 0.0
    }
}

dependencies {
    // Architectury API: per-loader artifact resolved from the node name suffix,
    // version pinned per Minecraft version in versions/dependencies/{mc}.properties
    "modApi"("dev.architectury:architectury-${mod.loader}:${mod.prop("architectury_version")}")
}

// Second dev client for multiplayer testing, e.g. `./gradlew :1.21.1-fabric:runClient2`
// (match the loader of the first client) — joins the first client's LAN world as "Dev2".
// If a Stonecraft/loom update ever breaks this block, it is safe to delete wholesale.
extensions.configure<net.fabricmc.loom.api.LoomGradleExtensionAPI> {
    runs.create("client2") {
        client()
        ideConfigGenerated(false)
    }
}
// Stonecraft reconfigures all runs in afterEvaluate (shared run dir, injects
// --username=developer), so client2's overrides must be applied after that.
afterEvaluate {
    extensions.configure<net.fabricmc.loom.api.LoomGradleExtensionAPI> {
        runs.named("client2") {
            runDir("run/client2")
            programArgs.removeIf { it.startsWith("--username") }
            programArgs("--username", "Dev2")
        }
    }
}
