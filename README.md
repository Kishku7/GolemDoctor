# Golem Doctor

**Why no golem?** Your villagers are not making iron golems and you do not know why. Point at one and the answer
appears in the middle of your screen, in plain words, with every problem listed at once.

## What you see
The popup has two parts.

**VILLAGER** - what is wrong with this one villager:
- **Has no bed** - it has nowhere to sleep.
- **Not Slept in 16 minutes** - it has not slept recently enough (a golem needs a sleep within the last day).
- **Saw Golem 12 seconds ago** - it saw an iron golem very recently and will not agree to another yet.

**GROUP** - the villagers around it and the spot a golem would appear:
- **Scare: 2 ready, needs 3** - the zombie-scare method, the one most farms use. Green when enough villagers are ready.
- **Gossip: 2 ready, needs 5** - the older method, where villagers meet and talk. Green when met.
- **Can't Gossip** - walls, pens or water keep the villagers from walking up to each other.
- **Can't Reach Bell** - the villager cannot walk to its bell.
- **No Spawn Space for Golem** - there is no room around the villager to place a golem.
- **Should Spawn (scare)** / **Should Spawn (gossip)** (green) - nothing is blocking that method.

Under that it shows which villager this is in its village ("Villager 3 of 5"), how big the village is in chunks,
and its coordinates.

## How it works
The server works out the answer from the same rules the game uses and the client only shows it. One jar serves
both. It works in singleplayer, on LAN and on dedicated servers. A server that has the mod does not need its
players to have it, and a player who has it loses nothing on a server that does not. The popup stays for five
seconds after you look away from the villager, so you can read it even if the villager runs off.
`/golemcheck <villager>` prints the same answer in chat.

## Supported
Fabric, Minecraft 26.3. Older and newer versions (1.21.11 to 26.4) and NeoForge are planned.

## Credit
Inspired by Iron Golem Spawn Checker by MossShade (https://modrinth.com/mod/iron-golem-spawn-checker), which
shows where golems can spawn. This is a separate work and uses none of its code. Thank you to its author for the idea.

## License
GNU General Public License v3.0 only. Copyright (c) 2026 Kishku7.

Questions and bug reports: https://github.com/Kishku7/mod_support/issues

Source and build guide: the `minecraft-26.3` branch.
