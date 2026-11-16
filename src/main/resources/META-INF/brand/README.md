# Elicit Default Brand

This directory is the **canonical default brand** for Elicit applications
(Survey, Admin and Author). It carries the Choice E mark -- three answer
rows stacked into an E, the middle one selected -- the Ink/Verdigris palette
taken from that mark, a warm Paper background, and IBM Plex Sans and Mono.

Each application embeds a copy of this directory at
`src/main/resources/META-INF/brand/` as its built-in default. This directory
can also be volume-mounted at `/opt/brand` (the path read via the
`brand.file.system.path` config property) to override the embedded default
at runtime without rebuilding -- the two are the same files. `checkBrand.sh`
in the umbrella repository fails when an embedded copy, or the manuals' style
package `docs/manual/elicit-brand.sty`, differs from this directory; it runs
before `buildDockerImages.sh` builds anything. After a change here, run
`./checkBrand.sh --sync` to copy it into the modules.

## Using This Brand in a New Project

1. Copy this entire directory into the new project as
   `src/main/resources/META-INF/brand/`.
2. Update `brand-config.json`'s `name`/`organization` if the new project
   needs its own display name (optional -- "Elicit Default Brand" is a
   reasonable default).
3. No other changes are required. The new project's `styles.css` must define
   the same `--lumo-*` variable mapping from `--brand-*` tokens that Survey
   and Admin already use (see either app's
   `src/main/resources/META-INF/resources/styles.css` for the pattern to
   copy).

## Directory Structure

```
elicit-brand/
├── brand-config.json          # Brand metadata (name, organization, logos, favicon)
├── brand-info.json            # Debug/display metadata (shown in a meta tag)
├── colors/
│   └── brand-colors.css       # CSS custom properties: palette + --brand-* mappings
├── typography/
│   ├── brand-typography.css   # @font-face rules, font stacks, type scale, utility classes
│   ├── typography.json        # Font metadata (for tooling/documentation)
│   └── fonts/                 # IBM Plex Sans, Sans Arabic and Mono (WOFF2) and their license
├── images/
│   ├── favicon.ico            # The white mark on an Ink tile, 16/32/48 px
│   ├── HorizontalLogo.png     # The ELICIT / SURVEY lockup
│   ├── icon-white.png         # The white mark, shown in the application header
│   └── source/                # SVG masters the three files above are rendered from
└── theme.css                  # Imports colors + typography together
```

## Color Palette

- **Ink** (`--elicit-ink`, `#0A3D62`) -- primary brand color and the mark's
  own navy; drives the header background and primary buttons. Paper text on
  it is ~11.0:1.
- **Verdigris** (`--elicit-verdigris`, `#22775D`) -- accent/interactive color
  and the mark's selected row; drives secondary buttons and, as its pale
  shade, the access-code pill background.
- **Paper** (`--elicit-paper`, `#FDFCFA`) -- warm off-white base background.
- **Slate** (`--elicit-slate`, `#4A5A6A`) -- question/field-label text
  (`--brand-text-secondary`), ~6.5:1 contrast on Paper.
- **Charcoal** (`--elicit-charcoal`, `#1A2229`) -- heading/body text
  (`--brand-text-primary`), ~14.8:1 contrast on Paper. Deliberately near-black
  rather than Ink, so body copy doesn't read as blue-tinted next to the navy
  header.
- **Brick** / **Amber** -- muted error/warning status colors, tuned to sit in
  the same warm/muted family as Ink and Verdigris rather than stock reds/
  yellows.

## How Brand Loading Works

1. **Colors** (`colors/brand-colors.css`) define the `--brand-*` and
   `--elicit-*` CSS custom properties.
2. **Typography** (`typography/brand-typography.css`) declares the brand's
   fonts, and defines font stacks, sizes, and a small set of `.elicit-*`
   utility classes.
3. **Theme** (`theme.css`) imports both of the above; this is what an
   application's `AppConfig` inlines into the page `<head>`.

Each application's `styles.css` provides the CSS variable *contract* --
mapping `--lumo-*` Lumo theme tokens to `--brand-*` tokens with sensible
fallbacks -- so a brand only needs to populate `--brand-*` values, never
`--lumo-*` directly. In particular, `--lumo-header-text-color` and
`--lumo-body-text-color` derive from `--lumo-shade`, which this brand
deliberately does **not** override -- leaving it unset gives near-black text
from Lumo's own default, avoiding a blue-tinted `--lumo-shade` fighting with
the Ink header.

## Fonts

The fonts are files in this brand, under `typography/fonts/`, and
`brand-typography.css` names them in `@font-face` rules with a `url()`
relative to that stylesheet (`url("fonts/IBMPlexSans-Regular.woff2")`). The
applications inline the stylesheet into the page and resolve each relative
`url()` against the brand, so the browser fetches the font from the
application itself. Nothing is requested from a third party, which is what a
site wants for a page a respondent opens. A brand of your own can carry fonts
the same way, or carry none and name a system font stack.

IBM Plex is licensed under the SIL Open Font License 1.1; the license travels
with the files as `typography/fonts/LICENSE.txt`.

## The Logo Files

The applications read three fixed names from `images/`: `HorizontalLogo.png`
(the lockup, shown on the System > Branding pages and in the manuals),
`icon-white.png` (the mark, shown in the header on the primary color) and the
favicon named in `brand-config.json`. They are rendered from the SVG masters
in `images/source/`, whose lettering is IBM Plex Sans converted to outlines.
`docs/brand-proposals/build-brand-assets.py` in the umbrella repository draws
the masters.

## Testing a Brand Override Locally

```bash
docker compose up -d
# then, to test THIS brand as an external override instead of the embedded default:
# add under the survey/admin service in docker-compose.yml:
#   volumes:
#     - ./elicit-brand/:/opt/brand:ro
```

## Translated brand names

The organization name (and the `brand-info.json` description) can carry per-language
variants in an optional `localized` block keyed by language tag. The application shows the
variant for the respondent's language, falling back to the language alone and then to the
base value; the base `name` also derives the technical brand key and must stay as it is.

```json
{
  "name": "Elicit Default Brand",
  "organization": "Elicit Software",
  "localized": {
    "es-419": { "name": "Marca predeterminada de Elicit", "organization": "Elicit Software" },
    "ar":     { "name": "العلامة الافتراضية لـ Elicit", "organization": "Elicit Software" }
  }
}
```

The `localized` block in `brand-info.json` sits inside `"brand"` and may carry `description`.
