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
