# Create: Transit

Welcome to Create: Transit, an addon that links separate logistics networks into a supply chain, letting one draw stock from another without merging the two.

Networks stay sovereign here, and freight moves between them the way it crosses a border. A shipment leaves carrying a transit label naming the gate it has to clear, the gate strips that label off the package as it passes, and what continues on the far side is an ordinary local delivery. Where the borders run, which lanes exist and what is allowed to clear them are yours to lay out.

- **Minecraft**: 1.20.1 (Forge)
- **Depends on**: Create 6.x (registered through CreateRegistrate)
- **Mod id**: `create_transit`
- **License**: All Rights Reserved
- **Status**: in development, unreleased

## Upstream sources

This mod is built against these projects at these branches. See [CLAUDE.md](CLAUDE.md) for how to read them.

| Module | Repository | Branch | Version used here |
| --- | --- | --- | --- |
| Create | <https://github.com/Creators-of-Create/Create> | `mc1.20.1/dev` | `create_version=6.0.8-289` |
| Flywheel | <https://github.com/Engine-Room/Flywheel> | `1.20.1/dev` | `flywheel_version=1.0.5` |
| Minecraft Forge | <https://github.com/MinecraftForge/MinecraftForge> | `1.20.1` | `forge_version=47.4.22` |
| JEI | <https://github.com/mezz/JustEnoughItems> | `1.20.1` | `jei_version=15.21.0.148` |
| JEI Integration | <https://github.com/SnowShock35/JEI-Integration> | `main` (archived) | `jei_integration_file=4999754` |

JEI and JEI Integration are development-client conveniences only
(`runtimeOnly`; the shipped mod neither needs nor notices them). JEI
Integration is archived — its 1.20.1 code lives on `main`, and it is pinned
by CurseForge file id (list files via <https://api.cfwidget.com/265917>).

## Development

```bash
./gradlew build        # package the mod
./gradlew runClient    # launch the development client
```

All resource JSONs (models, recipes, lang) are hand-authored under
`src/main/resources` — there is no datagen, so `runData` does nothing here.

Dependencies are set up as described in the official guide: <https://wiki.createmod.net/developers/depend-on-create/forge-1.20.1>
