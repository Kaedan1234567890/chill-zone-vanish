# Chill Zone Vanish 0.1.0-alpha

Minecraft 26.2 / Fabric / server-side.

## Command
`/vanish`

## LuckPerms permission
`chillzonevanish.command.vanish`

Recommended:
```
/lp group owner permission set chillzonevanish.command.vanish true
/lp group admin permission set chillzonevanish.command.vanish false
/lp group mod permission set chillzonevanish.command.vanish false
/lp group member permission set chillzonevanish.command.vanish false
```

## Alpha behaviour
- Fake LuckPerms-prefix leave message on vanish
- Fake LuckPerms-prefix join message on unvanish
- Removes vanished player from TAB for other players
- Keeps the player entity normally tracked and uses Minecraft invisibility instead
- Hides armour/held items client-side without deleting the player entity
- New players do not see already-vanished players
- Simple Voice Chat is intentionally untouched

## Test first
This is an alpha. Test with a second Java account/friend before relying on it.

## Fix 2
Minecraft 26.2 is unobfuscated, so this project intentionally does not declare Mojang/Yarn mappings. GitHub Actions uses Gradle 9.5.1, matching Fabric's 26.2 guidance.

## Fix 4
Removed the obsolete ServerPlayer.hasPermissions(int) fallback. Vanish now fails closed if LuckPerms is unavailable.

## Fix 5
Minecraft 26.2 moved registered entity constants from EntityType to EntityTypes. The unvanish spawn packet now uses EntityTypes.PLAYER.


## Fix 7
- Stops repeatedly sending entity-removal packets every second.
- Re-hides a vanished player when a viewer enters tracking range.
- Detects large position jumps such as staff teleport and re-hides once after the jump.
- Keeps existing TAB hiding, locator-bar hiding, fake join/leave messages, LuckPerms permission, and equipment resync behavior.

## Fix8 — Staff TP compatibility
Adds a soft compatibility bridge for Chill Zone Staff TP. During `/tpto` or `/tphere`, Vanish delays the re-hide packet by a few ticks so the teleport packet can settle first. This prevents the teleport and vanish entity-removal packets from colliding.

Pair this build with Chill Zone Staff TP 0.1.0-fix3 or newer.


## Fix9 — Safe entity tracking
- Removes the unsafe `ClientboundRemoveEntitiesPacket` vanish behaviour.
- Removes the manual `ClientboundAddEntityPacket` unvanish behaviour.
- Uses Minecraft's normal `ServerPlayer#setInvisible(true/false)` metadata so the server and client agree that the player entity still exists.
- Keeps the vanished player out of TAB.
- Sends empty equipment only to viewers while vanished so armour/held items are not left floating.
- Restores real equipment on unvanish.
- Keeps the Staff TP compatibility bridge, but `/tpto` no longer needs to race against entity removal packets.

This fix specifically targets clients being disconnected when `/vanish` is toggled or when a vanished staff member enters tracking range.
