<p align="center">
  <img src="assets/logo.webp" width="160" alt="ForgeItems logo">
</p>

<h1 align="center">ForgeItems</h1>

<p align="center"><i>YAML-defined custom items — 14 activators, 11 action verbs, cooldowns, soulbound, custom durability.</i></p>

<p align="center">
  <img src="https://img.shields.io/badge/version-1.0.0-ff7b2e?style=for-the-badge" alt="version 1.0.0">
  <img src="https://img.shields.io/badge/Paper-26.3-2f9e6e?style=for-the-badge" alt="Paper 26.3">
  <img src="https://img.shields.io/badge/Java-25-f89820?style=for-the-badge" alt="Java 25">
  <img src="https://img.shields.io/badge/14_activators-2563eb?style=for-the-badge" alt="14 activators">
  <img src="https://img.shields.io/badge/11_action_verbs-b565d8?style=for-the-badge" alt="11 action verbs">
  <img src="https://img.shields.io/badge/dependencies-zero-6b7280?style=for-the-badge" alt="zero dependencies">
</p>

<p align="center"><sub>Not affiliated with <a href="https://minecraftforge.net">MinecraftForge</a> — "Forge" is just a name.</sub></p>

---

YAML-defined custom items for Paper. Define items in plain YAML files — display name, lore, enchantments, attribute modifiers, durability rules — and attach **activators** that fire on gameplay events and run **actions** (lightning, explosions, potions, messages, console commands, sounds, and more). An original implementation with zero runtime dependencies beyond the Paper API.

## Features

- **YAML item definitions** in `plugins/ForgeItems/items/*.yml` — no code needed to add items
- **14 activators** (triggers): right-click, left-click, shift-right-click, hitting/killing entities, block break, taking damage, equip/unequip (armor), consuming, projectile launch/hit, sneak toggle, and a periodic `LOOP` trigger
- **11 built-in action verbs** plus per-activator console commands, a direct message, and potion effects
- **Per-activator conditions**: required sneak state, permission, player health range, fire chance
- **Per-activator and global cooldowns**, plus the vanilla material `cooldown` action
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

Drop `ForgeItems-1.0.0.jar` into your server's `plugins/` folder and restart. Three example items (`thunder_hammer`, `frost_bow`, `guardian_chestplate`) are generated into `plugins/ForgeItems/items/` on first run.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/forgeitems reload` (`/fitems reload`) | Reload `config.yml` and all item definitions | `forgeitems.admin` |
| `/forgeitems give <id> [player] [amount]` | Give a custom item (amount defaults to 1, clamped 1–64) | `forgeitems.give` |
| `/forgeitems list` | List all loaded item ids and names | `forgeitems.list` |

## Permissions

| Permission | Description | Default |
|---|---|---|
| `forgeitems.admin` | Reload items | op |
| `forgeitems.give` | Give custom items to players | op |
| `forgeitems.list` | List available custom items | true |

## Configuration

### config.yml

```yaml
settings:
  loop-interval-ticks: 20   # how often the LOOP activator scans players (20 = 1s)
  message-prefix: "<gray>[<gold>ForgeItems<gray>] "

messages:
  reloaded: "<green>ForgeItems reloaded: <white><count> <green>item(s) loaded."
  # ... no-permission, unknown-item, item-given, item-received,
  #     item-list-header, item-list-entry, item-broken, usage-left
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
    cancel-event: true               # cancel the underlying Bukkit event
    consume-use: true                # consume one durability use (default true, false for LOOP)
    conditions:
      sneaking: false                # require (or forbid) sneaking
      permission: "myserver.zap"     # activator-level permission
      min-health: 1.0                # player health range, half-hearts
      max-health: 19.0
    actions:                         # built-in action verbs, in order
      - "lightning"
      - "message <yellow>Zap!"
    commands:                        # console commands, in order
      - "give %player% diamond 1"
    message: "<yellow>Zap!"           # direct MiniMessage message to the user
    effects:                         # potion effects applied to the user
      - "minecraft:speed 200 1"      # or map form: {type:, duration:, amplifier:}
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
| `TAKE_DAMAGE` | Player takes damage holding/wearing the item |
| `EQUIP` | Item is equipped (armor slot change) |
| `UNEQUIP` | Item is unequipped (armor slot change) |
| `CONSUME` | Player consumes the item (food/potion) |
| `PROJECTILE_LAUNCH` | Player launches a projectile holding the item |
| `PROJECTILE_HIT` | Projectile launched with the item hits something |
| `SNEAK_TOGGLE` | Player toggles sneak holding/wearing the item |
| `LOOP` | Periodic scan while held/worn (see `loop-interval-ticks`) |

### Action verbs

Actions run in order; arguments support `%player%`, `%target%`, `%x%`, `%y%`, `%z%`, `%world%` placeholders. MiniMessage is parsed in `message`/`broadcast`.

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

The effect location is the trigger location, else the target's location, else the player's.

## Building from source

```bash
bash build.sh
```

The build uses `javac` directly (the Gradle daemon cannot run in this sandbox):

- JDK 25 at `~/workspace/.toolchains/jdk-25.0.4.1+1`
- Dependencies from `~/workspace/.toolchains/paper-deps/` (Paper API 26.3.build.35-alpha)
- Compiled with `-Werror -Xlint:deprecation`, so warnings fail the build

Output: `ForgeItems-1.0.0.jar`.

## Code quality

- No deprecated Paper/Bukkit APIs anywhere in the codebase or docs.
- Nullness is explicit: every package is `@NotNullByDefault` (JetBrains annotations, already on the compile classpath), with `@Nullable` on the specific parameters and returns that can legitimately be null (registry lookups, PDC reads, optional YAML keys).

---

<p align="center"><i>Part of the <a href="https://github.com/ForgePluginsMC">Forge</a> plugin suite — original implementations, zero dependencies.</i></p>
