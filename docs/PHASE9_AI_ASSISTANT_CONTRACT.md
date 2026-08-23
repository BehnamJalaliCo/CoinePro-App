# Phase 9 — AI Assistant Contract

## Trust boundary

The AI Assistant is contextual guidance, not an execution surface. Android never derives active positions, active signals, order state, trade levels, or executable actions from assistant prose.

Any market fact shown as verified context must come from the structured context snapshot returned by the authenticated CoinePro API. Assistant text may explain that context, but it does not mutate application truth.

## Authenticated API

`POST /user/ai/assistant/messages`

Request:

- optional `conversation_id`
- `message` (trimmed, non-empty, maximum 4,000 characters)
- requested context scopes:
  - `active_signals`
  - `market`
  - `news`
  - `calendar`
  - `risk`
  - `tools`

Response:

- conversation metadata
- one assistant reply
- zero or more structured context items attached to that reply

The server validates authentication, entitlement, conversation ownership and all supplied context.

Once Android has an active `conversation_id`, a later response must keep the same identifier. An unexpected conversation-id change is rejected and never appended as a trusted assistant reply.

## Structured context

Recognized context kinds:

- `active_signal`
- `market`
- `news`
- `calendar`
- `risk`
- `tool`

Every displayed context item requires a non-empty title. Context may also carry:

- summary
- source
- `as_of`
- freshness: `fresh`, `stale`, or `unknown`

An `active_signal` context item additionally requires a positive persisted `signal_id`. Other context kinds are rejected if they try to carry a `signal_id`.

Android may offer `Open verified Signal` only for that structured positive `signal_id`; the assistant itself has no direct execution route.

## Freshness and provenance

Freshness is never inferred from assistant wording. Unknown or future freshness values degrade to `UNKNOWN` rather than being shown as fresh.

A context item reported as `fresh` is displayed as `FRESH` only when both a non-empty `source` and `as_of` timestamp are present. A reported fresh item with missing provenance is downgraded to `UNKNOWN`.

Source and `as_of` are displayed when supplied. Missing provenance is never fabricated by Android.

## Conversation history policy

Phase 9 Android policy:

- assistant transcript is held in memory only
- no assistant transcript is written to Android persistent storage
- logout/session loss clears the in-memory conversation
- `New chat` clears the current in-memory conversation

The server reports its own conversation history policy as one of:

- `ephemeral`
- `account`
- unknown/unreported

If the server declares account history, it may also return positive `retention_days`. Android displays that server policy but does not silently create a second local history store.

## Failure behavior

- `403`: entitlement required
- `422`: request rejected by server validation
- `429`: rate limited
- unexpected conversation identity change: turn rejected and explicit error shown
- network/other failures: message remains visible as the user's attempted turn and an explicit error is shown; no fake assistant reply is inserted

## Exit safety

- active signals/positions are never created from prose
- context freshness/source is visible and `FRESH` requires provenance
- assistant has no direct execution endpoint
- transcript persistence policy is explicit
- conversation identity cannot silently switch mid-chat
- session loss removes the local transcript
