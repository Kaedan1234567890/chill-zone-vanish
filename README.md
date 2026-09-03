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
- Removes vanished player entity from other clients
- Re-applies hiding once per second
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
