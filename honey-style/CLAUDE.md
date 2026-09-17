# CLAUDE.md

WebStorm/IntelliJ plugin for [honey-style](https://github.com/React-Hive/honey-style): color
swatches, navigation and completion for theme color paths, plus `@honey-*` at-rule support.

The plugin is deliberately named for the library, not for one feature, so it can grow past colors.

## Tech stack

Java 25, Gradle (wrapper), IntelliJ Platform Gradle Plugin 2.x. No Kotlin — see the build
constraint below.

## Commands

| Task | Command |
| --- | --- |
| Build the installable zip | `./gradlew buildPlugin` |
| Run unit tests | `./gradlew test` |
| Try it in a sandbox IDE | `./gradlew runIde` |

Artifact: `build/distributions/honey-style-plugin-<version>.zip`.

## Build constraints

- **The platform ships Java 25 bytecode** (class file major 69). The plugin must be compiled by a
  JDK 25, and cannot target an older release. `org.gradle.java.home` in `gradle.properties` points
  at WebStorm's bundled JetBrains Runtime so no separate JDK is required.
- **The plugin compiles against the locally installed WebStorm** via `local(...)` in
  `build.gradle.kts`, not a downloaded IDE distribution. Override with `-PplatformLocalPath=...`
  or `WEBSTORM_HOME`.
- Java was chosen over Kotlin deliberately: the Kotlin Gradle plugin lags new JVM targets, and
  jvmTarget 25 is not reliably available. `javac` from the JBR handles it with no version matrix.
- `instrumentCode = false` — there are no `.form` files, and this avoids pulling the instrumenter.
- `untilBuild` is intentionally unset so an IDE upgrade does not disable the plugin.

## Architecture

Source lives in `src/main/java/com/reacthive/honeystyle/`.

```
HoneyThemeIndexer   discovers + parses theme files -> HoneyThemeModel
HoneyThemeService   project service, caches the model
HoneyThemeModel     palette + breakpoint keys
HoneyPalette        path -> HoneyColorEntry lookup, nearest-token search
HoneyColorPaths     recognises a color path at a PSI leaf
CssColorParser      CSS color literal -> java.awt.Color

HoneyPathMatcher    longest sub-path of a dotted token that exists in the palette

HoneyElementColorProvider              swatch + picker for JS expressions
HoneyTemplateColorLineMarkerProvider   swatch for bare paths inside template literals
HoneyColorGotoDeclarationHandler       Cmd+Click -> the property holding the value
HoneyColorCompletionContributor        path completion with color swatches
HoneyAtRuleCompletionContributor       @honey-* at-rules and their parameters
HoneyAtRuleDocumentationProvider       Ctrl+Q docs for those completion items
HoneyAtRules        at-rule metadata shared by completion and docs
HoneyBreakpoint     a theme.breakpoints entry, with its CSS value
HoneyStyleSettings / HoneyStyleConfigurable   per-project settings
```

Extension points are registered in `src/main/resources/META-INF/plugin.xml`.

## Things that are easy to get wrong

**One swatch per expression.** `ElementColorProvider.getColorFrom` is called for *every* PSI
element, so `HoneyElementColorProvider` handles leaves only (`getFirstChild() == null`). Handling
both a leaf and its parent produces duplicate gutter icons. It also pre-filters on parent type
before touching the palette, because this runs on every identifier in the file.

**The palette cache must not depend on `PsiModificationTracker.MODIFICATION_COUNT.`** That fires on
every keystroke anywhere in the project and would re-scan the file index constantly. `HoneyThemeService`
depends on the theme `PsiFile`s that were actually parsed, plus `VFS_STRUCTURE_MODIFICATIONS` (so a
newly added theme file is noticed), the settings tracker, and the `DumbService` tracker (so the
empty result returned during indexing expires afterwards).

**Template literals need a `LineMarkerProvider`, not `ElementColorProvider`.** A template chunk is a
single PSI element spanning many lines, so each occurrence needs its own `TextRange`. Markers are
anchored on the leaf returned by `findElementAt` — the platform expects a leaf element.
`JSStringTemplateExpression.getStringRanges()` excludes `${...}` interpolations, which is why an
interpolated path does not get two swatches; the code tolerates both relative and absolute ranges
because that contract is not guaranteed.

**Two palettes can declare the same path.** portalui has the current `theme.colors`
(`primary.royalBlue`) plus a deprecated `colors` object that reuses names like
`secondary.light` for different values. `HoneyPalette` therefore indexes entries both by bare path
and by `<root>.<path>`. `colors2` is deliberately excluded from indexing - it collides with the
deprecated `colors` object on paths like `secondary.light` while holding different values.
`HoneyThemeIndexer.score` ranks candidate files so mocks, docs and build output lose to real theme
sources, with a `honey` bonus breaking ties toward the current palette.

**`setColorTo` snaps rather than overwrites.** Only theme tokens are valid in these positions, so
picking a color rewrites the path to the nearest palette entry. It edits through the `Document`
rather than a PSI factory, and replaces only the path segment — replacing the whole reference would
destroy the qualifier in `useHoneyStyle().colors.primary.royalBlue`.

**TypeScript already covers most completion.** honey-style's `HoneyColorKey` is
`` `${ColorType}.${keyof HoneyColors[ColorType]}` ``. When a project augments `HoneyColors` with
concrete key unions — portalui does — that collapses to a real union and the TS service completes
both `resolveColor('…')` and `colors.primary.`. So `HoneyColorCompletionContributor` registers with
`order="first"`, intercepts via `runRemainingContributors`, and decorates matching items with a
swatch instead of adding duplicates; it only adds entries TypeScript did not offer. Without the
augmentation each group is `Record<string, HoneyCssColor>`, TS offers nothing, and the plugin
supplies everything. It returns early unless the caret is in one of the two known positions, so it
does not slow down completion elsewhere.

**Bare paths in css text are not valid honey-style.** `resolveColor` only runs inside `${...}`;
nothing in `packages/react-hive/honey-style/src/css/` resolves a dotted path found in CSS text
(`isCssColorProperty` is exported but unused). They get a swatch and navigation, because reading an
existing one is useful, but they are deliberately never completed.

**A `GotoDeclarationHandler` that returns targets wins over normal resolution.** That is intended
here: TypeScript resolves `colors.primary.royalBlue` to the key type, and the value is more useful.
The handler returns null whenever the path is not in the palette, so ordinary navigation is
untouched.

**At-rule completion has to survive CSS injection.** WebStorm's styled-components support injects
CSS into the template, so the caret sits in an injected CSS file, not the host TS file - a
`language="JavaScript"` contributor never fires there. `HoneyAtRuleCompletionContributor` registers
for `language="any"` and guards instead: it bails unless the lookback text contains `@` and the
caret resolves (directly or through the injection host) to a `JSStringTemplateExpression`. Matching
is done on text before the caret rather than on PSI, because the injected CSS tree for an unknown
`@honey-*` rule is an error node.

**The at-rule list is built in; breakpoints are not.** At-rule names are the library's API surface
and are versioned with it, so they live in `AT_RULES`. Breakpoint keys are project configuration and
are read from `theme.breakpoints` by the indexer, exactly like colors. Adding a rule upstream means
adding a line to `AT_RULES`.

**Completion rows show accepted arguments, nothing more.** An at-rule item carries its parameter
list as tail text, and a parameter item carries its category as type text - both are what you need
while choosing. What was removed is the stuff that made the row unreadable on one line: the rule's
expansion, and the resolved `min-width: 768px` plus a duplicate px value on every breakpoint. That
detail lives in quick documentation, which has room for it.

**Quick documentation for items with no PSI.** At-rule completion items are plain strings, so
there is nothing for the documentation system to resolve. `HoneyAtRuleDocumentationProvider`
implements `getDocumentationElementForLookupItem` and returns a `FakePsiElement` carrying the key,
which `generateDoc` then renders - the same approach as the platform's live template documentation.
It registers through the language-independent `documentationProvider` EP, not
`lang.documentationProvider`, because the caret may be in injected CSS.

**`up`/`down` map to raw min-width/max-width.** honey-style emits the breakpoint value unchanged for
both directions - there is no `- 0.02px` adjustment, unlike Bootstrap-style systems - and an omitted
direction means `up`. `HoneyAtRulesTest` pins this so the documentation cannot drift from
`create-media-at-rule-transformer.ts`.

**JSX props are XML PSI, not JS literals.** `$backgroundColor="accent.mediumGold"` parses to an
`XmlAttributeValue`, so the `JSLiteralExpression` branches never saw it. `HoneyColorPaths` handles
it as a third form, keyed on the leaf starting at the unquoted range so one prop yields one swatch.
Both the swatch and completion are gated on `COLOR_PROPS`, mirroring honey-style's
`CSS_COLOR_PROPERTIES`: `honey-layout/src/helpers/helpers.ts` calls `resolveColor` only for those,
so a path on any other prop reaches CSS verbatim. Matching on "name contains color" would wrongly
bless `$caretColor` and `$floodColor`. Like the at-rule names, this list is library API and is
mirrored here; the theme-derived data stays discovered.

**Literal CSS colors are valid in some positions, not all.** `Reference.allowsCssColor` marks the
places honey accepts a raw color as well as a path: a honey-layout color prop, and a color function
argument (`resolveColor` splits on `.` and returns the input unchanged when there is none). Plain
string literals elsewhere still require a dotted path, otherwise every `'red'` in the project would
get a swatch. Where the value is literal, `setColorTo` writes a hex instead of snapping to the
nearest token - the author chose not to use a token.

**Reference anchoring.** `HoneyColorPaths` accepts a dotted chain only when it is anchored on
`colors` / `palette` / `theme`, or when it is a bare two-segment `group.name`. Without
that rule, ordinary property access would be decorated.

## Conventions

- `-Xlint:all` is on and the build is warning-free; keep it that way rather than suppressing.
  Notably `JSReferenceExpression.getReferencedName()` is deprecated - use `getReferenceName()`
  from `JSReferenceItem`.
- Clamping uses `Math.clamp` (Java 21+), not `Math.max`/`Math.min` pairs.
- The one `@SuppressWarnings` is `serial` on the `FakePsiElement` subclass: platform PSI base
  classes are incidentally `Serializable` and PSI is never serialized.

- No inline `//` commentary in method bodies. Class-level Javadoc is the API documentation;
  non-obvious rationale belongs in this file.
- Unit tests cover the pure logic only (`CssColorParser`, `HoneyPalette`, the template sub-path
  matcher) and run as plain JUnit without booting an IDE fixture. Anything touching PSI is verified
  by compilation and by `runIde`.
