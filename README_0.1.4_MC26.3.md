# Chill Zone Vanish 0.1.4 — Minecraft 26.3 port candidate

Updated Fabric loader, Loom, Minecraft and Fabric API build target to 26.3 using versions already present in the working Moderation 0.3.18 source. Kept vanish features and logic unchanged; isolated vanish source from unrelated Homes code bundled in previous ZIP.

**UNVERIFIED:** no Gradle/Java 25 build completed locally; Minecraft APIs/mixin injection target may require additional changes. Do NOT replace a live working JAR until GitHub Actions build succeeds and testing confirms the mod loads.

Run GitHub Actions > Build. If build fails, copy the first Java compilation error, not just the failed status.

Permission: `chillzonevanish.command.vanish` for staff via LuckPerms. No changes to fake join/leave messages, TAB header rewrite, or vanish behavior in this pass.
