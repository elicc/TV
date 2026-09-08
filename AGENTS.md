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

## Coding Style & Naming Conventions

Match surrounding code: four-space indentation for Java/Gradle; two spaces, double quotes, and semicolons for TypeScript. Use PascalCase for Java classes and React components, camelCase for methods/variables, and snake_case for Android resources. Website linting uses Next.js ESLint rules. Reuse existing utilities; avoid unrelated formatting changes or new dependencies.

## Testing Guidelines

No automated test suite or coverage threshold is currently configured. Run the relevant checks above and document manual verification. Test affected Android flavors on devices/emulators, especially playback and navigation. For website changes, verify links, search, copying, and responsive layouts in the static preview. If adding Android tests, use `src/test/` or `src/androidTest/` and descriptive `*Test` names.

## Commit & Pull Request Guidelines

History favors short imperative subjects such as “Fix…” and “Refine…”. Follow the Lore protocol: explain intent first, adding useful `Constraint:`, `Tested:`, and `Not-tested:` trailers. Keep changes focused. PRs should describe behavior changes, link relevant issues, list validation and limitations, and include screenshots for UI changes.

## Security & Configuration

Never commit keystores, passwords, or `local.properties`. Keep local HTTP control endpoints on trusted networks; do not expose them publicly.
