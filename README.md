# WeaverLib 0.2.11

An initial rune and incantation mod for Minecraft 1.21.1, NeoForge 21.1.256, and Java 21. By Ex.

## Build and install

Run `gradlew.bat build` on Windows or `./gradlew build` on Linux/macOS with a Java 21 JDK. Install the JAR from `build/libs` on the server and each client alongside [Thaumaturge 1.1.0 for NeoForge 1.21.1](https://www.curseforge.com/minecraft/mc-mods/thaumaturge/files/9452543), Curios, and Lithostitched. Gradle downloads the pinned development dependencies. For development launches, use `gradlew.bat runClient` or `gradlew.bat runServer` with Java 21. `build` also runs the standalone angel progression and payment checks (`angelRulesTest`).
## Angel form — first playable form

Becoming an angel requires **25 completed gameplay advancements**. This is an eligibility requirement; reaching it does not transform or awaken a player automatically. Operators can use `/weaverangel grant <player>` once eligible while the acquisition ritual is being designed. Completed advancements with a display count, including modded advancements; recipes and internal advancements without a display do not. Existing Sol owners retain their angel access and reserve. A newly awakened angel starts with a full reserve. The old Sun Stone item ID is retained for existing worlds.

Progression is completed advancements plus Prime blessings. Existing angels become **Archangels at 75** and **Seraphim at 150**, with four and six wings respectively. Their capacities are 200 and 350 Grace, and sunlight regeneration is 4 and 6 Grace per second (base Angel: 100 and 2). Rank promotions are permanent; increasing capacity does not refill the reserve. `/weaverangel status` shows your advancement, blessing, progression and rank totals.

Operators designate founding angels using `/weaverangel prime <player>`. This issues a token bound to that player; it does not bypass the 25-advancement entry requirement. Right-click another living angel with the token to grant **one progression point per Minecraft day**. Blessings are progression, not actual Minecraft advancement grants. The recipient’s new total cannot exceed the Prime’s current total, even within the same rank. Rejected blessings use no daily charge. The daily limit belongs to the Prime, so replacement or duplicate tokens do not bypass it. Progression, ownership and daily usage survive death and reconnecting. The day boundary uses the Overworld’s calendar, shared across dimensions.

Press **V** to open the invocation wheel. Click **Sanctus** (Divinity) three times to transform. The wings grow out from glowing points on the back over about 1.8 seconds: the inner wing extends first, then the wrists unfold and the outer feathers emerge. Orange embers and white-gold sparks follow their growth. Additional wing pairs emerge in a short stagger. This animation shares its server start time with other players and is dimmer outside sunlight. Repeat the sequence to revert. The other two sectors are **Pyra** (Inferno) and **Nemesis** (Retribution), reserved for the next ranks. Escape or V cancels an unfinished sequence. This is the only angel-specific keybind.

Transformation costs 5 Grace when the combined energy supply can cover it. Sunlight waives this fee below 5 Grace to let an exhausted angel recover. Zero-energy transformation works under direct sunlight and immediately starts recovering energy. An attempt outside sunlight with insufficient combined energy produces a subdued flash and leaves you human. Human form never recharges. Angel form regenerates 2 Grace/second under direct daylight, up to 100. Angel form consumes 0.25/second. Slow flight adds 0.5/second and fast flight adds 2/second. Exhausting both the personal reserve and usable carried storage ends the transformation. Death, logout, dimension changes, and game-mode changes also restore human form and remove angelic flight privileges.

Wings use articulated shoulders, elbows and wrists with delayed feather motion, an extended power stroke and folded recovery. Creative-flight hovering flaps 44% slower than the initial cadence (a 30% reduction followed by another 20%); ascending smoothly speeds it up and descending slows it down (up to 40% either way). Wing roots sit close to the back. Blocking routes the inner wings behind and outside the arms and sleeves before curling the outer feathers forward into an overlapping X across the chest; ground attacks wind up and sweep outside the body. During a melee strike, the wing twists so its upper leading edge makes contact first and its feathers trail behind, then untwists during recovery. Fast-flight spin effects use the same diving body frame as the player, with layered gold and orange flame ribbons spiraling along the head-to-feet axis.

In angel form, double-tap jump for slow creative-style flight. Tap the normal sprint key while airborne to switch between slow and fast flight. The body and wings ease between upright and flying poses over about 0.4 seconds in either direction. Fast flight steers like continuously boosted Elytra flight and ends on landing or entering water. Descending into a dive smoothly folds the wings back along the body; they spread again as you level out or climb.

All mouse abilities require an empty main hand. While grounded, left-click performs a randomized wing sweep with up to 4 blocks of reach for 2 Grace, and holding right-click crosses the wings as a directional shield. Blocking starts after five ticks, costs one Grace per prevented damage point, respects shield-bypassing attacks and piercing arrows, and axes disable it for five seconds. During slow flight, left-click launches a light feather for 3 Grace. These have bright white-gold vanes, a white shaft, a camera-facing glint and an extended fading trail for visibility from any viewing angle. Feather impacts show a small fire burst without a golden shockwave ring. Hold right-click to repel every nearby entity type within 4.5 blocks, including projectiles and minecarts, for one Grace per entity per second. During fast flight, **hold left-click** to channel a spinning charge with collision damage. The wings wrap around the player while charging. Activation costs **10 Grace**, followed by **10 Grace per second** in addition to normal fast-flight upkeep. Release left-click to stop. The channel also ends on insufficient energy, landing, entering water, switching flight mode, losing an empty main hand, or opening a screen. Carried Ember fuel can cover its energy costs. Each target can be hit once per uninterrupted charge. Hold right-click for a luminous mantle that reduces damage by 75% and spends one Grace per prevented damage point. An unaffordable action spends nothing and has no effect.

Operators can directly select `/weaverangel stage angel`, `/weaverangel stage archangel`, or `/weaverangel stage seraphim`; append a player name to target someone else. These testing overrides bypass the entry requirement and persist across reconnects. Use `/weaverangel stage auto [player]` to restore natural progression; a previously human tester below the entry requirement returns to human. Changing an existing angel’s stage keeps and clamps their Grace rather than refilling it. A human entering a testing stage starts full. `/weaverangel status` identifies an active override.

Operators can use `/weaverangel refill`, `/weaverangel energy <amount>`, and `/weaverangel remove` for testing. Angelic energy is stored separately from Thaumaturge Vis and Goety Soul Energy. The previous Sol-specific Warp purification and casting penalties have been retired. Embers Re-Ignited is supported as an optional external fuel source.

With **Embers Re-Ignited** installed, batteries and storage devices in the player inventory (including hotbar, armor and offhand) cover missing angel energy. Personal Grace pays first, then supported carried Ember stores in slot order. This applies to transformation, upkeep, wing attacks, feathers, repulsion, spin and damage absorbed by defenses. Default exchange: **100 Ember = 1 Grace**; change `emberPerGrace` in `weaverlib-angels-server.toml` to balance it. Payment can span multiple items, is all-or-nothing, and does not recharge the personal reserve. At zero Grace, usable carried energy can sustain angel form. Without enough combined energy the action fails; failed upkeep reverts the player to human. Embers is optional.

## Rune guide

The canvas key requires the same Warp unlock as rune casting. Operators can use `/weaverrunes unlockall` for themselves or `/weaverrunes unlockall <player>` for one online player. The grant is saved on that server and reveals every rune in the guide. Canvas access still follows the Warp unlock.

Draw a rune freehand and finish it with **Tab** or release the canvas key. A recognized rune is saved in your guide for this Minecraft profile on this installation. Open the guide with **G** and scroll through learned runes.

Right-click the canvas to center a copy of the selected rune at the pointer. Drag away from the click to enlarge it and release to place it. Use the middle mouse button if right-click is already used by the canvas. Placed runes use the same spell rules as drawings. **Ctrl+Z** removes a placed rune in one step.

## Casting

| Incantation | Effect on the caster | Cost |
| --- | --- | ---: |
| `incant lux` | 60 seconds of night vision | 5 Vis |
| `incant sana` | Heals 2 hearts | 8 Vis |

Type incantations in regular chat with no slash. If the first word is `incant` (case-insensitive), only players within 10 blocks in the same dimension receive the message, even if the spell fails. Casting is available to living, non-spectator players who have earned the Warp unlock. A locked player who types a valid incantation gets a private chat message, and the attempted incantation is suppressed.

Hold **K** to open the parchment canvas. Press **G** to show or hide the rune guide, then scroll to browse. The numbers mark the start of each stroke and the arrows show its direction. The right page shows the latest rune match, and a quiet page-turn sound plays when you press Tab to finish a rune, toggle the guide, or scroll to another guide.

- **Lux** uses a V drawn from upper left, down, then up to the right. Draw the crossbar left to right.
- **Sana** starts with a rising, kinked stem that turns right at the top. From the middle of the stem, draw a short branch toward the lower right. Finish with a detached clockwise ring, starting at the top.

Drag with the left mouse button, or the right mouse button if you bind the canvas to left click. Release the canvas key to cast. Your drawing and its rune settings will be there when you reopen the canvas. Use **Ctrl+Z** to undo one stroke, or **Esc** to clear the drawing and close without casting. The drawing is kept for the current game session. Ordered Lux and Sana matches have full potency. Recognizable Lux and Sana shapes drawn out of order can cast at half potency for the same Vis cost.

The server recognizes the submitted ink and runs the same spell logic for both inputs. Vis comes from Thaumaturge's aura in the caster's current chunk. Insufficient Vis, unknown inputs, full-health healing and redundant night vision leave the aura unspent. There is a shared one-second interval between attempts, and native spells also use Thaumaturge’s computed cooldown. Lux and Sana each accept up to three optional modifiers.

The geometry matcher is adapted from the MIT-licensed AonDor port kit. See LICENSE for its copyright notice.

## Temporary Warp and casting costs

A drawn or spoken spell generates **1 Temporary Warp** once its initial Vis payment succeeds. Drawn and spoken spells always cost **3× normal Vis**, regardless of how much Temporary Warp you have. When a WeaverLib Warp gain would take you above **100 Temporary Warp**, you gain **1 Normal Warp** and Temporary Warp resets to **1**, discarding any overflow.

Leap surcharges and Eternal upkeep for drawn and spoken spells also cost **3× Vis**. Automatic repeats generate no additional Warp. Inscribe generates **20 Temporary Warp** after successfully binding an item and costs **150 Vis**. Item activation uses its own battery pricing, and Erase generates no Warp.

## Unlocking rune and word casting

Reach **50 combined Normal and Permanent Warp** or become a **Goety lich** to unlock both casting methods. Existing liches qualify when they next join or tick on the server. An explicit operator lock takes priority. Temporary Warp and bonuses from equipment do not count. The unlock is saved to the player and survives death, reconnecting, and purification. On a natural unlock, a two-part message appears above the hotbar. A locked rune attempt reports your current progress without spending Vis.

The server config entry `requiredWarp` in `weaverlib-server.toml` controls the threshold. Set it to `0` to disable the Warp requirement. Changing the threshold affects players who have yet to earn the unlock.

Operators can type `/weaverwarp unlock` or `/weaverwarp lock` for themselves. Add an online player name to target someone else. Both arguments must be typed manually. A manual lock takes precedence over the threshold and lasts until an operator unlocks that player.

## Lux and Sana rune slots

Draw the main effect near the center. Press **Tab** between complete runes to mark the end of a glyph. Draw smaller modifiers around the effect. They are read clockwise from the top, with nearer runes first at the same angle. Main effects can be traced on the canvas, and modifier guides appear on the right page.

| Slot | Default | Modifier |
| --- | --- | --- |
| Form | Affect the selected target | **Pulse** affects up to eight eligible targets within three blocks of the selected target, visible to the caster. |
| Target | Caster | **Gaze** selects the living entity you're looking at, up to eight blocks away. |
| Power | Base effect | **Surge** multiplies duration or healing by 1.5 and rounds its Vis multiplier up per recipient. |

**Null** skips one slot and uses its default. Trailing slots can be omitted. For example, draw Lux in the center, Null above it, and Gaze to its right to give another player night vision. For stronger self-healing, draw Sana with Null, Null, Surge in clockwise order.

To cast Pulse, draw Lux or Sana first and press **Tab**. Draw a small heartbeat line above it with a short lead-in, a sharp rise and dip, and a rise halfway back into a short tail. Pulse goes in the Form slot. Place Gaze and Surge clockwise after it. A rune placed just to either side of twelve o’clock still starts the clockwise order.

Lux affects players. Sana can heal living targets. Pulse charges per affected target plus 2 Vis. A malformed spell, missing target, or insufficient Vis leaves the aura unspent. Typed incantations use the effect's defaults.

## Thaumaturge focus magic

Native effects, deliveries, modifiers, and flows have Latin-inspired chat words and drawn runes. For example, `incant cura auge fulgor` casts Heal through Amplify and Beam. Old English spell words still work. See the [component and argument reference](docs/thaumaturge-components.md) for the full catalog, spell construction, settings, and aspect choices.

## Inscribed items

Hold a single item in your main hand. Draw **Inscribe** first, press **Tab**, then draw the spell normally. Put the spell's main rune near the center and its other runes clockwise around it. Inscribe's position is ignored when ordering the stored spell. Once you have drawn **Inscribe**, open the guide with **G** and scroll to it to review its three strokes. You can also say `incant insculpe cura auge fulgor` to bind Heal + Amplify + Beam. The earlier word `inscribe` still works.

Inscribe requires the Warp unlock and costs **150 Vis** from the local aura. It binds the spell without casting it. An item can hold one inscription. Its tooltip shows the stored incantation or rune names, the battery charge, and the casting multiplier.

A new battery starts empty and holds **200 Vis**. An item with a native Vis battery keeps its existing charge and capacity. Charge it with Thaumaturge's **Recharge Pedestal** or **Vis Amulet**. Right-click the pedestal to place the item on it. Inscribed items do not refill directly from ambient Vis while held.

Right-click with the item in your main hand to cast from its battery. Sneak-right-click to use the item normally. Using the item does not require the Warp unlock. Thaumaturge's normal spell research requirements still apply. A cast costs **1.5×** its normal Vis cost, or **1.2×** if a loaded recipe for that item requires a thaumium or void-metal ingredient. Thaumaturge stores whole Vis charges, so costs round up. Inscribed Eternal pulses and Leap surcharges also draw from that item's battery.

Vanilla map, written-book, and banner copying recipes reject inscribed items so charging one item cannot create multiple batteries.

With Curios installed, press **J** (Cast Inscribed Curio) to use the first inscribed item in Curios' active equipment order. It uses the same spell, cooldown, and battery cost as a held inscription. If the first item's battery cannot pay, the key does not move on to another item. Cosmetic and inactive slots are skipped.

To clear a stored spell, hold the item in your main hand and draw **Erase** by itself, or say `incant dele`. Erasing requires the Warp unlock and costs no Vis. It removes the battery added by Inscribe and discards any charge in it. An item's existing battery and charge are kept. The item can then be inscribed again.

## Optional Goety casting

With Goety 3.2.x installed on the client and server, hold a Goety wand or staff with an Osseous Focus equipped. Say `incant goety osseous` or draw the **Osseous** rune by itself. The rune traces a diagonal bone in one continuous stroke. Goety casts use their own focus, casting time, cooldown, summon rules and resource costs. Goety and Thaumaturge components cannot share an incantation, rune cast or inscription.

Goety's stored Soul Energy pays first. Each missing unit takes one percentage point of personal soul. For example, an eight-energy cast with three stored spends those three and leaves a full-soul player at **95%**. The cast is blocked if the total cost would leave less than **1%**. Spending personal soul generates Temporary Warp at an increasing rate as the reserve drops, rising by one Warp per point spent for each 20 percentage points missing. The existing condensation threshold applies.

Personal soul scales maximum health, attack damage, movement speed, attack speed and world color saturation. It recovers by **one percentage point every 30 seconds of active play**. Goety Soul Energy gains repair the personal reserve first and deposit the remainder in native storage. Recovery restores your health cap and leaves current health at its existing value.
