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
