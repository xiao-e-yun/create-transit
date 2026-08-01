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

Both are conveniences for the development client only: they are `runtimeOnly`
dependencies, no source file mentions either, and the shipped mod neither
requires them nor behaves differently when they are present.

JEI Integration breaks the table's own rules twice, and both are deliberate.
Its 1.20.1 code is on `main`, not on a version branch — the repository was
archived after that port, so for once the default branch is the right one to
read. And it is pinned by a CurseForge file id rather than a version, because
CurseForge is the only place it is published; nothing about `4999754` can be
worked out or bumped by hand. To find another, ask a CurseForge mirror for the
project's file list (`https://api.cfwidget.com/265917`) and read the id off the
build you want.

Despite the name it needs nothing from JEI: the 1.20.1 jar is four classes
hanging off Forge's `ItemTooltipEvent`, and its own `mods.toml` marks `jei` as
a non-mandatory dependency. It is here to put registry names and tags in
tooltips, which is worth having while building blocks that are copies of
Create's and have to be told apart.

## Development

```bash
./gradlew build        # package the mod
./gradlew runClient    # launch the development client
```

All resource JSONs (models, recipes, lang) are hand-authored under
`src/main/resources` — there is no datagen, so `runData` does nothing here.

Dependencies are set up as described in the official guide: <https://wiki.createmod.net/developers/depend-on-create/forge-1.20.1>
