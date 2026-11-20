# Iron Golem Spawn Checker - Build Guide (minecraft-26.3 branch)

Landing page: the `main` branch README. Report issues / support: https://github.com/Kishku7/mod_support

Point at a villager and see, in red, why it is not producing iron golems. This branch holds the one source
for the mod; one jar serves both the client and the server.

## What you need installed
- JDK 25 (Eclipse Temurin; Gradle finds it by major version, no path is pinned)
- Python 3 and Cog: `pip install cogapp`
- PowerShell 7

## How to build
```
pwsh scripts/build-fabric.ps1
```
The jar lands in `dist/` as `irongolemspawnchecker-<version>+26.3-fabric.jar`. The build logs to
`Fabric/_build.log`; it is only green when that log holds no `.java ... warning` line.

## Status of the platforms
| Loader | Line | State |
|---|---|---|
| Fabric | 26.3 | built and tested |
| NeoForge | 26.3 | planned (cell template kept) |
| Forge | 1.21.11 | planned (cell template kept) |
| Paper plugin | - | planned (cell template kept) |

Older Minecraft lines are planned too. Where they differ, the difference is isolated in `_codegen/compat.py`
with Cog markers in `shared`, never a second copy of a file.

## Repository layout
- `_codegen/cog_sources/common` - Minecraft-free Java (constants, reasons, result records).
- `_codegen/cog_sources/shared` - Minecraft code: the check logic, the packets, the client popup.
- `_codegen/cog_sources/fabric` - the Fabric packet wiring.
- `_codegen/compat.py` - per-loader entrypoints and the version axis.
- `_codegen/cell_templates` - per-loader build wiring.
- `scripts/` - `cog-gen.ps1` builds a whole cell from `_codegen`; `build-*.ps1` run it and Gradle.
- `Fabric/`, `NeoForge/`, `Forge/`, `Plugin/` - generated on every build, not tracked.

## How it works
The client sends the entity id of the villager under the crosshair. The server checks the same conditions vanilla
checks before it spawns a golem (a recent sleep, no golem nearby, five villagers that qualify within ten blocks,
villagers that can walk to each other, room to place the golem) and sends the list of what is blocking it,
plus the villager's number in its village. The client draws that as a popup while the crosshair stays on the
villager. The server only ever replies to a request, so a client without the mod is never sent anything, and a
client with the mod sends nothing to a server that does not have it.

## License and credit
Copyright (c) 2026 Kishku7. Released under the GNU General Public License v3.0 only (see `LICENSE`).

Inspired by Iron Golem Spawn Checker by MossShade (https://modrinth.com/mod/iron-golem-spawn-checker), a client-side
overlay that shows where iron golems can spawn. This mod is a separate work, written from the vanilla game code
for a different job (telling you why a villager is not producing golems); none of that mod's code is used here.
Thank you to its author for the idea.
