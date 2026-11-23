# Changelog

## [1.5.0] - 2026-11-22
- New `/golemlog towers [minutes]`: one row per golem tower, so a farm that has stopped producing stands out.
  Spawns within 24 blocks of each other (same dimension) count as one tower. Each row gives the golem count, the
  rate per minute, the average, shortest and longest gap between spawns, how long ago the last spawn was and, for a
  player, how far away the tower is. Minutes of history default to 15 (1 to 60), and rows are sorted busiest first.
- A tower with a very long longest gap, or a last spawn that keeps getting older, usually has a golem that never died
  and is keeping its villagers from spawning another.
- Built and tested for Fabric 26.3 only so far.

## [1.4.0] - 2026-11-21
- New `/golemlog` command: counts iron golems that spawn on the server and reports once a minute, to whoever
  turned it on (a player in chat, or the server console).
- Each report gives the server count for the minute, the running average, an estimated iron per hour, and, when a
  player asked, how many of those spawned within a radius of that player (default 8 chunks, the player's own chunk
  being chunk 0).
- `/golemlog now` prints rolling 15 minute and 60 minute totals, the busiest chunks with coordinates and the most
  recent spawn. `/golemlog radius <chunks>` and `/golemlog stop` do what they say.
- Needs the mod on the server. Uses one small mixin (the server adding a new entity).

## [1.3.0] - 2026-11-21
- Fabric builds for Minecraft 1.21.11, 26.1 (26.1 to 26.1.2), 26.2, 26.3 and 26.4 (built against 26.4-snapshot-10).
- Each build declares the Minecraft range and the Fabric API floor it was built and tested with.
- No change in behaviour or wording.

## [1.2.1] - 2026-11-21
- The jar manifest now carries the mod version (Implementation-Version).
- The Fabric API floor in the manifest is the version the mod was tested with (0.166.2), not an untested lower one.

## [1.2.0] - 2026-11-21
- The popup shows both ways a golem comes about at once, scare first: "Scare: n ready, needs 3" and
  "Gossip: n ready, needs 5", each green when met and red when short. Scare no longer waits for the villager
  to be panicking, so the usual zombie-scare farm is judged on its own number.
- Every problem is listed at once, in plain wording: Has no bed, Not Slept in N minutes, Saw Golem N seconds ago,
  Can't Gossip, Can't Reach Bell, No Spawn Space for Golem. Can't Gossip and Can't Reach Bell only affect gossip.
- "Should Spawn (scare)" and "Should Spawn (gossip)" show when nothing blocks that method.
- Has no bed is only listed while the villager has not slept.
- `/golemcheck` prints the same scare and gossip lines.

## [1.1.0] - 2026-11-20
- The popup now has two sections: VILLAGER (what is wrong with this one villager: NO BED, NOT SLEPT, GOLEM NEARBY,
  CANNOT MEET) and GROUP (villagers in range and how many are ready, NEED n READY, NO SPAWN SPACE, village size and
  coordinates).
- Panic is understood. Vanilla asks for 5 ready villagers when villagers gossip, but only 3 when a villager is
  panicking (a hostile mob in sight, or hurt). The popup shows the number that applies right now, and mentions the
  other one. CANNOT MEET is not reported for a panicking villager, since panic does not need villagers to meet.
- The popup stays for 5 seconds after the crosshair leaves the villager, and keeps refreshing, so a villager that is
  running away can still be read.
- A village is now counted by the bell its villagers gather at when they have one (villagers that share a bell belong
  together), falling back to the old grouping by nearby beds, job sites and bells.
- `/golemcheck` prints the group line and marks each blocker as villager or group.

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
