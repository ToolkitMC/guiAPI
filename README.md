# GUI API — Fabric 26.2

A Fabric mod that lets datapacks define and open chest GUIs via JSON files.  
No client mod required. No macros. No external dependencies beyond Fabric API.

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
| `tick_rate` | int | `0` | Auto-refresh interval in ticks (e.g., `20` = 1s). Set `0` to disable. |
| `close_on_move` | boolean | `false` | If true, closes screen if player walks away (> 1.5 blocks). |
| `filler` | object | — | Background filler configuration (see below). |
| `on_open` | action[] | `[]` | Actions executed when the GUI is opened. |
| `on_close` | action[] | `[]` | Actions executed when the GUI is closed (any reason). |
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
| `actions` | action[] | `[close]` | Actions executed in order on click. Supports `"delay": int` (ticks). |
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

---

## Action types

Any action can be delayed by adding `"delay": int` (in ticks) to its JSON block.

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
| `add_effect` | `effect_id:duration:amplifier:particles` | — | Give player status effect (duration in seconds, particles true/false). |
| `remove_effect` | `effect_id` | — | Remove a specific status effect from the player. |
| `clear_effects` | — | — | Clear all status effects from the player. |
| `set_var` | New value | — | Set a runtime variable. Requires `"var": "key"`. |
| `add_var` | Integer to add | — | Add an integer to a runtime variable. Requires `"var": "key"`. |
| `sub_var` | Integer to subtract | — | Subtract an integer from a runtime variable. Requires `"var": "key"`. |
| `reset_var` | — | — | Delete a single runtime variable. Requires `"var": "key"`. |
| `clear_vars` | — | — | Delete all runtime variables for this player. |
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

## Client-Side features (Optional)

Installing this mod on the client-side unlocks powerful, highly-polished user experience features:

### 1. In-Game GUI Editor
* Open the **Mod Menu** config screen to see a list of loaded GUIs.
* Click any GUI to open the **GUI Editor Screen**!
* Visually edit GUI `title`, `rows`, `tick_rate`, `close_on_move`, and background `filler`.
* Manage buttons list, add new buttons, and edit slot, item, amount, glint, lore (using `;` split), multiple actions, and toggle properties.
* Click **Apply & Back** to open the **Gui Save Loading Screen** which finds the target datapack folder on the server, safely writes the JSON to disk, and reloads the API definitions. No edits are ever lost on rejoin!

### 2. Native Keybindings
Integrates natively with Minecraft's official controls menu (**Options > Controls > Key Binds > GUI API**):
* **Accept Rules (Open GUI):** Opens the default welcome GUI (Defaults to **`G`**).
* **Toggle Search in GUI:** Activates the interactive slot search (Defaults to **`L`**).

### 3. Interactive Slot Search (`L`)
* Press **`L`** inside any GUI (or any chest/barrel container enventories!) to toggle the Search bar.
* Type alphanumeric characters to search. Matching items are highlighted with a gorgeous HSB glowing neon color-cycling gradient, while non-matching slots are dimmed.
* Minecraft closing/dropping hotkeys (like `E` and `Q`) are safely blocked while search is focused to ensure a pristine typing experience. Press `ESC` or `L` again to close.

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
