# Contributing to GUI API

Thanks for your interest. This document covers how to contribute code, report bugs, and suggest features.

---

## Before You Start

- Check [open issues](https://github.com/ToolkitMC/guiAPI/issues) to avoid duplicate work.
- For security vulnerabilities, see [SECURITY.md](SECURITY.md) — do not open a public issue.
- For large changes, open an issue first to discuss the approach.

---

## Development Setup

**Requirements:** Java 21, Git.

```bash
git clone https://github.com/ToolkitMC/guiAPI.git
cd guiAPI
./gradlew build
```

The built jar is at `build/libs/guiapi-1.0.3.jar`.

To run in a local Minecraft instance, use Fabric's `runServer` task or drop the jar into a test server's `mods/` folder.

---

## Code Style

- **Java 21** — records, switch expressions, and sealed types are welcome.
- **Indentation:** 4 spaces, no tabs.
- **Line length:** soft limit of 120 characters.
- **Naming:** camelCase for methods/fields, PascalCase for classes, UPPER_SNAKE for constants.
- **Comments:** English only. Explain *why*, not *what*.
- All server-side code lives under `src/main`. Client-side code (Mod Menu integration) is `modCompileOnly` — guard with `@Environment(EnvType.CLIENT)` where necessary.
- Do not add runtime dependencies beyond Fabric API without discussion.

---

## Pull Requests

1. Fork the repo and create a branch: `feat/<short-description>` or `fix/<short-description>`.
2. Keep commits focused. One logical change per commit.
3. Write a clear PR description: what changed, why, and how to test it.
4. Update `README.md` and the example datapack if your change affects the JSON schema or commands.
5. Target the `main` branch.

PRs that break backwards compatibility in the JSON schema require a version bump discussion.

---

## Reporting Bugs

Open a [GitHub issue](https://github.com/ToolkitMC/guiAPI/issues/new) with:

- GUI API version and Minecraft version
- Fabric Loader and Fabric API version
- The GUI JSON that triggers the bug (trimmed to the minimal reproducer)
- Expected behaviour vs actual behaviour
- Server log / crash report if applicable

---

## Suggesting Features

Open a [GitHub issue](https://github.com/ToolkitMC/guiAPI/issues/new) tagged `enhancement`. Include a JSON example of how the feature would look from a datapack author's perspective.

---

## License

By contributing, you agree that your contributions are licensed under the project's [MIT License](LICENSE).
