# Golem Doctor - Build Guide

Landing page: `README.md`. Report issues / support: https://github.com/Kishku7/mod_support

Why no golem? Point at a villager and see, in red, why it is not producing iron golems. This branch holds the
one source for the mod; one jar serves both the client and the server.

## What you need installed
- JDK 25 (Eclipse Temurin; Gradle finds it by major version, no path is pinned)
- Python 3 and Cog: `pip install cogapp`
- PowerShell 7

## How to build
```
pwsh scripts/build-fabric.ps1
```
The jar lands in `dist/` as `golemdoctor-<version>+26.3-fabric.jar`. The build logs to
`Fabric/_build.log`; it is only green when that log holds no `.java ... warning` line.

## Toolchain matrix
| Loader | Minecraft | JDK | Build tool |
|---|---|---|---|
| Fabric | 26.3 | 25 | fabric-loom, mojmap names, Gradle 9.4 |
| NeoForge | 26.3 | 25 | planned (ModDevGradle) |
| Forge | 1.21.11 | 21 | planned (ForgeGradle) |

## Status of the platforms
| Loader | Line | State |
|---|---|---|
| Fabric | 26.3 | built and tested |
| NeoForge | 26.3 | planned (cell template kept) |
| Forge | 1.21.11 | planned (cell template kept) |
| Paper plugin | - | planned (cell template kept) |

Older and newer Minecraft lines (1.21.11 to 26.4) are planned. Where they differ, the difference is isolated in
`_codegen/compat.py` with Cog markers in `shared`, never a second copy of a file. Already seamed: the entity type
class (`EntityType` before 26.2, `EntityTypes` from 26.2) and the GUI draw class (`GuiGraphics` on 1.21.11,
`GuiGraphicsExtractor` from 26.1).

## Repository layout
- `_codegen/cog_sources/common` - Minecraft-free Java (constants, reasons, result records).
- `_codegen/cog_sources/shared` - Minecraft code: the check logic, the packets, the client popup, the `/golemlog` counter.
- `_codegen/cog_sources/fabric` - the Fabric packet wiring and the one mixin.
- `_codegen/compat.py` - per-loader entrypoints and the version axis.
- `_codegen/cell_templates` - per-loader build wiring.
- `scripts/` - `cog-gen.ps1` builds a whole cell from `_codegen`; `build-*.ps1` run it and Gradle.
- `Fabric/`, `NeoForge/`, `Forge/`, `Plugin/` - generated on every build, not tracked.

## How the code generation works
Every Java file lives once under `_codegen/cog_sources`. `scripts/cog-gen.ps1` wipes a cell folder and rebuilds it
from that one source: the build files from `_codegen/cell_templates`, the generated entrypoints from
`_codegen/compat.py`, and the shared Minecraft code with Cog blocks resolved for the cell's Minecraft version.
Nothing inside `Fabric/`, `NeoForge/`, `Forge/` or `Plugin/` is edited by hand.

## How it works
The client sends the entity id of the villager under the crosshair. The server checks the same conditions vanilla
checks before it spawns a golem (a recent sleep, no golem seen recently, three villagers that qualify within ten blocks
for the scare method or five for gossip, villagers that can walk to each other, room to place the golem) and sends the list of what is blocking it,
plus the villager's number in its village. The client draws that as a popup while the crosshair stays on the
villager. The server only ever replies to a request, so a client without the mod is never sent anything, and a
client with the mod sends nothing to a server that does not have it.

## Golem spawn log
`/golemlog` counts iron golems as the server adds them. One mixin on `ServerLevel.addFreshEntity` records each
fresh golem (never one loaded back from a chunk, never one a player built from pumpkins). History is kept for an hour in
memory. `/golemlog now` prints rolling 15 and 60 minute figures, the busiest chunks and the last spawn; `/golemlog start [chunks]`
sends a report once a minute to whoever asked (a player or the console), with a near-you count within that many chunks of
the player (their own chunk is 0, default 8); `/golemlog stop` and `/golemlog radius <chunks>` do what they say.
`/golemlog towers [minutes]` groups the spawns of the last 1 to 60 minutes (default 15) into towers, spawns within 24 blocks
of each other being one tower, and prints one row per tower: count, rate, gap between spawns (average, shortest, longest),
how long ago the last spawn was and the distance from you. A tower whose last spawn keeps getting older has stalled.
Iron figures are estimates at 4 ingots per golem.

## License and credit
Copyright (c) 2026 Kishku7. Released under the GNU General Public License v3.0 only (see `LICENSE`).

Inspired by Iron Golem Spawn Checker by MossShade (https://modrinth.com/mod/iron-golem-spawn-checker), a client-side
overlay that shows where iron golems can spawn. This mod is a separate work, written from the vanilla game code
for a different job (telling you why a villager is not producing golems); none of that mod's code is used here.
Thank you to its author for the idea.
