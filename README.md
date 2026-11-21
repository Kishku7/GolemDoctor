# Golem Doctor

**Why no golem?** Your villagers are not making iron golems and you do not know why. Point at one and the answer appears in the middle of your screen, in plain words, with every problem listed at once. One jar works on the client and the server, and a server that has it does not need its players to have it.

Ships as a **Fabric** mod.

## Why Golem Doctor

The game decides whether a villager makes an iron golem from a handful of hidden rules: a recent sleep, no golem seen lately, enough villagers ready nearby, a way for them to meet, and room to place the golem. When a farm does nothing, you cannot see which rule is failing. Golem Doctor shows you, and it checks both ways a golem can be made: the zombie-scare method most farms use, and the older gossip method.

## Using Golem Doctor

Point your crosshair at a villager. A popup appears with two parts.

**VILLAGER** - what is wrong with this one villager:
- **Has no bed** - it has nowhere to sleep.
- **Not Slept in 16 minutes** - it has not slept recently enough (a golem needs a sleep within the last day).
- **Saw Golem 12 seconds ago** - it saw an iron golem very recently and will not agree to another yet.

**GROUP** - the villagers around it and the spot a golem would appear:
- **Scare: 2 ready, needs 3** - the zombie-scare method. Green when enough villagers are ready.
- **Gossip: 2 ready, needs 5** - the gossip method. Green when met.
- **Can't Gossip** - walls, pens or water keep the villagers from walking up to each other.
- **Can't Reach Bell** - the villager cannot walk to its bell.
- **No Spawn Space for Golem** - there is no room around the villager to place a golem.
- **Should Spawn (scare)** or **Should Spawn (gossip)**, in green - nothing is blocking that method.

Under that it shows which villager this is in its village ("Villager 3 of 5"), how big the village is in chunks, and its coordinates. The popup stays for five seconds after you look away, so you can still read it if the villager runs off. `/golemcheck <villager>` prints the same answer in chat.

It works in singleplayer, on LAN and on dedicated servers. The server works out the answer from the same rules the game uses and the client only shows it. A player who has the mod loses nothing on a server that does not.

## Source

Source code: [`minecraft-26.3`](https://github.com/Kishku7/GolemDoctor/tree/minecraft-26.3).

## Support

Report bugs or request features at https://github.com/Kishku7/mod_support/issues

Inspired by the Iron Golem Spawn Checker mod by MossShade, which shows where golems can spawn. This is a separate work and uses none of its code. Thank you to its author for the idea.

By Kishku7. Licensed GPL-3.0-only.
