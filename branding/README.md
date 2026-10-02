# MarbleMD brand assets

The MarbleMD symbol tells the product story in one mark:

- a **folded page** — Markdown documents;
- a geometric **M** ribbon — MarbleMD, Markdown;
- two opposing **arrows** — RTL/LTR bidirectional reading;
- **marble veins** across a midnight gradient — the "marble" in the name.

Palette: Midnight `#06132F`, Deep blue `#0B2A6E`, Violet `#2B1E86`, Cyan `#00D5E8`,
Indigo `#135DF5`, Purple `#7655FF`, Paper `#FFFFFF`.

## Everything is generated

`tools/generate-icons.py` holds one geometry definition and emits every asset the
app needs, so the icon can never drift between densities:

```bash
python3 tools/generate-icons.py
```

| Output | Purpose |
| --- | --- |
| `app/src/main/res/drawable/ic_launcher_background.xml` | adaptive icon background layer (108dp canvas with bleed) |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | adaptive icon foreground layer |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | themed icon (Android 13+) |
| `app/src/main/res/drawable/ic_marblemd_mark.xml` | tintable in-app mark |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher[_round].xml` | adaptive icon for API 26+ |
| `app/src/main/res/mipmap-anydpi/ic_launcher[_round].xml` | vector fallback for API 24/25 |
| `app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher[_round].png` | bitmaps for legacy launchers and stores |
| `branding/marblemd-app-icon.png` | 512×512 presentation icon |
| `branding/marblemd-play-store-512.png` | 512×512 full-bleed store icon |
| `branding/marblemd-feature-graphic.png` | 1024×500 feature graphic |

`branding/marblemd-logo.svg` is the hand-maintained horizontal logo used for
documents and the README.
