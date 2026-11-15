# Thaumaturge components in WeaverLib

This reference covers the player-facing spell parts in Thaumaturge 1.1.0. WeaverLib runs the original spell behaviors, sounds, particles, carriers and continuations. Research, aspect discoveries and native compatibility checks apply. Each cast uses the native Vis quote and the caster's local aura. The casting budget matches the Greater Focus. The canvas and chat accept up to eight drawn or spoken parts in one spell.

## Using the components

- **Words.** In regular chat, type `incant` followed by the effect and any additional parts. The incantation uses the usual ten-block chat radius. Use the incantation column for the Latin-inspired word. The English component names are also accepted.
- **Runes.** Hold the canvas key, draw the effect near the center, then put the remaining parts clockwise from twelve o’clock. Press **Tab** between complete glyphs. **G** cycles forward through the guides, and **Shift+G** cycles backward. Release the canvas key to cast.
- **Arguments.** For words, append `[key=value,key=value]` directly to the part name, with no spaces inside the brackets. For a drawn part, press **R** after completing its strokes. Use **↑/↓** to pick a setting and **←/→** to change it. Press **R** again to return to drawing. The editor uses the settings provided by the installed Thaumaturge registries.
- **Defaults.** Omitted arguments use Thaumaturge's defaults. In words, `nihil` can be supplied for a named field, such as `fulgor[range=nihil]`. A standalone **Null** rune or `nihil` word can also be included and is skipped. Heal and Imbue default to Self. Other effects default to Touch. An explicit delivery takes precedence.
- **Order.** Each delivery, modifier or ordinary flow wraps the preceding effect or subspell. Put a modifier after the part whose behavior it should influence. Branch combines the two preceding subspells into a fork. A delivery added after Branch feeds both branches. Relay, Delay and Sieve operate before the subspell they wrap. WeaverLib launches instant casts on chat submit or canvas release.
- **Equipment.** Plan uses the area controls on a held Thaumaturge caster. Exchange uses the remembered block on a held caster and follows Thaumaturge's block exchange rules. Other spells use the environment's Vis without requiring a focus item.

| Example | Result |
| --- | --- |
| `incant cura` | Cast Heal on yourself. |
| `incant cura[power=3] fulgor[range=16]` | Deliver stronger Heal along a 16-block beam. |
| `incant infunde[aspect=ignis]` | Select Ignis for Imbue and use the default Self delivery. |
| `incant ignis auge orbis` | Cast a projectile that applies Amplify to Fire on impact. |
| `incant ignis gelu furca fulmen` | Use one Bolt delivery to feed Fire and Frost. |
| `incant cura cerne[keep=allies] ambitus` | Use Aura, then keep allies before applying Heal. |

## Drawing the glyphs

Choose a glyph with **G** or **Shift+G** to see its exact shape and numbered starts. Draw each stroke continuously from its numbered start, then lift before the next stroke. The recognizer checks stroke count, order, direction, and relative placement first. For a close outline it can also accept a different starting point, direction, or one extra or missing pen lift. If two outlines are too close, the canvas asks for a clearer drawing. It accepts changes in overall size and canvas position. Start by practicing broad, simple shapes such as Heal. Curves are part of the templates for glyphs such as Fire, Air, and Cloud.

## Native arguments

The default is **bold**. A numeric range is inclusive and accepts only the listed step. Named values can be entered as the option's name or its native zero-based value where the minimum is zero. Arguments and aspect choices may differ when a datapack overrides the relevant part.

### Effects

| Component | Incantation | Behavior | Optional arguments |
| --- | --- | --- | --- |
| `fire` | `ignis` | Deals fire damage and can ignite entities or blocks. | `power` 1–5, step 1, default **1**<br>`duration` 0–5, step 1, default **1** |
| `frost` | `gelu` | Slows creatures and can freeze water. | `power` 1–5, step 1, default **1**<br>`duration` 2–10, step 1, default **2** |
| `air` | `ventus` | Hits with wind and pushes targets away. | `power` 1–5, step 1, default **1** |
| `earth` | `terra` | Strikes with stone and can damage fragile blocks. | `power` 1–5, step 1, default **1** |
| `flux` | `vitium` | Deals corrupting damage and can add Flux to the aura. | `power` 1–5, step 1, default **1** |
| `curse` | `maledictum` | Applies debilitating effects to creatures and can affect the terrain. | `power` 1–5, step 1, default **1**<br>`duration` 1–10, step 1, default **3** |
| `heal` | `cura` | Restores health to living targets and harms undead. | `power` 1–5, step 1, default **1** |
| `burst` | `erumpe` | Applies a selected strike affinity over an area. | `power` 1–5, step 1, default **2**<br>`radius` 1–4, step 1, default **2**<br>`aspect` default **ignis** |
| `imbue` | `infunde` | Applies the beneficial effect associated with the selected affinity. | `potency` 1–3, step 1, default **1**<br>`duration` 1–10, step 1, default **3**<br>`aspect` default **victus** |
| `break` | `frange` | Uses a block-breaking effect with adjustable strength. | `power` 1–5, step 1, default **1** |
| `exchange` | `permuta` | Replaces affected blocks with the held caster’s remembered block. | — |
| `rift` | `scinde` | Opens a temporary passage through blocks. | `depth` 8–32, step 8, default **8**<br>`duration` 2–10, step 1, default **4** |
| `ward` | `protege` | Protects a block or removes a ward the caster owns. | — |
| `primal` | `primordium` | Applies an unstable primal effect with adjustable power. | `power` 1–5, step 1, default **1** |
| `hellbat` | `vespertilio` | Summons fire bats to pursue affected targets. | `bats` 1–3, step 1, default **1** |
| `blink` | `transi` | Moves the caster to the spell’s destination. | — |
| `harvest` | `mete` | Harvests and replants mature crops in the selected area. | `radius` 0–3, step 1, default **1** |

### Deliveries

| Component | Incantation | Behavior | Optional arguments |
| --- | --- | --- | --- |
| `touch` | `tange` | Delivers to a block or creature within reach. | — |
| `bolt` | `fulmen` | Delivers instantly along a short ray. | — |
| `beam` | `fulgor` | Traces a beam along the caster’s aim. | `range` 8–24, step 4, default **12** |
| `projectile` | `orbis` | Creates a moving orb that delivers on impact. | `speed` 1–5, step 1, default **2** |
| `lob` | `iace` | Launches an arcing carrier that splashes its destination. | — |
| `spray` | `asperge` | Delivers across a cone in the direction of aim. | `range` 3–6, step 1, default **4** |
| `chain` | `catena` | Delivers to successive nearby enemies at decreasing strength. | `jumps` 1–4, step 1, default **2** |
| `aura` | `ambitus` | Delivers to nearby targets other than the caster. | `radius` 2–6, step 1, default **3** |
| `self` | `ego` | Uses the caster as the target. | — |
| `cloud` | `nubes` | Creates a persistent area that delivers the spell over time. | `radius` 1–3, step 1, default **1**<br>`duration` 5–30, step 5, default **10** |
| `mine` | `insidiae` | Sets a proximity trap with a selectable team filter. | `target` **enemy**, ally |
| `wall` | `murus` | Creates a temporary line that delivers to targets crossing it. | `width` 3–7, step 2, default **3**<br>`duration` 5–20, step 5, default **10** |
| `spellbat` | `noctilio` | Spawns a bat that carries the spell to creatures. | `target` **enemy**, ally |
| `sprite` | `scintilla` | Spawns an orbiting carrier that strikes nearby enemies. | `duration` 10–30, step 10, default **10** |
| `plan` | `forma` | Selects an area of blocks using the held caster’s size and shape settings. | `method` **full**, surface |

### Modifiers

| Component | Incantation | Behavior | Optional arguments |
| --- | --- | --- | --- |
| `amplify` | `auge` | Raises the power passed to child nodes. | `level` 1–3, step 1, default **1** |
| `extend` | `extende` | Raises the duration passed to child nodes. | `level` 1–3, step 1, default **1** |
| `widen` | `dilata` | Expands area effects and can add nearby recipients to single-target effects. | `level` 1–3, step 1, default **1** |
| `quicken` | `accelera` | Raises carrier speed and lowers the cooldown. | — |
| `efficient` | `parce` | Trades some power for a lower Vis cost. | — |
| `pierce` | `penetra` | Lets applicable carriers or rays affect more creatures along their path. | `level` 1–3, step 1, default **1** |
| `homing` | `sequere` | Allows supported carriers to seek enemy targets. | — |
| `bounce` | `resilie` | Allows projectiles to rebound from blocks. | `level` 1–3, step 1, default **1** |
| `silk_touch` | `sericum` | Applies Silk Touch behavior to downstream block interactions. | — |
| `fortune` | `fortuna` | Increases eligible downstream block drops. | `level` 1–3, step 1, default **1** |
| `scatter` | `disperge` | Creates several paths across a randomized cone. | `forks` 2–8, step 1, default **3**<br>`cone` 10–90, step 10, default **30** |
| `fan` | `arcus` | Creates several paths spaced across an arc. | `count` 2–7, step 1, default **3**<br>`arc` 30–120, step 15, default **60** |
| `echo` | `resonare` | Repeats the downstream spell at reduced power. | `repeats` 1–3, step 1, default **1** |

### Flows

| Component | Incantation | Behavior | Optional arguments |
| --- | --- | --- | --- |
| `branch` | `furca` | Passes the incoming targets to both child subspells. | — |
| `relay` | `reflecte` | Selects a new direction from the current target. | `direction` **forward**, reflect, up, down, caster, nearest |
| `delay` | `mora` | Schedules its child subspell after the specified tick delay. | `ticks` 5–60, step 5, default **20** |
| `sieve` | `cerne` | Passes only targets that match the selected category. | `keep` **entities**, blocks, enemies, allies, undead, living |

### Selectable aspects

Burst uses strike affinities. Imbue uses boon affinities. Thaumaturge only accepts a compatible aspect that the caster has discovered.

| Component | Base 1.1.0 options |
| --- | --- |
| `burst` | `aer`, `alienis`, `aqua`, `gelum`, `herba`, `ignis`, `lux`, `mortuus`, `motus`, `ordo`, `perditio`, `potentia`, `tenebrae`, `terra`, `vacuos`, `victus`, `vinculum`, `vitium`, `volatus` |
| `imbue` | `aer`, `aqua`, `herba`, `ignis`, `lux`, `motus`, `ordo`, `perditio`, `potentia`, `tenebrae`, `terra`, `victus`, `volatus` |

Thaumaturge supplies the hidden Origin and the boss-only parts internally.

## WeaverLib components

These components are available to drawn runes and spoken incantations. They use Thaumaturge's spell tree, validation, and effects system.

| Component | Incantation | Behavior | Optional argument |
| --- | --- | --- | --- |
| `leap` | `salta` | Pushes a living target along its current look direction, including up or down. Defaults to Self. | `strength` 1–4, default **1**. |
| `eternal` | `aeternum` | Runs its wrapped subspell immediately, then repeats it at the same power on the original targets. Each pulse charges the wrapped subspell's Vis cost from the caster's current chunk, with a minimum of 1 Vis. | `interval` 10–100 ticks, step 10, default **20** (one second). |
| `halt` | `siste` | Stops an active or pending Eternal loop. Alone, Halt targets you, costs no Vis, and works during cooldown. Add a delivery to use it on another player at the regular cast cost. | None. |

Each player can sustain one Eternal loop. A new Eternal cast replaces the previous one. Damage to the caster, death, logout, changing dimension, a casting lock, lost targets, or insufficient Vis can stop the loop. Halt prevents future Eternal pulses. Projectiles and effects that already fired follow their normal behavior.

- `incant salta` pushes you along your own look direction.
- `incant cura aeternum[interval=40] ego` sustains Heal on yourself every two seconds.
- `incant siste` ends your loop.
- `incant siste fulmen` attempts to end another player's loop by delivering Halt with Bolt.
