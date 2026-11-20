# Golem Doctor

**Why no golem?** Your villagers are not making iron golems and you do not know why. Point at one and the answer
appears in the middle of your screen, in red, in a few words.

## What you see
- **NO BED** - the villager has no bed to sleep in.
- **NOT SLEPT** - it has a bed but has not slept in the last day.
- **GOLEM NEARBY** - an iron golem is already close, so no new one is made.
- **3/5 READY** - only three of the five villagers a golem needs are ready.
- **CANNOT MEET** - walls, pens or water keep the villagers from walking up to each other.
- **NO SPAWN SPACE** - there is no room around the villager to place a golem.
- **SHOULD SPAWN** (green) - nothing is wrong.

Under that it shows which villager this is in its village ("Villager 3 of 5"), how big the village is in chunks,
and its coordinates.

## How it works
The server works out the answer from the same rules the game uses and the client only shows it. One jar serves
both. It works in singleplayer, on LAN and on dedicated servers. A server that has the mod does not need its
players to have it, and a player who has it loses nothing on a server that does not. The popup shows only while
your crosshair is on the villager and disappears the moment you look away. `/golemcheck <villager>` prints the
same answer in chat.

## Supported
Fabric, Minecraft 26.3. NeoForge, Forge and Paper builds are planned.

## Credit
Inspired by Iron Golem Spawn Checker by MossShade (https://modrinth.com/mod/iron-golem-spawn-checker), which
shows where golems can spawn. This is a separate work and uses none of its code. Thank you to its author for the idea.

## License
GNU General Public License v3.0 only. Copyright (c) 2026 Kishku7.

Questions and bug reports: https://github.com/Kishku7/mod_support/issues

Source and build guide: the `minecraft-26.3` branch.
