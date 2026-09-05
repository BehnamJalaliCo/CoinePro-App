# Phase 5 — app shell: motion, feedback, adaptive layout

## Done

| Item | Where | Note |
| --- | --- | --- |
| Springs for spatial motion | `CoineProMotionSpecs.fastSpatial / defaultSpatial / slowSpatial / defaultSpatialFor`; `AppNavigationMotion.kt` | The navigation slide (forward, back, and the predictive-back seek) is now a spring; the fades stay on the tween because an opacity has no momentum. Stiffness 380 / damping 0.8 for the default, 800 / 0.6 for a tap's response, 200 / no bounce for a large surface. |
| Predictive back | `AndroidManifest.xml` `android:enableOnBackInvokedCallback="true"` | The system back gesture previews the navigation transition above on Android 14+; every `BackHandler` in the app already goes through `OnBackPressedDispatcher`, which is what the flag routes. |
| Skeletons, not spinners | `MarketsScreen` (catalogue and figures), `SearchScreen`, `SignalsScreen` | `CoineProSkeletonRows` — the shape of the list that is coming, staggered in — replaces the three remaining gold spinners on list screens. Home, news, DOM and portfolio already used skeletons or cards. |
| Haptics | Phase 4 added the magnet snap and the crosshair-over-a-level ticks; timeframe chips, the toolbar, the object tree and order actions already tick or commit through `CoineProHaptics` (23 files). | — |
| Adaptive layout | `CoineProListDetail` (markets → chart and watchlist → chart go two-pane on a wide window), `ChartPaneGrid` (columns by width), `coineProWindowClass()` | Already present; not changed. |
| Dynamic colour off | `CoineProTheme` builds its palette from `CoineProPalette`; no `dynamic*ColorScheme` anywhere. Green-up / red-up is the reader's `MarketColorScheme`. | Already the case. |
| Widget configure | `WidgetConfigureActivity`, `widget_markets_info.xml` `android:configure`, `WidgetSnapshotStore.preferredListId` | Placing the widget asks which watchlist it follows (starred first); the choice is stored once for all widgets — there is one snapshot — and a refresh is queued so the new widget does not sit empty for half an hour. Tap-to-chart and WorkManager refresh with backoff were already there. |

## Not done, and why

| Item | Reason |
| --- | --- |
| `MaterialTheme(motionScheme = expressive)` | In the Material 3 this app builds against (1.4.0 in the offline cache) `MaterialExpressiveTheme`, `MotionScheme.expressive()` and the opt-in annotation are all `internal`. The springs are therefore ours (`CoineProMotionSpecs`) and applied where we animate; Material's own components keep their defaults until the API is public. The switch is one line in `CoineProTheme`, noted there. |
| Shared elements on signal → execution and heatmap → symbol | `Modifier.sharedElement` exists and the markets and explore rows use it for the logo and ticker. The signal card and heatmap tile are separate destinations without a shared key today; wiring them means keys on both ends and a `SharedTransitionLayout` around the nav host, which is a change to every route's enter/exit and was not done in passing. |
| Medium / SemiBold weights, a Latin number companion | IRANYekanX ships in Regular and Bold here; the extra weights are the owner's licence to obtain. Tabular Latin digits are already in the font (Phase 0 gate), so no companion face is needed for alignment. |
| Screenshot matrix (fa × en × light × dark × phone × tablet, font scale 1.3) | Phase 6. |
