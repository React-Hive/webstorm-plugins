# honey-style-plugin

IDE support for [honey-style](https://github.com/React-Hive/honey-style): color swatches,
navigation and completion for theme values, plus the `@honey-*` at-rules.

```ts
const Tab = styled(HoneyBox)`
  color: ${colors.primary.royalBlue};                            // 🟦 swatch
  background-color: ${resolveColor('primary.royalBlue', 0.25)};  // 🟦 swatch at 25%

  @honey-media (sm:up) {                                         // completes from theme.breakpoints
    border-color: ${colors.secondary.mediumGreen};               // 🟩 swatch
  }
`;

<HoneyBox $backgroundColor="accent.mediumGold" />                {/* 🟨 swatch */}
```

The palette and breakpoints are read from **your project's own theme sources** — nothing is
hardcoded. The `colors` property of your theme object is flattened into `group.name` paths, and
`breakpoints` supplies `@honey-media`, so the plugin tracks the theme as it changes.

A path is exactly two segments, because that is what honey resolves: `resolveColor` reads two parts
of the split, so anything deeper never resolves and is never offered.

## Swatches

| Form | Example |
| --- | --- |
| Destructured palette | `colors.primary.royalBlue` |
| Theme access | `theme.colors.neutral.fogGrey` |
| `resolveColor` | `resolveColor('primary.royalBlue', 0.25)` — alpha applied to the swatch |
| honey-layout color props | `$backgroundColor="accent.mediumGold"`, `$fill="accent.darkTeal"` |
| Bare path in a css template | `border-color: secondary.mediumGreen;` — see the caveat below |

Clicking a swatch opens the color picker. On a theme path it **rewrites the path to the nearest
token in the palette** rather than writing a raw hex, since a token is what belongs there.

Plain CSS colors work wherever honey resolves one — `$backgroundColor="white"`,
`resolveColor('#318BFA')` — because a non-path value is passed straight through. Those get a swatch
too, and picking a color there writes a hex, rather than snapping to a token you chose not to use.
All 148 CSS color names are recognised, and you can add your own in settings.

### Two things that look supported but are not

**A bare path in css text does not resolve.** `resolveColor` only runs inside `${...}`; nothing in
honey-style's css pipeline scans css text for dotted paths, so `border-color: secondary.mediumGreen;`
emits literal, invalid CSS. The plugin still shows a swatch so an existing one is easy to spot, but
deliberately never completes one.

**Only some props resolve a path.** honey-layout calls `resolveColor` for the props in honey-style's
`CSS_COLOR_PROPERTIES` — `color`, `backgroundColor`, `borderColor` and its four per-side variants,
`outlineColor`, `textDecorationColor`, `fill`, `stroke`. On anything else, such as `$caretColor`, the
string reaches CSS verbatim. Those props get no swatch, and the list is configurable if you wrap
honey-layout with props of your own.

## Navigation

Cmd/Ctrl+Click (or Ctrl+B) on a path jumps to the declaration holding the value —
`royalBlue: '#318BFA'` in your theme file — rather than to the key type TypeScript resolves to. It
works in every form above, including paths written as plain text inside a template literal, where
TypeScript cannot follow at all.

## Completion

Color paths complete with the actual color as the item icon and the hex on the right:

- in a `resolveColor('…')` argument, or any function you list in settings;
- after a palette group, e.g. `colors.primary.`;
- in a honey-layout color prop, e.g. `$backgroundColor="…"`.

The list is grouped — theme tokens first, then CSS color names, then everything else TypeScript
offers here. That last group is noisier than it looks: `HoneyCssColor` resolves to csstype's
`Property.Color`, which unions the named colors with `inherit`/`unset` and 38 *deprecated* CSS system
colors such as `Background` and `AppWorkspace`. Those are valid CSS, resolved by the OS, so they have
no fixed value and get no swatch — they are simply sorted to the bottom rather than hidden.

CSS color names get a swatch in the same list. TypeScript already offers them in these positions
from `HoneyCssColor`, so the plugin decorates those items rather than adding its own — typing
`$backgroundColor="w"` shows `white`, `wheat` and `whitesmoke` with their colors next to your theme
paths.

If your project augments `HoneyColors` with concrete key unions — portalui does, in
`libs/ui-components/src/honey-theme/index.ts` — TypeScript already offers these names. The plugin
runs first and decorates those items with the swatch and hex instead of duplicating them. Without
that augmentation each group is `Record<string, HoneyCssColor>`, TypeScript offers nothing, and the
plugin supplies the whole list.

## At-rules

Inside a styled/css template literal, typing `@` completes the honey at-rules:

| Rule | Expands to |
| --- | --- |
| `@honey-media (…)` | breakpoint media query |
| `@honey-stack (…)` | `display:flex; flex-direction:column; gap` |
| `@honey-inline (…)` | `display:flex; gap` |
| `@honey-center (…)` | centering — `horizontal` \| `vertical` |
| `@honey-if (…)` | `true` \| `false` |
| `@honey-ellipsis` | `overflow; text-overflow:ellipsis` |
| `@honey-absolute-fill` | `position:absolute; inset:0` |

Rules that take parameters insert the parens for you, and each item shows the arguments it accepts:

```
honey-media (breakpoint[:up|:down], orientation, media type)
honey-center (horizontal | vertical | (empty))

landscape    orientation
screen       media type
sm:up
```

Parameters complete as well:

- `@honey-media` — breakpoint keys from your `theme.breakpoints` (`sm`, `sm:up`, `sm:down`, …), plus
  `portrait`/`landscape` and `all`/`print`/`screen`/`speech`. Several space-separated tokens are
  supported, and only the one under the caret completes.
- `@honey-center` — `horizontal`, `vertical`.
- `@honey-if` — `true`, `false`.

Ctrl+Q (Quick Documentation) on any of them shows the resolved `@media (...)`, the breakpoint's value
from your theme, and a reminder that a bare key means `:up`.

## Settings

Settings → Tools → **Honey Style**, per project:

| Setting | Default |
| --- | --- |
| Show theme color swatches | on — master switch |
| Also scan styled/css template literals for bare paths | on |
| Scan .ts/.tsx files whose path contains | `theme` |
| Additional theme files | empty — project-relative paths scanned regardless of the filter, and given priority when two palettes declare the same path |
| Extra color names | empty — one `name = value` per line, e.g. `brand = #318BFA`. Adds to the 148 CSS names, and may redefine one |
| Color props | honey-layout's list — one per line, `$` optional |
| Color functions | `resolveColor`, the only one honey-style exports — add wrappers, or legacy helpers such as `getColor` |

Each list falls back to its default when left empty, so no field can be left in a state where
nothing resolves. The page also reports how many colors were found and which files they came from,
and has a **Rescan theme files** button.

## Building

```bash
./gradlew buildPlugin
```

The installable zip lands in `build/distributions/honey-style-plugin-<version>.zip`. Other tasks:

```bash
./gradlew test
```

```bash
./gradlew runIde
```

`runIde` launches a sandboxed WebStorm with the plugin loaded — the quickest way to try a change
without reinstalling.

Requires a JDK 25, because the IntelliJ Platform ships Java 25 bytecode. No separate install is
needed: `gradle.properties` points at WebStorm's bundled JetBrains Runtime. Two paths live there:

- `platformLocalPath` — the IDE compiled against. Defaults to `/Applications/WebStorm.app/Contents`,
  so no ~1 GB IDE distribution is downloaded. Override with `-PplatformLocalPath=...` or the
  `WEBSTORM_HOME` environment variable.
- `org.gradle.java.home` — the JDK 25 used to build. Delete the line to fall back to `JAVA_HOME`.

### Developing

Open the plugin in **IntelliJ IDEA**, not WebStorm — WebStorm has no Java language support, so
nothing resolves there. Opening the repository root is not enough either: each plugin directory is
its own Gradle build, so link `honey-style/build.gradle.kts` (right-click → **Link Gradle Project**,
or the Gradle tool window's **+**). Keep testing in WebStorm, which is what the plugin targets.

Code style is in `.editorconfig` at the repository root: four spaces, 120 columns, no wildcard
imports.

## Installing

Settings → Plugins → ⚙ → **Install Plugin from Disk…** → pick the zip from
`build/distributions/`, then restart the IDE. The change notes in Settings → Plugins say which
version is loaded.
