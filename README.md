<img src="src/main/resources/logo.png" alt="Create: Transit" width="180" align="right">

# Create: Transit

[![Modrinth](https://img.shields.io/modrinth/dt/create-transit?logo=modrinth&label=&suffix=%20&style=flat-square&color=242629&labelColor=5CA424&logoColor=1C1C1C)](https://modrinth.com/mod/create-transit)
[![CurseForge](https://img.shields.io/curseforge/dt/1634308?logo=curseforge&label=&suffix=%20&style=flat-square&color=242629&labelColor=F16436&logoColor=1C1C1C)](https://www.curseforge.com/minecraft/mc-mods/create-transit)

Welcome to Create: Transit, an addon that links separate logistics networks into a supply chain, letting one draw stock from another without merging the two.

> [!WARNING]  
> Early release: the core loop is playable and save-stable, but expect rough edges.

Networks stay sovereign here, and freight moves between them the way it crosses a border. A shipment leaves carrying a transit label naming the gate it has to clear, the gate strips that label off the package as it passes, and what continues on the far side is an ordinary local delivery. Where the borders run, which lanes exist and what is allowed to clear them are yours to lay out.

## The three blocks

| Block | What it does |
| --- | --- |
| **Transit Ticker** | Mounts a whole child network onto this one. Attach a Stock Link and the child's entire stock serves local requests, as if it were one big warehouse. Attach a Transit Link instead and the child becomes foreign territory reachable through a border. |
| **Transit Link** | Declares a border on this network. Set its transit label, and every shipment it forwards is stamped with the name of the gate it has to clear. A link with no label is simply disabled — clearing the label is how you close a border. |
| **Transit Gate** | Customs. Arriving packages have one transit label stripped; the parts of a declared order wait for each other and leave merged as a single delivery. Unrelated traffic passes straight through. |

Borders nest. A shipment can carry several labels and clear several gates — each border strips exactly one layer, so regional hubs, national networks and local depots compose the way real freight does. Stations, trains, chain conveyors and every vanilla logistics block work unmodified on each side.

## Upstream sources

This mod is built against these projects at these branches. See [CLAUDE.md](CLAUDE.md) for how to read them.

| Module | Repository | Branch | Version used here |
| --- | --- | --- | --- |
| Create | <https://github.com/Creators-of-Create/Create> | `mc1.21.1/dev` | `create_version=6.0.10-280` |
| Flywheel | <https://github.com/Engine-Room/Flywheel> | `1.21.1/dev` | `flywheel_version=1.0.6` |
| NeoForge | <https://github.com/neoforged/NeoForge> | `1.21.1` | `neo_version=21.1.235` |

This is the NeoForge 1.21.1 port branch; `main` remains the Forge 1.20.1 line
and the primary one. The offline-renderer tool chain (`scripts/mcrender`,
`dumpTransforms`/`checkTransforms`) is not ported yet — pixel-art work still
runs on `main`.

## Development

```bash
./gradlew build        # package the mod
./gradlew runClient    # launch the development client
```

All resource JSONs (models, recipes, lang) are hand-authored under
`src/main/resources` — there is no datagen, so `runData` does nothing here.
