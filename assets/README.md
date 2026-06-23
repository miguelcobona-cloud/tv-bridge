# TV-Bridge — Brand assets

Shared visual identity for the web client and Android TV receiver.

## Color palette

| Token | Hex | Usage |
|-------|-----|-------|
| Background | `#0F172A` | App shell, launcher background |
| Surface | `#1E293B` | Cards, TV frames |
| Accent | `#3B82F6` | Logo, links, focus |
| Text | `#F1F5F9` | Primary copy on dark |

## Files

| File | Description |
|------|-------------|
| `logo.svg` | Primary mark (TV + bridge signal) |
| `favicon.svg` | Simplified mark for browser tabs |
| `tv-bridge-app-icon.png` | Raster launcher / README / social (1024×1024) |
| `tv-bridge-banner.png` | Wide banner for README and docs (1280×640) |

## Web (`signaling-server/public/icons/`)

- `logo.svg` — header brand
- `favicon.svg` — tab icon
- `icon-tv.svg` — TV list rows
- `icon-empty.svg` — empty state
- `apple-touch-icon.png` — iOS home screen

## Android (`android-tv-receiver/app/src/main/res/drawable/`)

- `ic_launcher.xml` — launcher icon (vector)
- `ic_launcher_foreground.xml` / `ic_launcher_background.xml` — adaptive layers (API 26+)
- `tv_banner.xml` — Android TV leanback banner (320×180)
- `ic_logo.xml` — in-app logo on setup screen
