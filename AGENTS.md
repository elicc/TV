# Repository Guidelines

## Project Structure & Module Organization

- `app/src/main/` contains shared Android Java code, resources (`res/`), and bundled web assets (`assets/`).
- `app/src/leanback/` provides the TV interface; `app/src/mobile/` provides the phone interface. Keep device-specific changes in the appropriate flavor.
- `catvod/`, `chaquo/`, and `quickjs/` support Spider integrations and Python/JavaScript execution. Active modules are listed in `settings.gradle`.
- `buildSrc/` contains custom Gradle packaging logic; `gradle/libs.versions.toml` centralizes dependency versions.
- `website/app/` contains Next.js documentation pages and components; `website/public/` holds static assets. Deployment is configured in `.github/workflows/pages.yml`.

## Build, Test, and Development Commands

Android requires JDK 21, Android SDK, Python 3.10, matching player AARs in `app/libs/`, and local signing settings. Follow `README.md` for setup; a fresh clone lacks required `lib-*.aar` files.

Run from the repository root:

- `./gradlew :app:assembleLeanbackDebug` — build the TV debug APK.
- `./gradlew :app:assembleMobileDebug` — build the phone debug APK.
- `./gradlew :app:lintLeanbackDebug :app:lintMobileDebug` — run Android static analysis.

For documentation, use Node.js 22.18+ (22.x) or 24+, then run from `website/`:

- `npm ci && npm run dev` — install locked dependencies and start development.
- `npm run lint && npm run typecheck && npm run build` — validate and generate static output in `out/`.
- `npm start` — preview the built site over HTTP.

## TV Device Testing (TCL MT5879, `192.168.1.22:5555`)

- Install only with `scripts/deploy-leanback.sh -s 192.168.1.22:5555`. It pushes the APK and `.idsig` and installs incrementally from the local file on the device. Never use any adb-served install (`adb install`, `adb install -r`, `--incremental`, `pm install` of a streamed APK): they crash `system_server` in the package installer (SIGBUS in `libandroidfw`), and a plain `adb install -r` once left the TV completely offline (no ping) until it was power-cycled. Check `dumpsys dropbox --print system_server_native_crash` if the TV freezes.
- Someone may be watching the TV. Key events you did not send show up in app traces; do not reinstall or restart the app then.
- The main logcat buffer is 1 MB and fills within seconds with `MS_OMX_H264DEC Error Code` lines from the HAL. Run `adb -s 192.168.1.22:5555 logcat -G 16M` before tracing (it resets on reboot). Use temporary `Log.w` traces with a unique tag and remove them before committing.
- `adb shell input keyevent` takes about 600 ms to fire, so time interactions from an app-side trace (for example in `LiveActivity.dispatchKeyEvent`), not from the host.
- `SettingActivity` is not exported and `uiautomator dump` only returns the video node during playback; navigate with D-pad keys and `screencap`. Video areas capture black (hardware overlay).
- Live sources: the config `http://192.168.1.100:4567/sub/10` with its live `iptv` (multicast relayed by iptv-fcc on uos) is the user's choice. Set it under 设置 → 直播 (history icon for the config, house icon for the live inside it), and leave the TV on the user's config and channel after testing.
- Spider jars run in-process with full access to the app's data. The 饭太硬 jar bumps the `time` of its own live row in the `tv` database a few seconds after start, through its own SQLite connection, so the newest `Config` row is not the user's choice. `Config.vod()/live()/wall()` therefore resolve the active config from the `config_<type>` preference that `Config.update()` writes, and fall back to the newest row only when that URL has no row.

## Coding Style & Naming Conventions

Match surrounding code: four-space indentation for Java/Gradle; two spaces, double quotes, and semicolons for TypeScript. Use PascalCase for Java classes and React components, camelCase for methods/variables, and snake_case for Android resources. Website linting uses Next.js ESLint rules. Reuse existing utilities; avoid unrelated formatting changes or new dependencies.

## Android Icon System

- Use official [Lucide](https://lucide.dev/) SVGs for all new or replaced general-purpose Android UI icons. Choose the closest semantic icon instead of reusing an unrelated legacy asset merely to avoid adding a resource.
- Treat `tools/lucide/VERSION` as the source-version contract. Vendor the unmodified SVG from that exact Lucide version and commit into `tools/lucide/svg/`; keep `tools/lucide/LICENSE` and the pinned provenance intact. Android builds and the running app must never download icon assets.
- Generate Android `VectorDrawable` resources through `tools/lucide/generate_android_vectors.py`. Add the source/output/size mapping to `ICONS`, run the generator, and commit both the vendored SVG and generated XML. Never hand-edit generated `pathData`, trace an icon manually, or introduce icon fonts, raster copies, Material icon substitutions, or another icon dependency for a symbol Lucide provides.
- Preserve the Lucide visual language: `24 × 24` viewport, `2`-unit stroke, rounded line caps and joins, transparent fill unless the official SVG explicitly fills a node. Set the drawable size explicitly for its component; use the established `20dp` compact/card/navigation size and `24dp` settings or primary-action size unless the surrounding component defines another size.
- Keep state colors outside the geometry. Generated vectors use a neutral tintable color; apply theme attributes or Android color selectors for focused, selected, disabled, and default states. Do not create duplicate colored copies of the same icon.
- Current generated assets target `app/src/leanback/res/drawable/`. If a Lucide icon is needed in `main` or `mobile`, extend the generator with an explicit destination rather than copying or recreating the vector by hand.
- Brand marks, provider logos, content artwork, QR codes, and platform-mandated graphics are exceptions. When Lucide has no suitable semantic icon, document the exception in the change and prefer a reviewable vector asset; do not silently approximate it with custom path data.
- After any icon change, run `python3 tools/lucide/generate_android_vectors.py --check`, `python3 -m unittest tools.tests.test_tv_theme_resources`, and the affected Android build/lint tasks. For visible TV changes, verify default and focused states on a 16:9 TV device or emulator and retain screenshot evidence.

## Testing Guidelines

No automated test suite or coverage threshold is currently configured. Run the relevant checks above and document manual verification. Test affected Android flavors on devices/emulators, especially playback and navigation. For website changes, verify links, search, copying, and responsive layouts in the static preview. If adding Android tests, use `src/test/` or `src/androidTest/` and descriptive `*Test` names.

## Commit & Pull Request Guidelines

History favors short imperative subjects such as “Fix…” and “Refine…”. Follow the Lore protocol: explain intent first, adding useful `Constraint:`, `Tested:`, and `Not-tested:` trailers. Keep changes focused. PRs should describe behavior changes, link relevant issues, list validation and limitations, and include screenshots for UI changes.

## Security & Configuration

Never commit keystores, passwords, or `local.properties`. Keep local HTTP control endpoints on trusted networks; do not expose them publicly.
