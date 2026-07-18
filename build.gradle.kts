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

// Second dev client for multiplayer testing: `./gradlew :1.21.1-neoforge:runClient2`
// joins the first client's LAN world as "Dev2" (own run dir, so no file-lock clashes).
// If a Stonecraft/loom update ever breaks this block, it is safe to delete wholesale.
extensions.configure<net.fabricmc.loom.api.LoomGradleExtensionAPI> {
    runs.create("client2") {
        client()
        runDir("run/client2")
        programArgs("--username", "Dev2")
        ideConfigGenerated(false)
    }
}
