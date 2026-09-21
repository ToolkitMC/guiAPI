# GUI API — Fabric 26.2

A Fabric mod that lets datapacks define and open chest GUIs via JSON files.  
No client mod required. No external dependencies beyond Fabric API.

---

## Installation

1. Drop `guiapi-1.0.7+26.2.jar` into your `mods/` folder.
2. Drop your datapack into `world/datapacks/`.
3. Run `/reload` or `/guiapi reload`.

Optionally install [Mod Menu](https://modrinth.com/mod/modmenu) to see loaded GUIs and visually edit them in-game!

---

## Commands

| Command | Description |
|---------|-------------|
| `/guiapi` | Show help (same as `/guiapi help`) |
| `/guiapi open <id>` | Open a GUI for yourself |
| `/guiapi open <id> <targets>` | Open a GUI for target players |
| `/guiapi list` | List all loaded GUI definitions |
| `/guiapi reload` | Reload all datapack resources (including GUIs) |
| `/guiapi var get <player> <key>` | Get a player's runtime variable |
| `/guiapi var set <player> <key> <value>` | Set a player's runtime variable |
| `/guiapi var clear <player>` | Clear all runtime variables for a player |
| `/guiapi help` | Show command and JSON field reference in-game |

**Permission level 2** (OP) required by default (configurable in Mod Menu).

---

## File Location

GUI definition files go in:

```
data/<namespace>/gui/<name>.json
```

The GUI ID used in commands is `<namespace>:<name>` — matching the file path under `gui/`.

---

## JSON Schema

### Top-level fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `title` | string | `"GUI"` | Inventory title. Supports `§` color codes and placeholders. |
| `rows` | int 1–6 | `3` | Number of rows (9 slots each). |
| `container_type` | string | `"barrel"` | `barrel` · `chest` · `player` · `ender_chest` · `chest_minecart`. `ender_chest`/`chest_minecart` force 3 rows, `player` forces 4. |
| `tick_rate` | int | `0` | Auto-refresh interval in ticks (e.g., `20` = 1s). Set `0` to disable. |
| `close_on_move` | boolean | `false` | If true, closes screen if player walks away (> 1.5 blocks). |
| `filler` | object | — | Background filler configuration (see below). |
| `on_open` | action[] | `[]` | Actions executed when the GUI is opened. |
| `on_close` | action[] | `[]` | Actions executed when the GUI is closed (any reason). |
| `open_condition` | condition | — | Player must meet this condition to open the GUI (see [Open gate](#open-gate)). |
| `on_deny` | action[] | `[]` | Actions executed instead of opening when `open_condition` is false. Empty = short action-bar notice. |
| `open_cost` | string | — | Entrance fee as `"itemId:amount"` (e.g. `"minecraft:gold_ingot:5"`). Charged once per open from outside the GUI; page navigation is free. If unaffordable, `on_deny` runs. |
| `macros` | object | `{}` | Named, reusable action lists — run them with `run_function` / `run_random_function`. |
| `progress_bars` | object[] | `[]` | Progress-bar widgets (see [Widgets](#widgets)). |
| `displays` | object[] | `[]` | Read-only info items (see [Widgets](#widgets)). |
| `buttons` | button[] | `[]` | List of button definitions. |

#### Filler fields

Any empty slot in the inventory is automatically populated with this background item.

```json
"filler": {
  "item": "minecraft:gray_stained_glass_pane",
  "name": " ",
  "glint": false,
  "hide_tooltip": true
}
```

### Button fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `slot` | int | `0` | Zero-based slot index (0–`rows*9-1`). |
| `page` | int | `0` | Which page this button appears on. |
| `item` | string | `"minecraft:stone"` | Item ID. |
| `name` | string | `""` | Display name. Supports color codes and placeholders. |
| `lore` | string[] | `[]` | Lore lines. Supports placeholders. |
| `glint` | boolean | `false` | Apply enchantment glint effect. |
| `amount` | string | `"1"` | Item stack count (supports placeholders like `{var:counter}`). |
| `hide_tooltip` | boolean | `false` | Hides item name and lore from hover tooltip. |
| `hide_additional_tooltip` | boolean | `false` | Hides attributes, enchantments, and clutter. |
| `custom_model_data` | int or object | — | Custom model data component (supports legacy int and 1.21.4+ composite object). |
| `item_model` | string | — | Custom item model component ID (1.21.2+). |
| `click_type` | string | `"any"` | Which click triggers actions: `any` · `left` · `right` · `shift` |
| `condition` | object | — | Visibility condition (see below). |
| `else_item` | object | — | Alternate appearance shown when `condition` is false (button stays visible but inert). Takes the same visual fields as a button. |
| `cooldown` | int | `0` | Per-player click cooldown in ticks. Survives closing/reopening the GUI. Also applies to toggle buttons. |
| `actions` | action[] | `[close]` | Actions executed in order on click. Supports `"delay": int` (ticks). |
| `action` | action | — | Shorthand for a single action; used only when `actions` is absent. |
| `toggle` | object | — | Toggle definition — replaces `item`/`actions` (see below). |

---

## Placeholders

Supported in `title`, button `name`, `lore`, `message` values, and `run_command` values.

| Placeholder | Resolves to |
|-------------|-------------|
| `{player}` | Player's display name |
| `{gui}` | GUI ID (`namespace:name`) |
| `{page}` | Current page index (0-based) |
| `{page1}` | Current page index (1-based) |
| `{pages}` | Total page count |
| `{score:objective}` | Player's score in the given scoreboard objective |
| `{var:key}` | Player's runtime variable `key` (empty string if unset) |
| `{xp}` | Player's experience level |
| `{input}` | Last text entered through an `anvil_input` action |
| `{health}` / `{max_health}` | Current / maximum health in half-hearts, rounded up |
| `{food}` | Hunger level (0–20) |
| `{online}` | Number of players currently online |
| `{pos_x}` `{pos_y}` `{pos_z}` | Player's block coordinates |

Text inserted by `{var:key}` and `{input}` is treated as plain text — it is never scanned for further placeholders.

---

## Action types

Any action can be delayed by adding `"delay": int` (in ticks) to its JSON block.

Any action can also carry a `"condition"` (same format as button conditions, including `all` / `any` / `not`). It is checked right when the action is about to run — after its delay — and a false condition skips just that action while the rest of the chain continues:

```json
{ "type": "message", "value": "§6VIP bonus applied!", "condition": { "type": "has_tag", "value": "vip" } }
```

| Type | `value` format | `run_with` | Description |
|------|--------------|------------|-------------|
| `run_command` | Command string | `player` · `console` | Run a command. Default: player. Supports placeholders. |
| `close` | — | — | Close the GUI. |
| `refresh` | — | — | Refresh the current GUI inventory dynamically (no closing/flicker). |
| `open_gui` | `namespace:name` | — | Close and open another GUI. |
| `message` | Text string | — | Send a chat message to the player. Supports placeholders. |
| `action_bar` | Text string | — | Send an action bar message directly to the player. |
| `sound` | `sound.id` or `sound.id:volume:pitch` | — | Play a sound. Volume/pitch default to `1.0`. Supports placeholders. |
| `set_score` | `objective:value` | — | Set player's scoreboard objective score directly (supports placeholders). |
| `add_score` | `objective:value` | — | Add score to player's scoreboard objective directly. |
| `sub_score` | `objective:value` | — | Subtract score from player's scoreboard objective directly. |
| `take_item` | `itemId:amount` | — | Deduct a specified amount of an item from the player's inventory. |
| `give_item` | `itemId:amount` | — | Give item(s); overflow that doesn't fit is dropped at the player's feet. |
| `add_xp` | `n` or `Ln` | — | Add `n` XP points, or `n` levels with the `L` prefix (e.g. `L2`). |
| `run_function` | macro name | — | Run a named action list from `macros`. |
| `run_random_function` | `name[*weight],…` | — | Run one macro chosen at random, e.g. `common*70,rare*25,legendary*5`. Weight defaults to 1. |
| `set_gamemode` | `survival` · `creative` · `adventure` · `spectator` | — | Change the player's game mode. Can be disabled in config (`allow_gamemode_change`). |
| `anvil_input` | `Title\|Default` | — | Open an anvil text prompt; the result is stored in `"var"` (default `input`) and `{input}`. |
| `none` | — | — | Stop the action chain here without doing anything. |
| `add_effect` | `effect_id:duration:amplifier:particles` | — | Give player status effect (duration in seconds, particles true/false). |
| `remove_effect` | `effect_id` | — | Remove a specific status effect from the player. |
| `clear_effects` | — | — | Clear all status effects from the player. |
| `set_var` | New value | — | Set a runtime variable. Requires `"var": "key"`. |
| `add_var` | Integer to add | — | Add an integer to a runtime variable. Requires `"var": "key"`. |
| `sub_var` | Integer to subtract | — | Subtract an integer from a runtime variable. Requires `"var": "key"`. |
| `reset_var` | — | — | Delete a single runtime variable. Requires `"var": "key"`. |
| `clear_vars` | — | — | Delete all runtime variables for this player. |
| `add_tag` | Tag name | — | Add a scoreboard tag to the player (no `run_with: console` needed). Supports placeholders. |
| `remove_tag` | Tag name | — | Remove a scoreboard tag from the player. |
| `broadcast` | Text string | — | Send a chat message to every online player. Supports placeholders. |
| `next_page` | — | — | Go to the next page. |
| `prev_page` | — | — | Go to the previous page. |
| `goto_page` | Page index (string) | — | Jump to a specific page. |

---

## Condition types

Conditions control button **visibility**. Hidden buttons cannot be clicked.

| Type | `value` format | Visible when |
|------|---------------|--------------|
| `has_tag` | Tag name | Player has the scoreboard tag |
| `not_tag` | Tag name | Player does **not** have the scoreboard tag |
| `score_gt` | `"objective:threshold"` | Player's score > threshold |
| `score_lt` | `"objective:threshold"` | Player's score < threshold |
| `score_eq` | `"objective:value"` | Player's score == value |
| `var_eq` | `"key:value"` | Runtime variable `key` equals `value` (string compare) |
| `var_gt` | `"key:value"` | Runtime variable `key` (int) > `value` |
| `var_lt` | `"key:value"` | Runtime variable `key` (int) < `value` |
| `var_set` | `key` | Runtime variable `key` is set (any value) |
| `has_item` | `"itemId:amount"` | Player has at least `amount` of `itemId` in inventory |
| `not_item` | `"itemId:amount"` | Player has less than `amount` of `itemId` in inventory |
| `level_gt` | `value` | Player's XP level > value |
| `level_lt` | `value` | Player's XP level < value |
| `health_gt` | `value` | Player's current health > value |
| `health_lt` | `value` | Player's current health < value |
| `food_gt` | `value` | Player's hunger level > value |
| `food_lt` | `value` | Player's hunger level < value |
| `permission` | `0`–`4` | Player's command permission level is at least that value |
| `gamemode` | `survival` · `creative` · `adventure` · `spectator` | Player is in that game mode |
| `in_dimension` | dimension id (`minecraft:the_nether`, or bare `the_nether`) | Player is in that dimension |
| `all` | `"conditions": [ … ]` | **Every** listed condition is true (empty list = true) |
| `any` | `"conditions": [ … ]` | **At least one** listed condition is true (empty list = false) |
| `not` | `"condition": { … }` | The nested condition is **not** true (no nested condition = false) |

Composite conditions can be nested (up to 8 levels) and work everywhere a condition does — buttons, displays, actions and `open_condition`:

```json
"condition": {
  "type": "all",
  "conditions": [
    { "type": "has_tag", "value": "vip" },
    { "type": "not", "condition": { "type": "has_tag", "value": "banned" } },
    { "type": "any", "conditions": [
        { "type": "level_gt", "value": "10" },
        { "type": "score_gt", "value": "coins:100" }
    ] }
  ]
}
```

### Open gate

`open_condition` restricts who can open a GUI — through `/guiapi open`, an `open_gui` action, page navigation or an item with the `guiapi:open_gui` component. When it is false the GUI does not open and `on_deny` runs instead:

```json
{
  "title": "VIP Lounge",
  "open_condition": { "type": "has_tag", "value": "vip" },
  "on_deny": [
    { "type": "message", "value": "§cVIP only!" },
    { "type": "sound", "value": "minecraft:entity.villager.no" }
  ],
  "buttons": [ ... ]
}
```

---

## Widgets

Non-button elements, defined as top-level arrays. They are read-only: clicks on their slots are ignored.

**`progress_bars`** — a horizontal run of slots that fills according to a runtime value, recalculated on every open/refresh (use `tick_rate` for live updates).

| Field | Default | Description |
|-------|---------|-------------|
| `start_slot` | `0` | First slot of the bar. |
| `length` | `9` | Number of slots. |
| `page` | `0` | Page the bar appears on. |
| `value_source` | `"var:progress"` | `"score:<objective>"` or `"var:<key>"`. |
| `max_value` | `100` | Value at which the bar is full. |
| `filled_item` / `empty_item` | lime / gray glass pane | Items for filled and empty slots. |
| `name` / `lore` | — | Optional text; supports placeholders. |

**`displays`** — one read-only info item. Fields: `slot`, `page`, `item`, `name`, `lore`, `glint`, `amount`, `condition`.

---

## Toggle buttons

A toggle button shows different item/name/lore/actions depending on a scoreboard tag on the player. Replace the `item` and `actions` fields with a `toggle` object.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `tag` | string | `""` | Scoreboard tag that stores the on/off state. |
| `item_on` / `item_off` | string | lime/gray dye | Item shown in each state. |
| `name_on` / `name_off` | string | `§aEnabled` / `§7Disabled` | Display name in each state. |
| `lore_on` / `lore_off` | string[] | `[]` | Lore in each state. |
| `glint_on` / `glint_off` | boolean | `false` | Glint in each state. |
| `actions_on` | action[] | `[tag @s remove <tag>]` | Actions executed on click while ON (turning OFF). |
| `actions_off` | action[] | `[tag @s add <tag>]` | Actions executed on click while OFF (turning ON). |

Toggle actions also fully support the multi-action engine (separated by `;` in the visual editor).

---

## Configuration

`config/guiapi.json` (editable through Mod Menu). Notable options:

| Key | Default | Description |
|-----|---------|-------------|
| `chat_prefix_enabled` | `false` | Prepend `chat_prefix` to `message` (in `CHAT` mode), `broadcast` and error chat messages. Action-bar text is never prefixed. |
| `chat_prefix` | `§8[§6GuiAPI§8] §f` | The prefix text. |
| `allow_console_run_with` | `true` | Allow `run_with: console`. |
| `allow_gamemode_change` | `true` | Allow `set_gamemode`. |
| `allow_status_effects` | `true` | Allow effect actions. |
| `permission_level` | `2` | Permission level for `/guiapi`. |

---

## Client-Side features (Optional)

Installing this mod on the client-side unlocks powerful, highly-polished user experience features:

### 1. In-Game GUI Editor
* Open the **Mod Menu** config screen to see a list of loaded GUIs.
* Click any GUI to open the **GUI Editor Screen**!
* Visually edit GUI `title`, `rows`, `tick_rate`, `close_on_move`, and background `filler`.
* Manage buttons list, add new buttons, and edit slot, item, amount, glint, lore (using `;` split), multiple actions, and toggle properties.
* Click **Apply & Back** to open the **Gui Save Loading Screen** which finds the target datapack folder on the server, safely writes the JSON to disk, and reloads the API definitions. No edits are ever lost on rejoin!

### 2. Native Keybindings
Integrates with Minecraft's official controls menu (**Options > Controls > Key Binds > GUI API**):
* **Accept Rules (Open GUI):** Opens the default welcome GUI (Defaults to **`G`**).


---

## Examples

Please refer to the updated `example-datapack` directory in the repository sources for highly-polished, fully-annotated examples demonstrating auto-refreshing clocks, direct scoreboard trading, status effect controllers, and custom visual models!

---

## Building

```bash
chmod +x gradlew
./gradlew build
# Output: build/libs/guiapi-1.0.7+26.2.jar
```

Requires **Java 25**.

---

## License

MIT — see [LICENSE](LICENSE).
