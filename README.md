# WeaverLib 0.1.2

An initial rune and incantation mod for Minecraft 1.21.1, NeoForge 21.1.256, and Java 21. By Ex.

## Build and install

Run `gradlew.bat build` on Windows or `./gradlew build` on Linux/macOS with a Java 21 JDK. Install the JAR from `build/libs` on the server and each client alongside [Thaumaturge 1.1.0 for NeoForge 1.21.1](https://www.curseforge.com/minecraft/mc-mods/thaumaturge/files/9452543), Curios, and Lithostitched. Gradle downloads the pinned development dependencies. For development launches, use `gradlew.bat runClient` or `gradlew.bat runServer` with Java 21.

## Casting

| Incantation | Effect on the caster | Cost |
| --- | --- | ---: |
| `/incant lux` | 60 seconds of night vision | 5 Vis |
| `/incant sana` | Heals 2 hearts | 8 Vis |

Word casting takes a typed incantation. Both spells are available to all living, non-spectator players.

Hold **K** to open the parchment canvas and **G** to cycle the optional rune guides. The numbers mark the start of each stroke.

- **Lux** uses a V drawn from upper left, down, then up to the right. Draw the crossbar left to right.
- **Sana** starts with a rising, kinked stem that turns right at the top. From the middle of the stem, draw a short branch toward the lower right. Finish with a detached clockwise ring, starting at the top.

Drag with the left mouse button, or the right mouse button if you bind the canvas to left click. Use **Ctrl+Z** to undo a stroke. Release the canvas key to cast, or press **Esc** to cancel. Ordered matches have full potency. Recognizable shapes drawn out of order can cast at half potency for the same Vis cost.

The server recognizes the submitted ink and runs the same spell logic for both inputs. Vis comes from Thaumaturge's aura in the caster's current chunk. Insufficient Vis, unknown inputs, full-health healing and redundant night vision leave the aura unspent. There is a shared one-second interval between attempts. The first release handles one rune at a time.

The geometry matcher is adapted from the MIT-licensed AonDor port kit. See LICENSE for its copyright notice.
