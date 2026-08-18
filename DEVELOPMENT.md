# Development

Two mods in one repo. `transit/` is Create: Transit, `route/` is [Create: Routes](route/README.md);
each has its own `gradle.properties`, mixin config and lang namespace, and builds its own jar.

```bash
./gradlew build        # both mods
./gradlew runClient    # development client, both loaded
```

Jars land in `transit/build/libs/` and `route/build/libs/`, one per mod.

## Upstream sources

Built against these projects at these branches. See [CLAUDE.md](CLAUDE.md) for how to read them.

| Module | Repository | Branch | Version used here |
| --- | --- | --- | --- |
| Create | <https://github.com/Creators-of-Create/Create> | `mc1.20.1/dev` | `create_version=6.0.8-289` |
| Flywheel | <https://github.com/Engine-Room/Flywheel> | `1.20.1/dev` | `flywheel_version=1.0.5` |
| Minecraft Forge | <https://github.com/MinecraftForge/MinecraftForge> | `1.20.1` | `forge_version=47.4.22` |

## Resources

All JSON — models, recipes, lang — is hand-authored under `*/src/main/resources`. There is no
datagen, so `runData` does nothing here.

Some PNGs are not. These are generated from Create's own pixels in the pinned jar, so **editing
them by hand is undone on the next run**:

| Script | Writes |
| --- | --- |
| `scripts/route_logo.py` | `route/src/main/resources/logo.png` |
| `scripts/repalette.py` | `transit/…/textures/block/` |
| `scripts/transit_package.py` | `transit/…/textures/item/package…` |
| `scripts/draw_curtain.py` | the gate curtain's texture and its three models |

`scripts/lang_audit.py` and `scripts/render_model.py` only read: one checks the lang files
against the code, the other renders models to PNG without launching the game.

```bash
uv run scripts/lang_audit.py
```

[`.github/workflows/check.yml`](.github/workflows/check.yml) builds both mods and runs that audit
on every push. The generated art stays out of it: the source pixels live in the pinned Create jar,
so bumping `create_version` would fail the build over textures that still ship fine. Re-run the
generators after such a bump and read the `git diff` — the committed PNG is the previous output,
so version control is already the comparison.

## Release

One workflow per mod, each triggered by its own tag prefix and each building only its own
subproject — the other one failing to compile is not a reason a release can't go out.

| Tag | Workflow | Releases |
| --- | --- | --- |
| `v0.1.2-mc1.20.1` | [publish-transit.yml](.github/workflows/publish-transit.yml) | Create: Transit |
| `routes-v0.1.0-mc1.20.1` | [publish-routes.yml](.github/workflows/publish-routes.yml) | Create: Routes |

`v*` is Transit's because every tag in the repo's history already is one.
