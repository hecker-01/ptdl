# PTDL

PTDL is an open-source Android client inspired by Discord that focuses on native performance, low resource usage, and a customizable user experience.

**What it is:**

- An Android app built with platform-native components and modern Android patterns.
- Designed for speed and responsiveness on a wide range of devices.
- Provides configurable themes, layout options, and modular feature toggles so users can tailor their experience.

**Why PTDL exists:**

- To offer an alternative Android client that prioritizes efficiency and privacy while still supporting rich chat, voice, and community features.

**Key Features:**

- Native UI built with Android best-practices for smooth rendering.
- Theme support (light, dark, and custom accent colors, like material you).
- Modular features allowing users to enable/disable components.
- Low memory and CPU footprint compared to heavy cross-platform clients.

**Screenshots:**

- Add screenshots to `docs/screenshots/` and reference them here.

**Quick Start (developer):**

- Requirements: `JDK 11+`, `Android SDK` (with required platforms), and `Android Studio` or a compatible IDE.
- Recommended: use the bundled Gradle wrapper to avoid installing a global Gradle.

Windows PowerShell build commands (from project root):

```bash
cd `C:\Users\jesse\StudioProjects\PTDL`
.\gradlew assembleDebug
.\gradlew installDebug
```

Or to run from Android Studio: open the project and run the `app` module on a device or emulator.

**Project Structure (high level):**

- `build.gradle.kts`, `settings.gradle.kts`: project build configuration.
- `app/`: Android application module with sources, resources and module build file (`app/build.gradle.kts`).
- `app/src/main/`: application source files and resources.
- `app/build/`: build outputs and intermediate build artifacts (generated during builds).

APK output (when built): `app/build/outputs/apk/`.

**Development notes:**

- Keep the Gradle wrapper (`gradlew`/`gradlew.bat`) in sync with CI or contributors' environment.
- Use the module `app` for implementing UI, network, and feature modules.
- Consider adding `docs/` for design notes, architecture diagrams, and contributor guidelines.

**Contributing:**

- Fork the repository, create a branch, implement features or fixes, and open a pull request.
- Include a short description of changes and any steps to reproduce or test.

**Testing & Linting:**

- Unit and instrumentation tests live under `app/src/test/` and `app/src/androidTest/` if present.
- Run tests with the Gradle wrapper: `.\gradlew test` or `.\gradlew connectedAndroidTest` (requires device/emulator).

**License & Contact:**

- If a `LICENSE` file exists, this project follows that license; otherwise contact the maintainers for licensing information.
- For questions or contributions, open an issue or a pull request on the repository.
