# Aqua Client (Fabric 1.21.11)

A client-side Fabric mod project targeting Minecraft 1.21.11 and Java 21.

## Build with GitHub Actions

Push this project to the `main` branch or open **Actions → Build Aqua Client → Run workflow**.
The workflow uses Java 21 and uploads the built mod JAR as an artifact named `AquaClient`.

For a local build, use JDK 21 and Gradle, then run `gradle build`. The JAR is created in `build/libs/`.

## Controls

- Right Shift: open/close ClickGUI
- Select StorageFinder or SpawnerFinder to edit that feature's settings
- Save settings or close the GUI to save to `config/aquaclient.json`

## Finder behavior

- StorageFinder scans chests, trapped/ender chests, shulker boxes, droppers, dispensers and barrels.
- SpawnerFinder scans mob spawners independently.
- Both scan only already-loaded client chunks, within their configured horizontal range and a 24-block vertical band.
- Scanning runs twice per second.
- Both draw configurable-colour, configurable-opacity outlines.

The GUI still exposes options for fill, tracers, through-wall rendering, pulse and line thickness, but those effects are not implemented by this renderer yet. The current implementation draws standard depth-tested outlines. This project has not been compiled or tested in-game in this environment.
