# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Starlit is a client-side Minecraft sky mod (mod id `starlit`, package `dev.starlit`) built from one source tree for Fabric, NeoForge and Forge across Minecraft 1.18.2 through 26.x. Read `docs/HANDOFF.md` first (local, gitignored): it holds current status and work in progress.

## Commands

Every Gradle invocation targets one loader + Minecraft version (defaults from `gradle.properties`: fabric, 26.3):

```sh
./gradlew build -Ploader=fabric -Pminecraft_version=26.3      # jar -> build/libs (starlit-<ver>+mc<label>.jar; other loaders starlit-<loader>-...)
./gradlew test                                                 # JUnit 5, MC-free core math (src/test)
./gradlew test --tests dev.starlit.core.SkyCoreTest            # single test class
./gradlew runClient -Ploader=<l> -Pminecraft_version=<v>       # dev client; on Fabric add -Pmodmenu for Mod Menu
./gradlew runSelftest -Ploader=<l> -Pminecraft_version=<v>     # in-game self-test, pass = "ALL CHECKS PASSED"
```

- Self-test: opens a 1920x1080 client (user `Starlit`), screenshots to `run/screenshots/sl-NN-*.png` (matched by filename). Any `-Psl.<key>=<val>` becomes `-Dstarlit.<key>`; quick modes `-Psl.only=look|settings|perf|day|gallery|iris`, plus `-Psl.seed`, `-Psl.shaders`. Day mode also checks pixels.
- Showcase video (Fabric 26.x only, needs JAVA_HOME = JDK 25): `./gradlew runShowcase -Psl.showcase=<dir> -Psl.ffmpeg=<exe> [-Psl.scenes=intro,dusk]`.
- Release: `docs/release-all.sh` builds every target into `build/release` (skips duplicate labels; `RESUME=1` resumes). Production self-test jars: `selftestJar`/`remapSelftestJar` (Fabric 1.x), `-Pprodselftest` on Forge.
- Java toolchain comes from the version's `java` property (25 for 26.x, 17 for 1.20.1, etc.).

Testing constraints: run only one Minecraft instance at a time (16 GB machine; parallel/background runs get OOM-killed). 1.18.2 dev runs occasionally crash with 0xC0000005 — rerun. Helper scripts and logs live in `test-archive/` (`iristest.py`, `realtest.py`, `release-tests.sh`, `runall.sh`).

Token-saving workflow:
- Gradle: always `-q`, pipe through `tail -n 30`; on failure grep for `error:` / `FAILED` instead of reading the full log.
- Self-test / iristest logs: grep for `ALL CHECKS PASSED`, `FAIL`, `Exception`, `Caused by` — never dump whole logs.
- Read files with targeted ranges or Grep, not whole files (`docs/HANDOFF.md` is large: read only the relevant section / latest session).
- Keep each session to 1–2 versions; update the handoff and `/clear` at milestones instead of letting context compact.
- Porting edits happen in the main session (pack families must stay consistent). Build + test runs may be delegated, when the user asks, to one subagent per version, strictly sequential, each returning only PASS or FAIL + error/file:line.

## Architecture

### Version selection
`build.gradle` is a single multi-target build. It loads `versions/<v>.properties` (Fabric) or `versions/<loader>/<v>.properties` (NeoForge/Forge). Those keys (`packs`, `label`, `range`, `java`, `shaders`, `shader_json`, `main_excludes`, loader/API versions, `pack_format`, …) drive plugin choice and are also expanded into resources (mod metadata). Plugins: Fabric 1.x → `fabric-loom-remap` (Mojang mappings); Fabric 26.x → unobfuscated `fabric-loom`; NeoForge → `net.neoforged.moddev`; Forge → `moddev.legacyforge` (mixin refmap, MixinExtras via jarJar).

### Source sets
The main source set is assembled per target:
- `src/main` — version-free core (config, `SkyPainter`, `dev.starlit.core` math with no Minecraft deps). `main_excludes` drops specific files on versions that can't compile them.
- `src/loader/<loader>/` — entrypoints and mod metadata.
- `src/compat/<pack>/` — one directory per pack listed in the version's `packs=`.
- `src/selftest` (+ `src/selftest/<loader>`, `src/compat/<pack>/selftest/java`) — in-game self-test mod `starlit_selftest`.
- `src/showcase` — Fabric 26.x only, enabled by `-Psl.showcase`.

### Compat packs
Version differences are isolated by **pack variants that each define the same fully-qualified class** (e.g. `dev.starlit.client.render.Gpu`, `dev.starlit.mixin.SkyRendererMixin`, `Clock`, `Frames`, `Texts`, `KeyRegistrar`, `ScreenOpener`, `StarlitOptionsScreen`, `ShaderSetup`, …). Each version's `packs=` picks exactly one variant per family. When changing an API in one pack, update every variant of that family.

- `gpu-*` packs implement `Gpu`/`GpuPipeline`/`GpuMesh`/`MeshWriter` for each rendering API era (`gpu-renderpearl` 26.3, `gpu-bindgroup` 26.2, `gpu-colortarget` 26.1, `gpu-blend*` 1.21.6–1.21.11, `gpu-uniform`, `gpu-coreshader`, `gpu-legacy*`, `gpu-1192`/`gpu-118` with bundled JOML).
- `hook-*` packs provide `SkyRendererMixin` + `LevelRendererOverlayMixin` for each era's sky entry point (modern `renderSunMoonAndStars`, legacy `LevelRenderer.renderSky`).

### Rendering flow
`SkyPainter` is version-free and talks only to the `Gpu` abstraction. Hooks call `SkyPainter.beforeCelestials` at HEAD and `afterCelestials` at TAIL of the sky celestial pass, cancel vanilla stars, and draw the End sky. A layer whose shader fails is disabled via `SkyPainter.broken` (logged, no crash).

### Shaders
Templates live in `src/shaders` (`*.vsh`, `*.fsh`, `noise.glsl`, `far.glsl`). The `generateShaders` task substitutes `STARLIT_VERTEX_HEADER`, `STARLIT_FRAGMENT_HEADER`, `STARLIT_NOISE`, `STARLIT_FAR` per the version's `shaders` style (renderpearl|moj|uniform) and writes to `build/generated/starlit-shaders/<style>-<json>/assets/starlit/shaders/core`. When `shader_json` ≠ `none`, program JSONs are generated from `programs.txt`, which must stay in sync with the pipelines declared in `SkyPainter`.

### Config & resources
`dev.starlit.config.StarlitConfig` (file `run/config/starlit.json` in dev), lang `src/main/resources/assets/starlit/lang/en_us.json`, mixins `src/main/resources/starlit.mixins.json`.

## Release notes
1.0.0 is published on CurseForge (project 1733952) and Modrinth (`starlit-sky`, s4gwsAGY). Never re-upload the 1.0.0 jars.
