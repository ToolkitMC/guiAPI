# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.0.x (Minecraft 1.21.1) | ✅ Active |

Only the latest release receives security fixes.

---

## Reporting a Vulnerability

**Do not open a public issue for security vulnerabilities.**

Report privately via GitHub's [Security Advisories](https://github.com/ToolkitMC/guiAPI/security/advisories/new) feature.

Include:
- A clear description of the vulnerability
- Steps to reproduce
- Potential impact (e.g. command injection, privilege escalation, crash)
- Affected version(s)

You can expect an initial response within **72 hours** and a patch within **14 days** for confirmed critical issues.

---

## Threat Model

GUI API runs entirely server-side. The attack surfaces to keep in mind:

### Datapack authors (trusted by design)
GUI JSON files are loaded from the server's datapack directory. Anyone who can place files in `world/datapacks/` already has filesystem access to the server — GUI API does not expand that trust boundary.

### `run_with: "console"` actions
Buttons with `"run_with": "console"` execute commands with server-level (console) permission. This is intentional and only reachable by players who trigger a button defined by the datapack author. If you expose such buttons without a `condition` guard, any player who opens the GUI can trigger the command.

**Recommendation:** Always gate elevated-command buttons with a `condition` such as `has_tag` to restrict access.

### Command injection
The `run_command` action passes the `value` string directly to the Minecraft command dispatcher. The value is set at datapack authoring time — it is not interpolated with player input at runtime. There is no runtime injection surface from the player side.

### Player inventory protection
All slot interactions inside a GUI inventory are consumed server-side. `quickMove` (shift-drag), `THROW` (Q), `CLONE` (middle-click), and all other `SlotActionType` variants other than `PICKUP` and `QUICK_MOVE` are silently discarded, preventing item duplication or extraction.

### State map (`OPEN_GUIS`)
Per-player open state is stored in a server-side `HashMap<UUID, OpenState>`. It is cleared on screen close. There is no persistent state across sessions, no serialisation to disk, and no cross-player data sharing.

---

## Known Non-Issues

- **Client-side spoofing of slot clicks** — the server validates all slot interactions before acting. Clients cannot trigger actions on slots that have no button defined.
- **Permission escalation via `/guiapi open`** — the command requires permission level 2. Players cannot open GUIs for other players without OP.
