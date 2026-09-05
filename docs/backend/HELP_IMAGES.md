# Help pictures — served from the API host, not shipped in the base module

The help centre's 215 WebP pictures (8.4 MB) left `core/help/src/main/assets` in 4.41.0. The base
module's download fell under the 9 MiB budget the store gate now enforces
(`scripts/release/check-bundle-size.sh … 9`), and the pictures are fetched on first open through the
app's image loader (Coil, the app's own OkHttp client, a disk cache) and read from disk after that.

## What the backend serves

| Path | Source in this repository | Content type |
|---|---|---|
| `GET {API_BASE_URL}/assets/help/images/<file>.webp` | `assets-cdn/help/images/<file>.webp` | `image/webp`, cacheable for a day or more |

`{API_BASE_URL}` is the build's `BuildConfig.API_BASE_URL` — `https://coineprofx.com/` on a
release. The file names are the ones `core/help/src/main/assets/help/content.json` references;
`HelpCatalogTest` fails when a referenced name is missing from `assets-cdn/help/images`, so the
directory in this repository is the source of truth the host mirrors.

The same host already serves the symbol logos the vendored artwork does not cover
(`/assets/logo/<SYMBOL>.webp`, see `LogoProvider`), so this is one more directory under the same
static root.

## Syncing

Copy `assets-cdn/help/` to the static root as-is (`rsync -a assets-cdn/help/ <root>/assets/help/`).
Nothing is renamed; nothing is processed. A picture edited here is a picture the host should re-sync.

## What the app does without the host

A skeleton while a picture loads; the caption alone if it never arrives; never a broken-image
glyph. `content.json` — every topic's words — stays in the base module, so the help centre is
complete without the pictures, and the pictures are complete once the host serves them.
