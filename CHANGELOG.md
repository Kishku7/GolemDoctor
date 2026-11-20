# Changelog

## [1.0.0] - 2026-11-20
First release. One jar per loader and Minecraft line for both client and server.

- Point the crosshair at a villager and a popup appears in the middle of the screen. It stays while the
  crosshair is on that villager and is gone the moment it leaves.
- The server works out why the villager is not producing iron golems and sends the answer. The client
  only displays it. Every blocker found is listed in red, one to three words each: NO BED, NOT SLEPT,
  GOLEM NEARBY, n/5 READY, CANNOT MEET, NO SPAWN SPACE. When nothing is wrong it shows SHOULD SPAWN in green.
- The popup also shows the villager number in its village ("Villager 3 of 5"), the village size in chunks
  and the village coordinates. A village is grouped from nearby beds, job sites and bells.
- Works in singleplayer, on LAN and on dedicated servers. A server with the mod does not need clients to have
  it, and a client with the mod does nothing on a server without it.
- `/golemcheck <villager>` prints the same answer as chat lines.
- Fabric 26.3 is built and tested. NeoForge 26.3, Forge 1.21.11 and a Paper plugin are planned.

Licensed GPL-3.0-only. Credit: the idea of a spawn checker for iron golems was inspired by MossShade's Iron Golem
Spawn Checker. No code from that mod is used.
