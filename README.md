<p align="center">
  <img src="assets/logo.webp" width="160" alt="ForgeItems logo">
</p>

<h1 align="center">ForgeItems</h1>

<p align="center"><i>YAML-defined custom items — 25 activators, 25 action verbs, mana, item levels, mob drops, sets, recipes, browser GUI, soulbound, custom durability.</i></p>

<p align="center">
  <img src="https://img.shields.io/badge/version-3.0.0-ff7b2e?style=for-the-badge" alt="version 3.0.0">
  <img src="https://img.shields.io/badge/Paper-26.3-2f9e6e?style=for-the-badge" alt="Paper 26.3">
  <img src="https://img.shields.io/badge/Java-25-f89820?style=for-the-badge" alt="Java 25">
  <img src="https://img.shields.io/badge/25_activators-2563eb?style=for-the-badge" alt="25 activators">
  <img src="https://img.shields.io/badge/25_action_verbs-b565d8?style=for-the-badge" alt="25 action verbs">
  <img src="https://img.shields.io/badge/mana_+_levels-0ea5e9?style=for-the-badge" alt="mana and levels">
  <img src="https://img.shields.io/badge/sets_+_recipes-ef4444?style=for-the-badge" alt="sets and recipes">
  <img src="https://img.shields.io/badge/dependencies-zero-6b7280?style=for-the-badge" alt="zero dependencies">
</p>

<p align="center"><sub>Not affiliated with <a href="https://minecraftforge.net">MinecraftForge</a> — "Forge" is just a name.</sub></p>

---

YAML-defined custom items for Paper. Define items in plain YAML files — display name, lore, enchantments, attribute modifiers, durability rules — and attach **activators** that fire on gameplay events and run **actions** (lightning, explosions, potions, messages, console commands, sounds, and more). An original implementation with zero runtime dependencies beyond the Paper API.

## Features

- **YAML item definitions** in `plugins/ForgeItems/items/*.yml` — no code needed to add items
- **25 activators** (triggers): right/left/shift-right click, hitting/killing entities, block break/place, taking damage, dying, equip/unequip, consuming, item drop/pickup, projectile launch/hit, fishing catches, sneak toggle/start, sprint start, glide start, join/respawn/world-change, and a periodic `LOOP` trigger
- **25 built-in action verbs** plus per-activator console commands, a direct message, and potion effects
- **Mana system**: per-player mana pool with regen and action-bar display; activators cost mana via `mana-cost`
- **Item levels/XP**: items earn XP from triggers, kills, and block breaks; level-ups rewrite the lore line and fire messages, actions, and commands
- **Mob drop tables** (`drops.yml`): custom items drop from mobs by chance, with biome/world filters
- **Item sets** (`sets.yml`): wear N pieces for potion-effect tiers plus message/action/command rewards
- **Crafting recipes**: shaped/shapeless recipes that produce custom items, with vanilla *or* custom-item ingredients
- **Item browser GUI** (`/fitems menu`): paginated, rarity-sorted browser; click to take a copy (with `forgeitems.give`)
- **Rarity tiers**: COMMON → MYTHIC, shown as a colored lore line and used for GUI sorting
- **Rich per-activator conditions**: sneak state, permission, health range, fire chance, biomes, day/night, weather, light level, target entity type
- **Per-activator and global cooldowns** with action-bar "ready in Xs" feedback, plus the vanilla material `cooldown` action
- **Custom durability**: usage limits stored in the item's PersistentDataContainer, with break and uses-left warnings
- **Soulbound**: `keep-on-death` items are retained through death
- **Usage gating**: per-item `use-permission` and `restricted-worlds`
- **Modern Paper data components** for name, lore, enchantments, attributes, unbreakable, custom model data, item model, glint override, max stack size, use cooldown, consumable, and food properties
- **MiniMessage** names, lore, and messages throughout
- Item identity and usage counters stored in PDC, so items survive restarts and inventory moves
- Action verbs are validated at load time — typos surface in the console, not mid-fight

## Requirements

- Paper 26.3+ (`api-version: '26.3'`)
- Java 25

## Installation

Drop `ForgeItems-3.0.0.jar` into your server's `plugins/` folder and restart. Five example items (`thunder_hammer`, `frost_bow`, `guardian_chestplate`, `ember_blade`, `ember_chestplate`) are generated into `plugins/ForgeItems/items/` on first run, along with an example `sets.yml` (the Ember Set) and `drops.yml` (ember items from zombies/blazes).

## Commands

| Command | Description | Permission |
|---|---|---|
| `/forgeitems reload` (`/fitems reload`) | Reload `config.yml`, items, sets, and recipes | `forgeitems.admin` |
| `/forgeitems give <id> [player] [amount]` | Give a custom item (amount defaults to 1, clamped 1–64) | `forgeitems.give` |
| `/forgeitems list` | List all loaded item ids and names | `forgeitems.list` |
| `/forgeitems menu` (`/fitems menu`) | Open the paginated item browser GUI | `forgeitems.menu` |

## Permissions

| Permission | Description | Default |
|---|---|---|
| `forgeitems.admin` | Reload items | op |
| `forgeitems.give` | Give custom items to players (also click-to-take in the browser) | op |
| `forgeitems.list` | List available custom items | true |
| `forgeitems.menu` | Open the item browser GUI | true |

## Configuration

### config.yml

```yaml
settings:
  loop-interval-ticks: 20   # how often the LOOP activator scans players (20 = 1s)
  message-prefix: "<gray>[<gold>ForgeItems<gray>] "
  cooldown-notify: true     # action-bar "ready in Xs" when an activator is on cooldown
  mana:
    enabled: true
    max-mana: 100.0
    regen-per-second: 5.0
    display: actionbar      # actionbar | none (shown while mana is not full)
    display-format: "<aqua>Mana <white>%mana%<gray>/%max%"

messages:
  reloaded: "<green>ForgeItems reloaded: <white><count> <green>item(s) loaded."
  # ... no-permission, unknown-item, item-given, item-received,
  #     item-list-header, item-list-entry, item-broken, usage-left,
  #     cooldown-left (<seconds>), no-mana (<cost>)
```

Message placeholders use MiniMessage tags: `<count>`, `<id>`, `<name>`, `<amount>`, `<player>`, `<uses>`.

### Item YAML schema

Each file in `items/` defines one item. All keys except `id` and `material` are optional.

```yaml
id: thunder_hammer
material: MACE                      # Bukkit material name
name: "<gradient:#ffcf3f:#ff7b2e>Thunder Hammer</gradient>"   # MiniMessage
lore:
  - "<gray>Right-click: call down lightning"
  - ""
enchantments:
  minecraft:knockback: 2            # namespaced key (or bare name) -> level
attributes:
  minecraft:attack_damage:
    amount: 12
    operation: ADD_NUMBER            # ADD_NUMBER | ADD_SCALAR | MULTIPLY_SCALAR_1
    slot: ANY                        # ANY | MAINHAND | OFFHAND | HAND | ARMOR |
                                     # HEAD | CHEST | LEGS | FEET | BODY
unbreakable: true
custom-model-data: [1.0, 2.0]        # or a single number
item-model: "minecraft:storm_hammer" # item model key
glint-override: true                 # force enchantment glint on/off
max-stack-size: 1
keep-on-death: true                  # soulbound: retained through death
usage-limit: 250                     # custom durability; 0 = unlimited
cooldown-seconds: 0                  # global cooldown across all activators
restricted-worlds: [world_nether]    # item activators are inert here
use-permission: "myserver.vip"       # players without it cannot trigger the item
rarity: EPIC                        # COMMON | UNCOMMON | RARE | EPIC | LEGENDARY | MYTHIC
set: ember                          # item-set id from sets.yml (bonuses while worn)
levels:                             # optional XP/leveling for this item
  max-level: 10
  xp-base: 40                       # XP for level 1 -> 2; each level costs xp-base * xp-growth^(level-1)
  xp-growth: 1.6
  xp-per-trigger: 3                 # XP per activator firing
  xp-per-kill: 20                   # bonus XP on KILL_ENTITY
  xp-per-block-break: 5             # bonus XP on BLOCK_BREAK
  level-up-message: "<red>Your <name> <red>reached level <white><level><red>!"
  level-up-actions:                 # %level% and %name% available here
    - "particle minecraft:flame 40 0.6 0.05"
  level-up-commands: []
recipe:                             # optional crafting recipe producing this item
  type: shaped                      # shaped | shapeless
  shape:                            # shaped: 1-3 rows
    - " B "
    - " B "
    - " S "
  ingredients:                      # shaped: char -> material or custom item id
    B: "minecraft:blaze_rod"        # shapeless: list of materials/item ids instead
    S: "minecraft:stick"
consumable: false
consume-seconds: 1.6                 # eat/drink time when consumable
food:
  nutrition: 4
  saturation: 0.6
activators:
  zap:
    trigger: RIGHT_CLICK             # see trigger list below
    cooldown-seconds: 4
    chance: 1.0                      # 0.0–1.0, default 1.0
    mana-cost: 10                    # mana per firing; blocked with a no-mana message when short
    cancel-event: true               # cancel the underlying Bukkit event
    consume-use: true                # consume one durability use (default true, false for LOOP)
    conditions:
      sneaking: false                # require (or forbid) sneaking
      permission: "myserver.zap"     # activator-level permission
      min-health: 1.0                # player health range, half-hearts
      max-health: 19.0
      biomes: [plains, minecraft:forest]  # allowed biomes (bare or namespaced)
      time: night                    # day | night | any
      weather: thunder               # clear | rain | thunder | any
      min-light: 0                   # block light level 0-15
      max-light: 7
      target-types: [ZOMBIE]         # allowed target entity types
    actions:                         # built-in action verbs, in order
      - "lightning"
      - "message <yellow>Zap!"
    commands:                        # console commands, in order
      - "give %player% diamond 1"
    message: "<yellow>Zap!"           # direct MiniMessage message to the user
    effects:                         # potion effects applied to the user
      - "minecraft:speed 200 1"      # or map form: {type:, duration:, amplifier:}
```

Placeholders available in `actions`, `commands`, and `message`: `%player%`, `%target%`, `%x%`, `%y%`, `%z%`, `%world%`, `%uses%` (durability left), `%rarity%`, `%set%`.

### sets.yml

Item sets grant bonuses while enough pieces are worn (armor slots are scanned every 2 seconds and on armor changes). Each tier applies its potion effects while active and fires its message/actions/commands once per threshold crossing (with a 30-second re-fire guard).

```yaml
sets:
  ember:
    name: "<red>Ember Set"
    bonuses:
      - pieces: 2
        effects:
          - "minecraft:fire_resistance 200 0"
        message: "<red>Ember Set <gray>(2 pieces): <white>Fire Resistance!"
        actions:
          - "particle minecraft:flame 40 0.6 0.05"
        commands: []
      - pieces: 4
        effects:
          - "minecraft:fire_resistance 200 0"
          - "minecraft:strength 200 1"
        message: "<gold>Ember Set complete: Strength II!"
```

### Triggers

| Trigger | Fires when |
|---|---|
| `RIGHT_CLICK` | Player right-clicks with the item (air or block) |
| `LEFT_CLICK` | Player left-clicks with the item |
| `SHIFT_RIGHT_CLICK` | Player shift-right-clicks with the item |
| `HIT_ENTITY` | Player hits an entity holding/wearing the item |
| `KILL_ENTITY` | Player kills an entity holding/wearing the item |
| `BLOCK_BREAK` | Player breaks a block holding the item |
| `BLOCK_PLACE` | Player places a block holding the item |
| `TAKE_DAMAGE` | Player takes damage holding/wearing the item |
| `PLAYER_DEATH` | Player dies holding/wearing the item |
| `EQUIP` | Item is equipped (armor slot change) |
| `UNEQUIP` | Item is unequipped (armor slot change) |
| `CONSUME` | Player consumes the item (food/potion) |
| `ITEM_DROP` | Player drops the item |
| `ITEM_PICKUP` | Player picks up the item |
| `PROJECTILE_LAUNCH` | Player launches a projectile holding the item |
| `PROJECTILE_HIT` | Projectile launched with the item hits something |
| `FISH_CAUGHT` | Player catches a fish holding the item |
| `SNEAK_TOGGLE` | Player toggles sneak holding/wearing the item |
| `SNEAK_START` | Player starts sneaking holding/wearing the item |
| `SPRINT_START` | Player starts sprinting holding/wearing the item |
| `GLIDE_START` | Player starts gliding (elytra) holding/wearing the item |
| `PLAYER_JOIN` | Player joins holding/wearing the item |
| `PLAYER_RESPAWN` | Player respawns holding/wearing the item |
| `WORLD_CHANGE` | Player changes world holding/wearing the item |
| `LOOP` | Periodic scan while held/worn (see `loop-interval-ticks`) |

### Action verbs

Actions run in order. MiniMessage is parsed in `message`/`broadcast`/`title`/`subtitle`/`actionbar`.

| Verb | Syntax | Effect |
|---|---|---|
| `damage` | `damage <amount>` | Damage the target (or the player if none) |
| `heal` | `heal <amount>` | Heal the player (default 2) |
| `launch` | `launch [power]` | Launch the player in their look direction (default 1.5) |
| `lightning` | `lightning` | Strike lightning at the effect location |
| `explode` | `explode <power>` | Create an explosion at the effect location (default 2) |
| `potion` | `potion <effect> <ticks> <amplifier>` | Apply a potion effect to the target (or player) |
| `message` | `message <MiniMessage>` | Send a message to the player |
| `broadcast` | `broadcast <MiniMessage>` | Broadcast to the whole server |
| `command` | `command <console command>` | Dispatch a command as console |
| `cooldown` | `cooldown <seconds>` | Apply the vanilla material cooldown |
| `sound` | `sound <key> [volume] [pitch]` | Play a sound to the player |
| `particle` | `particle <key> [count] [spread] [speed]` | Spawn particles at the effect location |
| `title` | `title <MiniMessage>` | Show a title to the player |
| `subtitle` | `subtitle <MiniMessage>` | Show a subtitle to the player |
| `actionbar` | `actionbar <MiniMessage>` | Send an action-bar message |
| `feed` | `feed [amount]` | Restore hunger (default 20) |
| `extinguish` | `extinguish` | Put out the player |
| `sudo` | `sudo <command>` | Run a command as the player |
| `spawnmob` | `spawnmob <type> [count]` | Spawn mobs at the effect location (max 10) |
| `mana` | `mana <amount>` | Restore (positive) or drain (negative) the player's mana |
| `giveitem` | `giveitem <id> [amount]` | Give a ForgeItems custom item to the player |
| `ignite` | `ignite [ticks]` | Set the target (or player) on fire (default 100 ticks) |
| `freeze` | `freeze [ticks]` | Freeze the target (or player, default 100 ticks) |
| `clearpotion` | `clearpotion [effect]` | Clear one effect, or all when omitted |
| `fly` | `fly <on\|off\|toggle>` | Toggle flight for the player |

The effect location is the trigger location, else the target's location, else the player's.

### drops.yml

Mob drop tables: custom items dropped by mobs, with optional biome/world filters.

```yaml
drops:
  - mob: ZOMBIE
    item: ember_blade
    chance: 0.03          # per kill, 0.0-1.0
    min-amount: 1
    max-amount: 1
    biomes: [plains]      # optional
    worlds: [world]       # optional
```

## Building from source

```bash
bash build.sh
```

The build uses `javac` directly (the Gradle daemon cannot run in this sandbox):

- JDK 25 at `~/workspace/.toolchains/jdk-25.0.4.1+1`
- Dependencies from `~/workspace/.toolchains/paper-deps/` (Paper API 26.3.build.35-alpha)
- Compiled with `-Werror -Xlint:deprecation`, so warnings fail the build

Output: `ForgeItems-3.0.0.jar`.

## Code quality

- No deprecated Paper/Bukkit APIs anywhere in the codebase or docs.
- Nullness is explicit: every package is `@NotNullByDefault` (JetBrains annotations, already on the compile classpath), with `@Nullable` on the specific parameters and returns that can legitimately be null (registry lookups, PDC reads, optional YAML keys).

---

<p align="center"><i>Part of the <a href="https://github.com/ForgePluginsMC">Forge</a> plugin suite — original implementations, zero dependencies.</i></p>
