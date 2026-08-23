# Phase 8 — AI Vision Contract

## Trust boundary

Android never executes raw multimodal model text. AI Vision may expose an action only when the server returns a validated structured `actionable` result with a positive persisted `signal_id`. The user then enters the existing Signal Detail → Execution flow.

## Image privacy and preparation

- Camera capture uses CameraX and requests `CAMERA` only when the user chooses capture.
- Gallery/file selection uses Android's document picker and does not require broad storage permission.
- The selected image is decoded, orientation-normalized, resized to a maximum 2048 px edge when needed, and re-encoded as JPEG before upload.
- Re-encoding strips original EXIF metadata from the outbound payload.
- Temporary CameraX files created inside the app cache are deleted after preprocessing, including preprocessing failure paths.
- Prepared uploads are limited to 6 MB.
- Local file paths, image bytes and EXIF metadata must never be logged.

## API

Authenticated endpoints:

- `POST /user/ai/vision/jobs`
  - `multipart/form-data`
  - image part name: `image`
  - accepted client payloads are prepared image JPEG/PNG/WebP under the client size limit
- `GET /user/ai/vision/jobs/{job_id}`

Android recognizes only these server job states:

- `queued`
- `running`
- `done`
- `failed`
- `expired`

No local progress percentage or timer is converted into a success state.

## Structured result

Every displayable result must have `validated=true` and an assessment of:

- `actionable`
- `low_confidence`
- `unknown`
- `unsupported`

Common optional fields:

- `symbol`
- `timeframe`
- `confidence` (0–100)
- `trend_bias`
- `market_structure`
- `setup`
- `reasoning`
- `validated_at`

An `actionable` result additionally requires:

- product-scoped `symbol`
- supported timeframe: `M1`, `M5`, `M15`, `H1`, `H4`, `D1`
- `BUY` or `SELL`
- valid positive entry zone
- valid positive stop loss on the protective side of the entry zone
- one to three unique positive targets on the profit side of the entry zone
- confidence 0–100
- risk: `low`, `medium`, or `high`
- non-empty trend/bias, market structure, setup, and reasoning
- positive persisted `signal_id`

`low_confidence`, `unknown`, and `unsupported` results must not carry `signal_id`, direction, entry zone, stop loss, or targets. Android rejects such contradictory payloads rather than exposing an action.

## Failure behavior

- `403`: entitlement required
- `410`: job expired
- `413`: image too large
- `415`: unsupported image media
- `422`: server validation rejected the image/request
- `429`: rate limited

Failed and expired jobs do not become successful locally. The user may resubmit the prepared image or choose a new one.

## Exit safety

- unclear/unsupported images have explicit non-actionable states
- low-confidence output has no execution CTA
- `done` without a validated structured result is treated as an error
- actionable output can only open the persisted Signal; AI Vision has no direct execution endpoint
