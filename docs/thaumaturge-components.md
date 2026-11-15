# Thaumaturge components in WeaverLib

This reference covers the player-facing spell parts in Thaumaturge 1.1.0. WeaverLib runs the original spell behaviors, sounds, particles, carriers and continuations. Research, aspect discoveries and native compatibility checks apply. Each cast uses the native Vis quote and the caster's local aura. The casting budget matches the Greater Focus. The canvas and chat accept up to eight drawn or spoken parts in one spell.

## Using the components

- **Words.** In regular chat, type `incant` followed by the effect and any additional parts. The incantation uses the usual ten-block chat radius. Use underscores in multiword names, such as `silk_touch`. A hyphen also works.
- **Runes.** Hold the canvas key, draw the effect near the center, then put the remaining parts clockwise from twelve o’clock. Press **Tab** between complete glyphs. **G** cycles forward through the guides, and **Shift+G** cycles backward. Release the canvas key to cast.
- **Arguments.** For words, append `[key=value,key=value]` directly to the part name, with no spaces inside the brackets. For a drawn part, press **R** after completing its strokes. Use **↑/↓** to pick a setting and **←/→** to change it. Press **R** again to return to drawing. The editor uses the settings provided by the installed Thaumaturge registries.
- **Defaults.** Omitted arguments use Thaumaturge's defaults. In words, `null` can be supplied for a named field, such as `beam[range=null]`. A standalone **Null** rune or word can also be included and is skipped. Heal and Imbue default to Self. Other effects default to Touch. An explicit delivery takes precedence.
- **Order.** Each delivery, modifier or ordinary flow wraps the preceding effect or subspell. Put a modifier after the part whose behavior it should influence. Branch combines the two preceding subspells into a fork. A delivery added after Branch feeds both branches. Relay, Delay and Sieve operate before the subspell they wrap. WeaverLib launches instant casts on chat submit or canvas release.
- **Equipment.** Plan uses the area controls on a held Thaumaturge caster. Exchange uses the remembered block on a held caster and follows Thaumaturge's block exchange rules. Other spells use the environment's Vis without requiring a focus item.

| Example | Result |
| --- | --- |
| `incant heal` | Cast Heal on yourself. |
| `incant heal[power=3] beam[range=16]` | Deliver stronger Heal along a 16-block beam. |
| `incant imbue[aspect=ignis]` | Select Ignis for Imbue and use the default Self delivery. |
| `incant fire amplify projectile` | Cast a projectile that applies Amplify to Fire on impact. |
| `incant fire frost branch bolt` | Use one Bolt delivery to feed Fire and Frost. |
| `incant heal sieve[keep=allies] aura` | Use Aura, then keep allies before applying Heal. |

## Drawing key

Each focus glyph takes two strokes. Draw the family frame first. Then draw the eight teeth from left to right, returning to the middle line between teeth. A tooth points above or below the middle line according to the sequence in the table. The in-game guide shows the exact start point and spacing.

| Family | Frame stroke |
| --- | --- |
| Effect | Bottom left → top left → top right → bottom right. |
| Harvest | Top left → top right → bottom right → bottom left → top left. |
| Delivery | Top left → bottom left → bottom right → top right. |
| Modifier | Bottom left → top left → bottom right → top right. |
| Flow | Left → top → right → bottom → left. |

| Effect | Teeth | Delivery | Teeth | Modifier | Teeth | Flow | Teeth |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `fire` | ↑ ↑ ↑ ↑ ↑ ↑ ↑ ↑ | `touch` | ↑ ↑ ↑ ↑ ↑ ↑ ↑ ↑ | `amplify` | ↑ ↑ ↑ ↑ ↑ ↑ ↑ ↑ | `branch` | ↑ ↑ ↑ ↑ ↑ ↑ ↑ ↑ |
| `frost` | ↓ ↑ ↑ ↑ ↓ ↓ ↓ ↑ | `bolt` | ↓ ↑ ↑ ↑ ↓ ↓ ↓ ↑ | `extend` | ↓ ↑ ↑ ↑ ↓ ↓ ↓ ↑ | `relay` | ↓ ↑ ↑ ↑ ↓ ↓ ↓ ↑ |
| `air` | ↑ ↓ ↑ ↑ ↓ ↓ ↑ ↓ | `beam` | ↑ ↓ ↑ ↑ ↓ ↓ ↑ ↓ | `widen` | ↑ ↓ ↑ ↑ ↓ ↓ ↑ ↓ | `delay` | ↑ ↓ ↑ ↑ ↓ ↓ ↑ ↓ |
| `earth` | ↓ ↓ ↑ ↑ ↑ ↑ ↓ ↓ | `projectile` | ↓ ↓ ↑ ↑ ↑ ↑ ↓ ↓ | `quicken` | ↓ ↓ ↑ ↑ ↑ ↑ ↓ ↓ | `sieve` | ↓ ↓ ↑ ↑ ↑ ↑ ↓ ↓ |
| `flux` | ↑ ↑ ↓ ↑ ↓ ↑ ↓ ↓ | `lob` | ↑ ↑ ↓ ↑ ↓ ↑ ↓ ↓ | `efficient` | ↑ ↑ ↓ ↑ ↓ ↑ ↓ ↓ |  |  |
| `curse` | ↓ ↑ ↓ ↑ ↑ ↓ ↑ ↓ | `spray` | ↓ ↑ ↓ ↑ ↑ ↓ ↑ ↓ | `pierce` | ↓ ↑ ↓ ↑ ↑ ↓ ↑ ↓ |  |  |
| `heal` | ↑ ↓ ↓ ↑ ↑ ↓ ↓ ↑ | `chain` | ↑ ↓ ↓ ↑ ↑ ↓ ↓ ↑ | `homing` | ↑ ↓ ↓ ↑ ↑ ↓ ↓ ↑ |  |  |
| `burst` | ↓ ↓ ↓ ↑ ↓ ↑ ↑ ↑ | `aura` | ↓ ↓ ↓ ↑ ↓ ↑ ↑ ↑ | `bounce` | ↓ ↓ ↓ ↑ ↓ ↑ ↑ ↑ |  |  |
| `imbue` | ↑ ↑ ↑ ↓ ↑ ↓ ↓ ↓ | `self` | ↑ ↑ ↑ ↓ ↑ ↓ ↓ ↓ | `silk_touch` | ↑ ↑ ↑ ↓ ↑ ↓ ↓ ↓ |  |  |
| `break` | ↓ ↑ ↑ ↓ ↓ ↑ ↑ ↓ | `cloud` | ↓ ↑ ↑ ↓ ↓ ↑ ↑ ↓ | `fortune` | ↓ ↑ ↑ ↓ ↓ ↑ ↑ ↓ |  |  |
| `exchange` | ↑ ↓ ↑ ↓ ↓ ↑ ↓ ↑ | `mine` | ↑ ↓ ↑ ↓ ↓ ↑ ↓ ↑ | `scatter` | ↑ ↓ ↑ ↓ ↓ ↑ ↓ ↑ |  |  |
| `rift` | ↓ ↓ ↑ ↓ ↑ ↓ ↑ ↑ | `wall` | ↓ ↓ ↑ ↓ ↑ ↓ ↑ ↑ | `fan` | ↓ ↓ ↑ ↓ ↑ ↓ ↑ ↑ |  |  |
| `ward` | ↑ ↑ ↓ ↓ ↓ ↓ ↑ ↑ | `spellbat` | ↑ ↑ ↓ ↓ ↓ ↓ ↑ ↑ | `echo` | ↑ ↑ ↓ ↓ ↓ ↓ ↑ ↑ |  |  |
| `primal` | ↓ ↑ ↓ ↓ ↑ ↑ ↓ ↑ | `sprite` | ↓ ↑ ↓ ↓ ↑ ↑ ↓ ↑ |  |  |  |  |
| `hellbat` | ↑ ↓ ↓ ↓ ↑ ↑ ↑ ↓ | `plan` | ↑ ↓ ↓ ↓ ↑ ↑ ↑ ↓ |  |  |  |  |
| `blink` | ↓ ↓ ↓ ↓ ↓ ↓ ↓ ↓ |  |  |  |  |  |  |
| `harvest` | ↑ ↑ ↑ ↑ ↑ ↑ ↑ ↑ |  |  |  |  |  |  |

## Native arguments

The default is **bold**. A numeric range is inclusive and accepts only the listed step. Named values can be entered as the option's name or its native zero-based value where the minimum is zero. Arguments and aspect choices may differ when a datapack overrides the relevant part.

### Effects

| Word | Behavior | Optional arguments |
| --- | --- | --- |
| `fire` | Deals fire damage and can ignite entities or blocks. | `power` 1–5, step 1, default **1**<br>`duration` 0–5, step 1, default **1** |
| `frost` | Slows creatures and can freeze water. | `power` 1–5, step 1, default **1**<br>`duration` 2–10, step 1, default **2** |
| `air` | Hits with wind and pushes targets away. | `power` 1–5, step 1, default **1** |
| `earth` | Strikes with stone and can damage fragile blocks. | `power` 1–5, step 1, default **1** |
| `flux` | Deals corrupting damage and can add Flux to the aura. | `power` 1–5, step 1, default **1** |
| `curse` | Applies debilitating effects to creatures and can affect the terrain. | `power` 1–5, step 1, default **1**<br>`duration` 1–10, step 1, default **3** |
| `heal` | Restores health to living targets and harms undead. | `power` 1–5, step 1, default **1** |
| `burst` | Applies a selected strike affinity over an area. | `power` 1–5, step 1, default **2**<br>`radius` 1–4, step 1, default **2**<br>`aspect` default **ignis** |
| `imbue` | Applies the beneficial effect associated with the selected affinity. | `potency` 1–3, step 1, default **1**<br>`duration` 1–10, step 1, default **3**<br>`aspect` default **victus** |
| `break` | Uses a block-breaking effect with adjustable strength. | `power` 1–5, step 1, default **1** |
| `exchange` | Replaces affected blocks with the held caster’s remembered block. | — |
| `rift` | Opens a temporary passage through blocks. | `depth` 8–32, step 8, default **8**<br>`duration` 2–10, step 1, default **4** |
| `ward` | Protects a block or removes a ward the caster owns. | — |
| `primal` | Applies an unstable primal effect with adjustable power. | `power` 1–5, step 1, default **1** |
| `hellbat` | Summons fire bats to pursue affected targets. | `bats` 1–3, step 1, default **1** |
| `blink` | Moves the caster to the spell’s destination. | — |
| `harvest` | Harvests and replants mature crops in the selected area. | `radius` 0–3, step 1, default **1** |

### Deliveries

| Word | Behavior | Optional arguments |
| --- | --- | --- |
| `touch` | Delivers to a block or creature within reach. | — |
| `bolt` | Delivers instantly along a short ray. | — |
| `beam` | Traces a beam along the caster’s aim. | `range` 8–24, step 4, default **12** |
| `projectile` | Creates a moving orb that delivers on impact. | `speed` 1–5, step 1, default **2** |
| `lob` | Launches an arcing carrier that splashes its destination. | — |
| `spray` | Delivers across a cone in the direction of aim. | `range` 3–6, step 1, default **4** |
| `chain` | Delivers to successive nearby enemies at decreasing strength. | `jumps` 1–4, step 1, default **2** |
| `aura` | Delivers to nearby targets other than the caster. | `radius` 2–6, step 1, default **3** |
| `self` | Uses the caster as the target. | — |
| `cloud` | Creates a persistent area that delivers the spell over time. | `radius` 1–3, step 1, default **1**<br>`duration` 5–30, step 5, default **10** |
| `mine` | Sets a proximity trap with a selectable team filter. | `target` **enemy**, ally |
| `wall` | Creates a temporary line that delivers to targets crossing it. | `width` 3–7, step 2, default **3**<br>`duration` 5–20, step 5, default **10** |
| `spellbat` | Spawns a bat that carries the spell to creatures. | `target` **enemy**, ally |
| `sprite` | Spawns an orbiting carrier that strikes nearby enemies. | `duration` 10–30, step 10, default **10** |
| `plan` | Selects an area of blocks using the held caster’s size and shape settings. | `method` **full**, surface |

### Modifiers

| Word | Behavior | Optional arguments |
| --- | --- | --- |
| `amplify` | Raises the power passed to child nodes. | `level` 1–3, step 1, default **1** |
| `extend` | Raises the duration passed to child nodes. | `level` 1–3, step 1, default **1** |
| `widen` | Expands area effects and can add nearby recipients to single-target effects. | `level` 1–3, step 1, default **1** |
| `quicken` | Raises carrier speed and lowers the cooldown. | — |
| `efficient` | Trades some power for a lower Vis cost. | — |
| `pierce` | Lets applicable carriers or rays affect more creatures along their path. | `level` 1–3, step 1, default **1** |
| `homing` | Allows supported carriers to seek enemy targets. | — |
| `bounce` | Allows projectiles to rebound from blocks. | `level` 1–3, step 1, default **1** |
| `silk_touch` | Applies Silk Touch behavior to downstream block interactions. | — |
| `fortune` | Increases eligible downstream block drops. | `level` 1–3, step 1, default **1** |
| `scatter` | Creates several paths across a randomized cone. | `forks` 2–8, step 1, default **3**<br>`cone` 10–90, step 10, default **30** |
| `fan` | Creates several paths spaced across an arc. | `count` 2–7, step 1, default **3**<br>`arc` 30–120, step 15, default **60** |
| `echo` | Repeats the downstream spell at reduced power. | `repeats` 1–3, step 1, default **1** |

### Flows

| Word | Behavior | Optional arguments |
| --- | --- | --- |
| `branch` | Passes the incoming targets to both child subspells. | — |
| `relay` | Selects a new direction from the current target. | `direction` **forward**, reflect, up, down, caster, nearest |
| `delay` | Schedules its child subspell after the specified tick delay. | `ticks` 5–60, step 5, default **20** |
| `sieve` | Passes only targets that match the selected category. | `keep` **entities**, blocks, enemies, allies, undead, living |

### Selectable aspects

Burst uses strike affinities. Imbue uses boon affinities. Thaumaturge only accepts a compatible aspect that the caster has discovered.

| Component | Base 1.1.0 options |
| --- | --- |
| `burst` | `aer`, `alienis`, `aqua`, `gelum`, `herba`, `ignis`, `lux`, `mortuus`, `motus`, `ordo`, `perditio`, `potentia`, `tenebrae`, `terra`, `vacuos`, `victus`, `vinculum`, `vitium`, `volatus` |
| `imbue` | `aer`, `aqua`, `herba`, `ignis`, `lux`, `motus`, `ordo`, `perditio`, `potentia`, `tenebrae`, `terra`, `victus`, `volatus` |

Thaumaturge supplies the hidden Origin and the boss-only parts internally.
