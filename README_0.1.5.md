# Chill Zone Vanish 0.1.5 — First custom-message update

- Minecraft 26.3 source, Vanish-only repository (NO Homes classes).
- /vanish permission remains `chillzonevanish.command.vanish`.
- Fake vanish: aqua `- PlayerName` then gray ` left the server`.
- Fake unvanish: aqua `+ PlayerName` then gray ` joined the server`.
- Fake announcements only reach other connected players.
- Retains previous TAB removal, entity removal and `Players: X/Y` header count rewrite logic. This logic is NOT verified with the user's live TAB configuration; server testing is needed.
- Exact custom-message formatting is inferred from a compressed screenshot. If the live chat differs, adjust styling after an in-game comparison.
- No server/game build performed here. Do NOT treat as tested.
- Replace files in the GitHub Vanish repository fully to remove any old Homes source files.
