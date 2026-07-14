# Thaumaturgy: The New Age

A Thaumcraft-inspired magic mod of research, aura, and wonder. Multi-loader
(**Fabric** + **NeoForge**), multi-version via
[Stonecutter](https://stonecutter.kikugie.dev/) +
[Stonecraft](https://stonecraft.meza.gg/) + [Architectury API](https://docs.architectury.dev/).

Currently targeting Minecraft **1.21.1**. Full design in [docs/PLAN.md](docs/PLAN.md);
agent/contributor operational rules in [CLAUDE.md](CLAUDE.md).

## Building

Requires JDK 21. The first run downloads a large chunk of the modding toolchain
and can sit silent for several minutes — it is not hung.

```
./gradlew chiseledBuild            # build every {version}-{loader} node
./gradlew chiseledGameTest         # run GameTests on every node
./gradlew chiseledBuildAndCollect  # build + gather jars into build/libs/
./gradlew :1.21.1-neoforge:runClient   # dev client for one node
```

Switch the active development version with
`./gradlew "Set active project to 1.21.1-fabric"` (always switch back to
`1.21.1-neoforge` before committing).

## License

[MIT](LICENSE)
