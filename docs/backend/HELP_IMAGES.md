# Help pictures — shipped in the APK

The help centre's 215 WebP pictures (8.4 MB) live in `core/help/src/main/assets/help/images` and
ship in the base module. `HelpCatalogTest` fails when `content.json` references a file that is not
there; the sheet decodes each one straight from the packaged asset.

## History

4.42.0 moved them out to `assets-cdn/help/images` and fetched them from
`{API_BASE_URL}/assets/help/images/<file>` through Coil, to get the base download under nine
megabytes. The owner's answer was that every picture behind «؟» has to be there from the first
open, host or no host, and 4.43.0 put them back. **The API host does not need to serve them.** The
store gate's budget is 16 MiB again (`scripts/release/check-bundle-size.sh … 16`).

If the download size ever has to come down, the route is Play Asset Delivery on a store build, not
a host: the owner takes the universal APK directly, and an asset pack is empty outside Play.
