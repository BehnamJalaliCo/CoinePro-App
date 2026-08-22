# CoinePro Delivery Checklist

Use this as the execution checklist beside `PRODUCT_ROADMAP.md`.

## Global gates for every phase

- [ ] Backend/API contract documented before UI integration
- [ ] Loading / empty / error / offline states implemented
- [ ] RTL layout and LTR financial values verified
- [ ] Security and logging implications reviewed
- [ ] Unit/UI tests added for critical paths
- [ ] CI green
- [ ] No fake realtime state or fake AI progress

## Phase progress

- [x] Phase 0 — Foundation bootstrap
- [x] Phase 1A — Design Direction locked
- [x] Phase 1B — Initial `core:designsystem` tokens/theme
- [ ] Phase 1C — Architecture skeleton modules + navigation shell
- [ ] Phase 2 — Authentication / Session / Entitlements
- [ ] Phase 3 — Realtime Market Data Foundation
- [ ] Phase 4 — Signals Core
- [ ] Phase 5 — Alerts & Push
- [ ] Phase 6 — Connections & Signal Execution Bridge
- [ ] Phase 7 — AI Generated Market Signal
- [ ] Phase 8 — AI Vision Flagship
- [ ] Phase 9 — AI Assistant
- [ ] Phase 10 — News & Economic Calendar
- [ ] Phase 11 — Trader Tools
- [ ] Phase 12 — Activity / History / Performance
- [ ] Phase 13 — Offline / Reliability / Background Work
- [ ] Phase 14 — Security Hardening
- [ ] Phase 15 — Quality / Performance / Accessibility
- [ ] Phase 16 — Release Engineering
- [ ] Phase 17 — Launch Readiness

## Current next milestone

Phase 1C is next. It should establish the module boundaries, five-destination navigation shell, shared result/error types, network abstraction, persistence boundaries, and financial formatting helpers before any production feature integration begins.
