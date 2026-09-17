# honey-style-plugin

WebStorm support for [honey-style](https://github.com/React-Hive/honey-style): theme color
swatches, navigation and completion, plus `@honey-*` at-rules.

```ts
const Tab = styled(HoneyBox)`
  color: ${colors.primary.royalBlue};                    // 🟦 swatch
  background-color: ${resolveColor('primary.royalBlue', 0.25)};  // 🟦 swatch, 25% alpha
  border-color: secondary.mediumGreen;                   // 🟩 swatch
`;
```

The palette is read from **your project's own theme sources** — nothing is hardcoded. Any
`colors` or `palette` object literal is flattened into dotted paths, so the plugin
tracks the palette as it changes.

## What gets a swatch

| Form | Example |
| --- | --- |
| Destructured palette | `colors.primary.royalBlue` |
| Theme access | `theme.colors.neutral.fogGrey` |
| `resolveColor` | `resolveColor('primary.royalBlue', 0.25)` — alpha is applied to the swatch |
| `getColor` / `getContrastColor` | `getColor('primary.main')` |
| honey-layout color props | `$backgroundColor="accent.mediumGold"`, `$fill="accent.darkTeal"` |
| Bare path in a styled/css template | `border-color: secondary.mediumGreen;` |

Clicking the gutter swatch opens the color picker. Because only theme tokens are valid in these
positions, picking a color **rewrites the path to the nearest token in the palette** rather than
writing a raw hex value.

A plain CSS color works in these positions too — `$backgroundColor="white"` or
`resolveColor('#318BFA')` — because honey passes a non-path value straight through. Those get a
swatch as well, and picking a color there writes a hex value instead of snapping to a token.

Only the props honey-layout resolves a path for are recognised — `$color`, `$backgroundColor`,
`$borderColor` plus the four per-side variants, `$outlineColor`, `$textDecorationColor`, `$fill` and
`$stroke` (honey-style's `CSS_COLOR_PROPERTIES`). Anything else, such as `$caretColor`, passes the
string straight through to CSS, so a path there would not resolve and gets no swatch.

## Navigation

Cmd/Ctrl+Click (or Ctrl+B) on a path jumps to the declaration holding the value —
`royalBlue: '#318BFA'` in the theme file — instead of the key type. This works in all the forms
above, including paths written as plain text inside a template literal, where TypeScript cannot
follow at all.

## Completion

Color path completion shows the actual color as the item icon, with the hex on the right:

- in a `resolveColor('…')` / `getColor('…')` / `getContrastColor('…')` argument;
- after a palette group, e.g. `colors.primary.`.

If your project augments `HoneyColors` with concrete key unions (portalui does, in
`libs/ui-components/src/honey-theme/index.ts`), TypeScript already offers these names — the plugin
runs first and adds the swatch and hex to those items rather than duplicating them. Without that
augmentation `HoneyColors` is `Record<string, HoneyCssColor>`, TypeScript offers nothing, and the
plugin supplies the whole list.

Bare paths inside a css template literal are **not** completed on purpose: honey-style does not
resolve them at runtime, so `border-color: secondary.mediumGreen;` compiles to invalid CSS. They
still get a swatch, so an existing one is easy to spot.

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

Rules that take parameters insert the parens for you. Parameters are completed too:

- `@honey-media` — breakpoint keys read from your `theme.breakpoints` (`sm`, `sm:up`, `sm:down`, …),
  plus `portrait`/`landscape` and `all`/`print`/`screen`/`speech`. Several space-separated tokens are
  supported, and only the one under the caret is completed.
- `@honey-center` — `horizontal`, `vertical`.
- `@honey-if` — `true`, `false`.

Each at-rule shows the arguments it accepts, and each argument shows its category:

```
honey-media (breakpoint[:up|:down], orientation, media type)
honey-center (horizontal | vertical | (empty))

landscape    orientation
screen       media type
sm:up
```

Press Ctrl+Q (Quick Documentation) on one to see the resolved `@media (...)`, the breakpoint's value
from your theme, and a note that a bare key means `:up`.

## Building

Requires a JDK 25 — WebStorm's bundled JetBrains Runtime is one, and `gradle.properties` points at
it by default, so no separate JDK install is needed.

```bash
./gradlew buildPlugin
```

The installable zip lands in `build/distributions/honey-style-plugin-<version>.zip`.

Other tasks:

```bash
./gradlew test
```

```bash
./gradlew runIde
```

`runIde` launches a sandboxed WebStorm with the plugin loaded — the quickest way to try a change.

### Build configuration

Both paths live in `gradle.properties`:

- `platformLocalPath` — the IDE the plugin compiles against. It defaults to
  `/Applications/WebStorm.app/Contents`, so no ~1 GB IDE distribution is downloaded. Override with
  `-PplatformLocalPath=...` or the `WEBSTORM_HOME` environment variable.
- `org.gradle.java.home` — the JDK 25 used to build. Delete the line to fall back to `JAVA_HOME`.

## Installing

Settings → Plugins → ⚙ → **Install Plugin from Disk…** → pick the zip from
`build/distributions/`, then restart the IDE.

## Settings

Settings → Tools → **Honey Style** (per project):

- **Show theme color swatches** — master switch.
- **Also scan styled/css template literals for bare paths** — the
  `border-color: secondary.mediumGreen;` case.
- **Scan .ts/.tsx files whose path contains** — discovery filter, `theme` by default.
- **Additional theme files** — project-relative paths scanned regardless of the filter, and given
  priority when two palettes declare the same path.

The page also reports how many colors were found and which files they came from, and has a
**Rescan theme files** button.
