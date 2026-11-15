# WeaverLib 0.1.15

An initial rune and incantation mod for Minecraft 1.21.1, NeoForge 21.1.256, and Java 21. By Ex.

## Build and install

Run `gradlew.bat build` on Windows or `./gradlew build` on Linux/macOS with a Java 21 JDK. Install the JAR from `build/libs` on the server and each client alongside [Thaumaturge 1.1.0 for NeoForge 1.21.1](https://www.curseforge.com/minecraft/mc-mods/thaumaturge/files/9452543), Curios, and Lithostitched. Gradle downloads the pinned development dependencies. For development launches, use `gradlew.bat runClient` or `gradlew.bat runServer` with Java 21.

## Casting

| Incantation | Effect on the caster | Cost |
| --- | --- | ---: |
| `incant lux` | 60 seconds of night vision | 5 Vis |
| `incant sana` | Heals 2 hearts | 8 Vis |

Type incantations in regular chat with no slash. If the first word is `incant` (case-insensitive), only players within 10 blocks in the same dimension receive the message, even if the spell fails. Casting is available to living, non-spectator players who have earned the Warp unlock.

Hold **K** to open the parchment canvas and **G** to cycle the optional rune guides. The numbers mark the start of each stroke. The right page shows the latest rune match, and Tab and G play a quiet page-turn sound when you change runes.

- **Lux** uses a V drawn from upper left, down, then up to the right. Draw the crossbar left to right.
- **Sana** starts with a rising, kinked stem that turns right at the top. From the middle of the stem, draw a short branch toward the lower right. Finish with a detached clockwise ring, starting at the top.

Drag with the left mouse button, or the right mouse button if you bind the canvas to left click. Release the canvas key to cast. Your drawing and its rune settings will be there when you reopen the canvas. Use **Ctrl+Z** to undo one stroke, or **Esc** to clear the drawing and close without casting. The drawing is kept for the current game session. Ordered Lux and Sana matches have full potency. Recognizable Lux and Sana shapes drawn out of order can cast at half potency for the same Vis cost.

The server recognizes the submitted ink and runs the same spell logic for both inputs. Vis comes from Thaumaturge's aura in the caster's current chunk. Insufficient Vis, unknown inputs, full-health healing and redundant night vision leave the aura unspent. There is a shared one-second interval between attempts, and native spells also use Thaumaturge’s computed cooldown. Lux and Sana each accept up to three optional modifiers.

The geometry matcher is adapted from the MIT-licensed AonDor port kit. See LICENSE for its copyright notice.

## Unlocking rune and word casting

Reach **50 combined Normal and Permanent Warp** to unlock both casting methods. Temporary Warp and bonuses from equipment do not count. The unlock is saved to the player and survives death, reconnecting, and purification. On a natural unlock, a two-part message appears above the hotbar. A locked casting attempt displays your current progress without spending Vis.

The server config entry `requiredWarp` in `weaverlib-server.toml` controls the threshold. Set it to `0` to disable the Warp requirement. Changing the threshold affects players who have yet to earn the unlock.

Operators can type `/weaverwarp unlock` or `/weaverwarp lock` for themselves. Add an online player name to target someone else. Both arguments must be typed manually. A manual lock takes precedence over the threshold and lasts until an operator unlocks that player.

## Lux and Sana rune slots

Draw the main effect near the center. Press **Tab** between complete runes to mark the end of a glyph. Draw smaller modifiers around the effect. They are read clockwise from the top, with nearer runes first at the same angle. **G** cycles the guides. Main effects can be traced on the canvas, and modifier guides appear on the right page.

| Slot | Default | Modifier |
| --- | --- | --- |
| Form | Affect the selected target | **Pulse** affects up to eight eligible targets within three blocks of the selected target, visible to the caster. |
| Target | Caster | **Gaze** selects the living entity you're looking at, up to eight blocks away. |
| Power | Base effect | **Surge** multiplies duration or healing by 1.5 and rounds its Vis multiplier up per recipient. |

**Null** skips one slot and uses its default. Trailing slots can be omitted. For example, draw Lux in the center, Null above it, and Gaze to its right to give another player night vision. For stronger self-healing, draw Sana with Null, Null, Surge in clockwise order.

Lux affects players. Sana can heal living targets. Pulse charges per affected target plus 2 Vis. A malformed spell, missing target, or insufficient Vis leaves the aura unspent. Typed incantations use the effect's defaults.

## Thaumaturge focus magic

Native effects, deliveries, modifiers, and flows have Latin-inspired chat words and drawn runes. For example, `incant cura auge fulgor` casts Heal through Amplify and Beam. Old English spell words still work. See the [component and argument reference](docs/thaumaturge-components.md) for the full catalog, spell construction, settings, and aspect choices.
