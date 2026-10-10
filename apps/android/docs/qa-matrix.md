# Android QA Matrix

## Scope

This matrix covers transport reliability and startup-path parity for Android websocket + bridge flows.

## Desktop Connect reliability closure — 2026-09-30

- Source changes now cover the shared reconnect owner, route-scoped actions, true 10-row recent paging/project routes, host/session drafts, protected pending journals and exact admission receipts, message-tail/staged gap fill, source-aware real permissions/questions/stop, desktop-visible models and shared NE ASR. CLI QR connection is unchanged.
- Current regression evidence: shared actual-source Rust harness 35 passed; host 11 passed/1 environment-dependent sidecar test ignored; backend 13 passed/119 assertions and package typecheck exit 0. Android production/androidTest Kotlin compilation and 9 Desktop unit tests passed after the final native changes and regenerated bindings (46s). The real Host/local Connect controller integration passed (1 test/66 assertions). Final Android native library build and APK assembleDebug passed (49s); APK signature, package/arm64 ABI and provenance from latest JNI through merged/stripped native libraries were verified. Artifact: `D:\project\litter-mobile-desktop-connect\apps\android\app\build\outputs\apk\debug\app-debug.apk` (2026-09-30 20:45:09), SHA256 `46D29D81F908F40237FA0E456AA0C39DBA0F518F7AFEF1FE49E44A97213E0CBE`.
- Device acceptance checklist (not yet run): initial connection; timeout then foreground recovery without app restart; switch hosts during slow requests; disconnect must not auto-connect; recent first page exactly 10 + load more; fixed project entry and project-specific Back; rapid project/new-session actions; rotation keeps the route; draft/attachment/recording owner after navigation; only enabled usable models; image+text; NE ASR; lost prompt reply exact retry/no duplicate; live stop/permission/question; large disconnect gap; compact tools and Markdown lists; warm/light layout and large fonts.
- No new APK installed, no device connected for this pass; instrumentation has only compiled. iOS sources/bindings stay in parity, but Windows has no Xcode. Build/test success is not visual, ANR or public-network latency acceptance.

## Desktop Connect UI validation (historical checks)
- 2026-09-29: Android/iOS connected-session header now separates the page title and refresh action from a full-width current-computer selector. The selector reserves space for long host names and exposes status and switching in one touch target; the existing warm palette, project grouping, and CLI QR flow are unchanged. Android `:app:compileDebugKotlin --offline` and `:app:assembleDebug --offline` passed. No live desktop account was used on the emulator for this pass, so the connected full-screen light/dark rendering, narrow-width and large-font behavior, host switching, and message detail remain **unverified**; iOS still needs Xcode compilation and device inspection. Do not treat this source/build check as product-level visual acceptance.

- Latest host picker pass: Android and iOS now show a flat computer list rather than nested bordered cards. The current computer stays first with a subtle warm highlight; other computers are separated by simple dividers, and a section label appears only when switching among multiple computers. Single-computer selection no longer repeats an online counter or a section heading. Existing theme tokens, inline status, actions, account footer, and CLI QR entry remain unchanged. Android focused instrumentation (6 cases) passed on the 4 KB emulator at 1.0x and 1.4x font size; the emulator font scale was restored to 1.0. The emulator's launcher captures normally, but the foreground App screenshots are black despite a populated accessibility tree, so the complete App/sheet has **not** received visual sign-off. Real login/switch flows and iOS compile/device rendering are also unverified.
- Connected home and conversation headers now show the same compact desktop selector with an explicit healthy/disconnected status; the redundant 76dp host card was removed from the list. An empty message result after a transport error shows a retry instead of falsely inviting a new conversation. Android `:app:compileDebugKotlin --offline` passed after the change; iOS still requires compilation and a real-device rendering pass.

- Desktop session browse now follows the CLI workspace hierarchy: Android and iOS group sessions by project (most recently active project first), allow collapsing projects, and automatically expose matching rows while searching or filtering. The active desktop selector, project filter, and new-session action remain at the top. Android `:app:compileDebugKotlin --offline` passed; iOS is a source-only change on Windows. Real connected list rendering, long titles and Dynamic Type remain unverified.

- Earlier host picker hierarchy pass (superseded by the flat-list pass above): connected-host switching separated **当前电脑** in an accent-outlined warm surface from **其他电脑** in a neutral grouped list; first-time selection has **你的电脑**. Both platforms retain the account/status/action semantics and the existing palette. Android focused instrumentation was red before the section labels were added and passed afterward, including a 320dp / 1.3x-font run on `emulator-5564`; the emulator was restored to 1080×2400 and 1.0x font. This is semantics/layout coverage, **not** a visual sign-off. iOS could not be compiled on Windows. Install a fresh app build and inspect the complete light/dark sheets, long names, multi-host scrolling, reconnect/error states, and iOS Dynamic Type before release.

- Android: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:assembleDebug` passed offline. The APK was installed only on the `codex-ui-4k` emulator; the login bottom sheet was visually inspected at standard font size in both light and dark mode, and earlier at 1.3x font size / 320dp width. No user device was touched.
- Host selection and switching now share the same host-row visual hierarchy on Android and iOS: active host first, available hosts next, unavailable hosts last; active action remains visible during loading, while unavailable hosts have no action. Theme colors are unchanged.
- **Not verified:** No NE account/online desktop is available to this emulator. Multi-host ordering, offline/busy status, switch feedback, connected-session navigation, dark-mode and large-font host rows must be checked with live data. A successful build or a login screenshot is not a substitute for this check.
- Android host-tile visual fixture (`DesktopHostTileVisualTest`) passed on the `codex-ui-4k` emulator with sample current/available/busy/offline hosts. At 320dp and 1.3x font, the inline action cramped the host name; the tile now places the action below the host identity in compact/large-font layouts. Host rows are grouped in one surface with inset separators instead of separate outlined cards. Standard-width behavior and real sheet navigation still need a live-connection check. The fixture now checks semantics and layout visibility without shipping or storing screenshot artifacts.
- The logged-in host-picker header is now title-first, without redundant explanatory copy; the online-count heading appears only for multiple hosts, and the grouped host surface no longer has an extra outline. These changes are in both Android and iOS selection/switching sheets, but the **full real sheet** still needs a live visual check.
- Host status now has an explicit semantic chip (connected/available/busy/offline), with icon color following actual health rather than highlighting a disconnected current host. On the `codex-ui-4k` emulator, the focused host-row instrumentation test passed at a 320dp-wide window and 1.3x font scale; this fixture does **not** render the real bottom sheet and is not visual sign-off. A broad instrumentation run was stopped after its host-row test passed because the unrelated `NativeContextInitTest` did not finish; it is not counted as a passing suite.
- Full Android host-picker fixture: at 320dp / 1.3x font, the account identifier and logout remain visible before and after scrolling to the offline host. It uses sample hosts, not a live account. The emulator renders the Compose test activity black in both `screencap` and `UiAutomation` capture (status/navigation bars are visible), although the Compose visibility assertions pass. **Do not count those black captures as a visual review of the complete sheet.** The login sheet was reviewed separately, not this host picker. Current-host and multi-host appearance in light/dark mode remain pending on a device that can render the sheet.
- Logged-in picker footer now identifies the actual NE account on Android and iOS instead of only saying it is logged in; Android's focused 320dp test exercises the label. No credential/token is displayed.
- **iOS not built:** Windows has no Apple SDK here. The SwiftUI tile now uses a fitting horizontal/stacked layout, but it still requires a macOS build and device/simulator inspection, including Dynamic Type and sheet detents.

## Automated Regression Scaffolding

Run unit tests for both runtime flavors:

```bash
./gradlew :app:testOnDeviceDebugUnitTest
./gradlew :app:testRemoteOnlyDebugUnitTest
```

Current automated checks:

- `RuntimeFlavorConfigTest`
  - validates startup mode/build config parity (`ENABLE_ON_DEVICE_BRIDGE`, `RUNTIME_STARTUP_MODE`)
  - validates canonical app runtime transport declaration (`APP_RUNTIME_TRANSPORT`)
- `BridgeTransportReliabilityPolicyTest`
  - validates reconnect detection policy for healthy/stale websocket state
- `CodexRuntimeStartupPolicyTest`
  - validates startup toggle parsing and precedence logic
- `ThreadPlaceholderPrunePolicyTest`
  - validates placeholder prune-on-refresh behavior (including active-thread exemption)

## Manual Matrix

| Area | onDevice flavor | remoteOnly flavor |
|---|---|---|
| App launch | App launches and can start local bridge-backed session | App launches and does not auto-start local bridge |
| Connect local/on-device | Success (`ServerConfig.local`) | Expected failure with clear "disabled" error |
| Connect remote server | Success | Success |
| SSH-discovered remote server | Prompts for SSH credentials, connects through SSH port forwarding, and never attempts `ws://host:22` directly | Same |
| Local transport drop | Reconnect and one-time reinitialize before next non-initialize RPC | N/A (local startup disabled) |
| Remote transport drop | Reconnect behavior via Rust `AppStore` updates and resumed RPC notifications | Same |
| Thread start/resume fallback sandbox | `workspace-write` with `danger-full-access` fallback when linux sandbox missing | Same |
| Thread turn pagination (v0.125+ remote) | Conversation opens with last 5 turns; "Load earlier messages" button fetches older 5-turn pages via `thread/turns/list` | Same |
| Thread turn pagination fallback (v0.124 remote) | Capability flips off via response inspection; embedded turns load fully; "Load earlier" button hidden | Same |

## NeCode Desktop Connect (pending device QA)

The NE login/Connect host picker is separate from the existing CLI QR pairing flow. Verify on iOS and Android with a deployed HTTPS Connect service:

- Sign in with NE account/password (no SMS code), restore identity after restart, list two desktops, show offline/busy, and select one available host.
- Open projects and sessions, create a session, send/read a message; verify no sidecar password is exposed to mobile.
- Use two phones: the second cannot acquire a busy desktop; the first phone cannot connect to a second desktop before disconnecting.
- Disconnect and switch host. Force-stop and reopen within the 60-second lease: the same saved mobile identity should resume the original host without scanning or choosing again. After expiry, show available hosts instead. Re-login to the same account/device should also resume; a different device must not take over.
- Expire a lease, log out, and verify cross-account isolation.
- Recheck CLI QR pairing independently. Neither platform has completed this device matrix yet.
- UI verification pending: install the freshly rebuilt Android APK and check both host pickers now use a grouped warm-surface computer list with subtle separators (current row highlighted, online status and clear primary action); compare against the old flat list and stacked-card versions rather than trusting a build; verify multi-host scrolling, busy/offline/error states, narrow screens, large font, light/dark themes and keyboard avoidance. In an interrupted connection, “重连” must attempt a bridge connection instead of dismissing; failure must stay visible and success must reopen the selected host sessions. Check the iOS custom back button, disconnected CTA, and right-aligned user messages against the Android conversation. iOS SwiftUI build and device visual check still require Xcode/macOS.
- Picker parity pass: initial selection and connected-host switching both show compact loading feedback; unavailable hosts show status without a redundant disabled action pill. Validate grouped-row layout at 320dp/large fonts and the 48dp Android header touch targets on device; no visual pass has been claimed.
- Current UI code pass: the host list now keeps a compact loading placeholder while searching; session detail shows a project chip instead of a raw path, and Android applies IME insets to keep the composer above the keyboard. Recheck these on a freshly installed APK with a narrow screen and large system font; SwiftUI remains uncompiled locally.
- Conversation polish pass: user messages now use compact right-aligned bubbles without repeated role labels; assistant Markdown remains full-width with a small NeCode identity row. Both host pickers cap their scroll content to the viewport, and picker errors remain in the picker until dismissed. Android focused tests, Kotlin compile, and APK assembly passed; iOS syntax was reviewed but Xcode/device rendering is unavailable here. Do not treat this as a visual sign-off: compare the login/host-switch sheets and conversation at small/large text sizes on real devices before release.
- Host row hierarchy pass: the current computer now uses a filled primary action in both host pickers, while other available computers retain a lighter connect affordance and unavailable computers remain status-only. Host names can wrap to two lines instead of crowding the action on narrow screens. Android Kotlin compile, focused unit tests, and APK assembly pass; verify actual rendering, contrast, text scaling, and current/reconnect states on Android and iOS devices before calling this product-ready.
- Host switch sheet layout pass: Android keeps the title, refresh, error, and disconnect control outside the scrolling host list; iOS keeps its existing fixed controls, omits an empty list card, and allows expansion to a large detent when text or host count grows. The list itself is scrollable. Verify on-device that the sheet remains usable at large font sizes, with many hosts and a visible error; iOS is still not compiled on Windows.
- Initial desktop connect sheet layout pass: Android and iOS now keep the title, refresh control, loading indicator, and error above a separately scrollable login/host-selection area. Android keeps the sheet above the IME and disables close during restore/connect; iOS offers a large detent for multi-host selection. Android focused tests, Kotlin compile, and APK assembly pass. No fresh device rendering or iOS compilation has occurred; verify login with keyboard open, long error text, host counts, and dynamic text sizes before release.
- Computer picker row parity pass (Android/iOS): current host identity and connection availability are now separate lines, with a small semantic-color status badge and the existing primary continue/reconnect action. Offline current host says "上次连接" rather than implying a live connection. Android focused instrumentation: 7/7 on the default 1080x2400 / 1.0 font emulator and 7/7 at 720x1600 / 1.4 font; debug APK builds. The Android emulator still shows an all-black App screenshot despite a live Compose accessibility tree, whether captured by adb screencap or emulator console, and changing HWUI renderer/overlays did not restore pixels. Do not treat instrumented layout assertions as full-page visual sign-off; real on-device picker, connected-home, session-list and detail checks remain. iOS row parity was edited on Windows and still needs a macOS Xcode build and device review.
- Android emulator visual QA attempt (Pixel_7, API 37 16 KB page-size image): the debug APK installs with downgrade allowed but the app exits on launch before the connect sheet. Logcat reports `libjnidispatch.so` program alignment 8192 below system page size 16384, then JNA native load fails. This is a simulator/runtime packaging blocker, not evidence of UI correctness or incorrectness. Recheck on a 4 KB emulator or device before visual sign-off; do not extrapolate from this build.
- Message reading regression pending on both devices: open a long conversation, scroll to an older message, then receive both a new message and more text within the existing assistant message; reading position must stay put. At the bottom, both kinds of updates should follow the *end* of long assistant text, not the beginning of its row. After sending on mobile, the newest message should be visible. Reopen another conversation and verify its first load starts at the latest message. A build alone cannot establish this interaction or visual quality.

## Terminal UX Matrix

The terminal screen renders through Ghostty on both platforms; this section
tracks parity between iOS (UIKit + Metal) and Android (Compose + SurfaceView).

| Area | iOS | Android |
|---|---|---|
| Full-screen surface | No bottom composer; the Ghostty surface fills the body | Same |
| Accessory bar | Esc/Tab/Ctrl/arrows/Paste/Clear/Send-to-AI dock above the keyboard via `inputAccessoryView` | Compose row anchored above the IME using `imePadding` |
| Tap-to-toggle keyboard | Single tap (no selection, no link) toggles the keyboard | Same |
| Long-press selection | Long-press seeds word selection; drag extends; handles paint via `TerminalSelectionOverlayView` | Long-press seeds word selection; drag extends; handles painted by a sibling Compose Canvas |
| Edit menu (Copy / Paste / Select All) | `UIEditMenuInteraction` anchored at the selection union rect | Compose floating action menu above the selection |
| Pinch-to-zoom font | `UIPinchGestureRecognizer` clamps to 10–24 pt and re-grids on settle | `ScaleGestureDetector` updates `TerminalConfigPrefs.fontSize` live and re-grids on scale-end |
| BEL haptic | `UIImpactFeedbackGenerator(.medium)` + system sound, throttled 250 ms | `performHapticFeedback(LONG_PRESS, IGNORE_VIEW_SETTING)`, throttled 250 ms |
| OSC8 hyperlink tap | Detected through Rust `linkAtPoint`; opens via `UIApplication.shared.open` | Detected through Rust `linkAtPoint`; opens via `Intent.ACTION_VIEW` |
| Cell-grid math | Driven by Ghostty `surfaceMetrics`; falls back to font-size-aware estimate on first frame | Same path via `nativeSurfaceSize` |
| Resize on rotation / keyboard show-hide | `layoutSubviews` plus `UIResponder.keyboardWillChangeFrame` triggers | `onSizeChanged` re-fires through Compose's `imePadding` insets |
| Mouse-tracking apps (vim / htop) | Single-finger drag forwards to Ghostty when `mouseCaptured` | Same |
| Alleycat remote host | Discovery toolbar QR button opens `AlleycatAddServerSheet`; CameraX + ML Kit scan parses the Alleycat payload via `AlleycatBridge.parsePairPayload`; debug builds expose paste-JSON path; after token-authenticated pairing the sheet calls `serverBridge.listAlleycatAgents`, lets the user choose Codex/Pi/OpenCode, connects with `serverBridge.connectRemoteOverAlleycat`, and persists the token through `AlleycatCredentialStore`; `SavedServerStore.rememberAlleycat` writes `{node_id, relay?, agent}` records, reconnect attaches the encrypted-store token directly, and legacy Alleycat records require a new QR scan. | Same |

## Plugin `@`-mention parity (follow-up)

iOS ships `@plugin` mentions in the composer: typing `@` now lists installed
Codex plugins above the file results, selecting one inserts an `@<name>`
chip and emits an `AppUserInput.Mention { name, path }` item on
`turn/start`. Path encoding lives in shared Rust (`PluginSummary` UniFFI
record + `list_plugins` on `AppClient`).

Android does not have an `@`-trigger composer popup yet (no inline
`@<file>` autocomplete either), so plugin mentions are a follow-up. The
shared Rust client and the `AppUserInput.Mention` variant are already
wired through the Kotlin bindings, so the platform side just needs a
composer popup, chip row, and send-time append. Track this alongside the
broader composer autocomplete work.

## Suggested Smoke Steps

1. `onDeviceDebug`: connect local default server, start thread, send turn, toggle network off/on, send another turn.
2. `onDeviceDebug`: kill local bridge process (or force stop app), relaunch, confirm initialize and thread list recover.
3. `remoteOnlyDebug`: attempt local connect path, verify explicit disabled error; connect remote server and run thread/list + turn/start.
4. Both flavors: verify account read/login status refresh still updates UI after reconnect.

## Thinking-indicator Minigame (iOS + Android)

Gated by Settings → Experimental → "Thinking minigame" (off by default on
both platforms). The shimmering "Thinking..." indicator becomes tappable
while the assistant is generating; tapping it spins up an ephemeral thread
on `gpt-5.3-codex-spark` (low reasoning, fast tier) and renders the
returned `show_widget` HTML in a bottom-40% overlay that hides the
composer.

| Check | iOS | Android |
|---|---|---|
| Indicator is non-interactive when flag off | "Thinking" shimmer has no tap effect | Shimmer has no clickable ripple |
| Tap while thinking opens overlay immediately | Slides in with skeleton | Slides in with skeleton |
| Skeleton swaps to rendered widget on completion | WidgetWebView (no zoom, theme-injected) | MinigameWebView (no zoom, themed) |
| Composer is hidden while overlay is up | `ConversationBottomChrome` returns `EmptyView` from `safeAreaInset` | Composer `Column` is omitted |
| Last message is not occluded | safeAreaInset reserves overlay height in scroll inset | LazyColumn has trailing spacer / overlay sits over nav bar inset |
| Close (X) restores composer + idles overlay | `dismissMinigame()` cancels in-flight task | `dismissMinigame()` cancels coroutine |
| Repeat tap on a fresh assistant turn generates a new game | New ephemeral thread per request | New ephemeral thread per request |
| Bridge globals stubbed in minigame mode | `WKUserScript` at `.atDocumentStart` no-ops `sendPrompt`/`saveAppState`/`loadAppState`/`structuredResponse`, and the matching `WKScriptMessageHandler` registrations are skipped | `evaluateJavascript` in `onPageStarted` injects stubs; the matching `@JavascriptInterface` registrations are skipped (`WidgetBridge` for openLink/height/ready only) |
| Light + dark mode rendering | Widget uses host CSS variables that adapt automatically | Same |
| Minigame is NOT saved as a regular widget | Waiter intercepts `show_widget` so `auto_upsert_saved_app` is skipped, and `start_minigame` does not call `saved_app_upsert` itself | Same — single shared Rust path |
| Ephemeral thread is torn down | `thread/archive` after waiter resolves or times out | Same |
| Generation timeout (~30s) | Overlay shows failure card with "Try again" | Overlay shows failure card with "Try again" |

## Settings — Server Connection Editor (iOS + Android)

Tapping a saved server row in Settings opens the inline editor. Local servers are
name-only; alleycat-paired servers are name-only; everything else allows
mode + host/port/wake-MAC + URL editing. Save persists, Save & Reconnect
disconnects and re-establishes the chosen transport.

| Check | iOS | Android |
|---|---|---|
| Tap saved server row opens editor | `SettingsServerSheet.edit` opens `SettingsServerConnectionEditor` form sheet | Tap or row-menu "Edit" opens `ServerEditSheet` ModalBottomSheet |
| Local server: only name editable | Editor displays "managed automatically" copy; only Save & Restart action shown | Same — `ServerEditSheet` shows the same copy and uses "Save & Restart" |
| Alleycat-paired server: only name editable | Editor shows paired-pairing-metadata copy; mode picker hidden | Same — mode picker hidden, message visible |
| Switch to Direct Codex + Save & Reconnect | Persists, disconnects, calls `serverBridge.connectRemoteServer` | Same — `reconnectController.reconnectServer` reconnects via persisted record |
| Switch to WebSocket + Save & Reconnect | Persists, disconnects, calls `serverBridge.connectRemoteUrlServer` with `ws://` or `wss://` | Same — record stores `websocketURL`; reconnect dispatches via Rust `ReconnectController` |
| Switch to SSH + Save & Reconnect | Persists, dismisses editor, opens `SSHLoginSheet`; connect uses `serverBridge.startRemoteOverSshConnect` | Persists, dismisses editor, opens shared `SSHLoginDialog`; connect uses `serverBridge.startRemoteOverSshConnect` |
| Validation errors surface inline | Alert "Invalid Server" with localized reason, dismiss returns to editor | Same — `AlertDialog` with reason; dismiss returns to editor |
| Save (no reconnect) | Persists `SavedServerStore` + calls `store.renameServer`, leaves connection intact | Same |
| Remove server | Awaits bridge disconnect, then removes `SavedServerStore`, deletes the saved Alleycat token, closes SSH state, and refreshes the projection | Same |

## Sidebar + Picker Parity Checklist (iOS + Android)

### Session Sidebar

- Sidebar stays unmounted while closed; local UI controls persist when reopened.
- Search + server filter + forks filter produce stable grouping and lineage chips.
- Opening/closing sidebar does not trigger excessive recomposition/signpost churn in idle state.

### Thread List Consistency

- Refresh (`thread/list`) prunes non-authoritative placeholder threads unless they are currently active.
- Notification-only placeholder rows disappear on next refresh once inactive.
- No regressions in thread switching, forking, or session search after placeholder pruning.

### Directory Picker

- Primary action: one-tap `Continue in <last folder>` appears when recents exist.
- Top controls remain visible while list scrolls: connected server chip/status + search.
- Breadcrumb + `Up one level` navigation always reflects current path.
- Bottom CTA is sticky and mirrors path state: `Select <path>` (or disabled helper text).
- Error state exposes both `Retry` and `Change server`.
- `Clear recent directories` requires destructive confirmation.
- Back behavior parity:
  - Android: `Back` navigates up before dismissing sheet.
- iOS: dismiss is blocked while not at root; cancel navigates up first.

### Appearance

- Chat wallpaper can be chosen from the photo library, persists across relaunch, and can be removed from Settings.
- Conversation screen and appearance preview both render the selected wallpaper instead of the fallback theme gradient.

### Conversation Selection

- Settled assistant markdown supports long-press selection/copy.
- Reasoning text supports long-press selection/copy.
- Command output supports selection/copy and still scrolls vertically.
- Error text supports selection/copy.
- Code blocks support selection/copy and still scroll horizontally.
- Markdown links remain tappable after selection support changes.

## Home Dashboard Zoom + Swipe Reply Parity (mirrors iOS b96961b3 + 52ff299d)

Ported in parallel with the iOS "new ui" and "new ui stuff" commits. Each
item must render identically to the iOS `HomeDashboardView` on zoom-1/2/3/4
for the matching state.

| Feature | Check |
|---|---|
| Zoom toolbar button | Top-right of header cycles 1→2→3→4→3→2→1 with icon matching level (ViewQuilt→ViewList→ViewAgenda→ViewStream). Persists across app restart via `DashboardZoomPrefs` SharedPreferences. |
| Pinch-to-zoom | Pinch on home LazyColumn crosses level thresholds (`pinchAccumulator ± 0.4 → 1 level`), emits haptic on transition, does not steal single-finger vertical scroll. |
| Zoom 1 (scan) | Title + StatusDot only; no meta/body/chips. |
| Zoom 2 (glance) | + time · server · workspace meta line. Tool-activity label + pulsing dots appear only when `isActive && isToolCallRunning(hydratedItems)` — pure thinking falls through to server metadata. |
| Zoom 3 (read) | + modelBadgeLine (server icon + server + model + fork/subagent) with trailing inline stats, + user-message quote `>` prefix, + compact tool log (1 row), + response preview capped at 25% screen. |
| Zoom 4 (deep) | Tool log expands to 3 rows; response preview cap rises to 50% screen; preview scroll-anchors to bottom when overflowing. |
| Response preview crossfade | New assistant-block id flip triggers Crossfade on the preview; preserved on empty new-turn assistant items via `displayedAssistantMessage` walking back to last non-empty. |
| TurnStopwatchChip | Live 1Hz tick while turn active (end=null) via `produceState` + `delay(1000)`; static elapsed when ended. Format `<60s → "Xs"`, `<3600s → "Xm" or "XmYs"`. |
| Tool log grouping | Consecutive exploration commands (read/search/listFiles `HydratedCommandActionKind`) collapse into `⌕ Explored N files, M searches, K listings` summary row; other tool kinds render as single-line rows with `toolIconForName` glyph. |
| inlineStats chips | Turn count, tool count, diff `+N/-N`, TurnStopwatchChip, token % (warning tint ≥80%). Left text truncates first; chips stay pinned. |
| recentUserMessage | `>` chip prefix + FormattedText at `LitterFont.conversationBodyPointSize × textScale`. Only shown when message exists and differs from title. |
| StatusDot shimmer | Active state gets both the 800ms alpha pulse AND a 2s linear-gradient sweep overlay. |
| Home hydration | Home list calls `appModel.externalResumeThread(session.key)` — not `client.readThread` — so the server attaches a live listener and cards update without opening the thread. |
| Swipe reply | Right-swipe on home row reveals reply affordance (`SessionReplySwipe` via `SwipeableRow.leadingAction`); past commit threshold opens `QuickReplySheet` modal; send path resumes the thread before `startTurn` to avoid "thread cannot be found" on cold launches. Left-swipe reveals hide (trailingAction). |
| SavedProjectStore | Last-selected server + project persist across app restart via Rust `preferencesSetHomeSelection` / `HomeSelection`. Wired through `LitterApp.kt`. |
| StreamingMarkdownView bodySize | Optional `bodySize` parameter thread through to TextView font size; opt-in by response preview and by direct consumers that need parametric sizing. |

## Tool Call Card Parity Matrix (iOS + Android)

Renderer contract for this release:

- default collapsed for tool cards, except `failed` cards (default expanded)
- header order: icon, summary/title, spacer, status chip, optional duration chip, chevron
- section order: metadata KV, payload sections (`Command/Arguments/Result/Output/Action`), auxiliary sections (`Prompt/Targets/Progress`)
- parse miss fallback: legacy markdown rendering unchanged

| Tool kind | Summary rule | Status chip | Expected sections |
|---|---|---|---|
| Command Execution | stripped command + status/duration suffix | `inProgress`/`completed`/`failed`/`unknown` | Metadata, Command, Output (if present), Progress (if present) |
| Command Output | output label fallback (`Command Output`) when no command | usually `unknown` | Output text/code |
| File Change | first basename + `+N files` | normalized from `Status:` | Metadata, repeated `Change N` metadata + diff/text content |
| File Diff | first path basename when available, else `File Diff` | usually `unknown` | Diff panel |
| MCP Tool Call | `Tool:` value + status suffix/check | normalized from `Status:` | Metadata, Arguments/Result, Error/Progress as available |
| MCP Tool Progress | tool/status fallback or title | usually `unknown` unless merged into MCP call | Progress timeline text |
| Web Search | `Query:` value | usually `unknown` | Metadata, Action JSON |
| Collaboration | `Tool:` value fallback | normalized from `Status:` | Metadata, Prompt text, Targets list |
| Image View | basename from `Path:` | usually `unknown` | Metadata (`Path`) |

Status normalization parity:

- `inProgress`, `in progress`, `running`, `pending`, `started` -> in progress (amber)
- `completed`, `complete`, `success`, `ok`, `done` -> completed (green)
- `failed`, `failure`, `error`, `denied`, `cancelled`, `aborted` -> failed (red)
- anything else/missing -> unknown (neutral)

## Saved Apps (Generative UI persistent apps)

Generative UI is permanent (no flag). Local-server threads register `show_widget` / `visualize_read_me`; remote-server threads do not. Saved apps are created automatically whenever a local-server model finalizes a `show_widget` with an `app_id` slug — there is no manual "Save as App" button.

| Area | Check |
|---|---|
| Bootstrap | `AppClient.setSavedAppsDirectory(MobilePreferencesDirectory.path(context))` is called once in `AppModel.init`, before any thread starts. Without this, the Rust `show_widget` finalize hook is a silent no-op. |
| Auto-upsert | When the model finalizes a `show_widget` with `app_id = "fitness-tracker"` on a local-server thread, the Rust hook calls `saved_app_upsert(directory, originThreadId, appId, title, html, w, h, schema)` and writes to `{filesDir}/LitterPreferences/apps/saved_apps.json` + `html/<uuid>.html`. No Kotlin-initiated promote call is needed. |
| Saved-as chip | Finalized `WidgetRow` whose `HydratedWidgetData.appId` is non-null renders a compact "Saved as `<slug>`" chip below the WebView (11sp mono, accent slug). Tap resolves `SavedAppsStore.appForSlug(slug, threadId)` to a UUID and pushes `Route.SavedApp`. Chip is absent when `appId == null` or the widget isn't finalized. |
| Home-row takeover | `HomeDashboardScreen` keeps a `savedAppsByThread` map keyed by `originThreadId`, reloaded via `SavedAppsStore.reload` on every snapshot tick (MVP coarse reactivity; R3 will supply a `SavedAppsChanged` stream). When a session's threadId has entries, its row renders `HomeAppTakeoverRow` (monogram + title + slug subtitle + "+N more" when there are siblings) instead of `SessionCanvasRow`. Tap navigates to `Route.SavedApp(mostRecent.id)`. Swipe-to-hide on the session still works. |
| Apps list entry | Settings sheet "Apps → Saved Apps" row is always visible (no flag gate). Pushes `Route.Apps`. |
| Apps list | `AppsListScreen` renders apps newest-updated-first: monogram tile + title + relative timestamp. Swipe-to-dismiss cascades `savedAppDelete`. Empty state explains that saved apps are created automatically. |
| Detail relaunch | Tapping a row (or a Saved-as chip, or a home-row takeover) pushes `Route.SavedApp(uuid)`. `SavedAppScreen` calls `savedAppGet(dir, uuid)` on enter, hydrates the WebView with `wrapWidgetHtml(html, AppStateInjection(stateJson, schemaVersion))`, and registers `__LitterAppBridge` via `addJavascriptInterface`. |
| State persistence | `window.saveAppState(obj)` flows through the bridge into `SavedAppsStore.saveState`, 250 ms trailing-edge debounced per `appId`, landing in `savedAppSaveState`. `SavedAppException.StateTooLarge` logs + swallows. |
| Structured response | `window.structuredResponse({prompt, responseFormat})` returns a Promise that resolves with parsed JSON. Routes through `__LitterAppBridge.structuredResponse(requestId, prompt, schemaJson)` → `AppClient.structuredResponse` on an ephemeral hidden thread (`ThreadStartParams.ephemeral = true`). First call per view session creates the hidden thread; subsequent calls reuse it via `remember(appId) { mutableStateOf<String?>(null) }`. Reply flows back via `webView.evaluateJavascript("window.__resolveStructuredResponse(...)")`. Navigating away + returning resets the cache so a fresh ephemeral thread is created. The hidden thread never appears in the thread list (ephemeral threads are absent from `thread/list`). |
| Cold-launch persistence | Increment a counter in a saved app, `am force-stop`, relaunch, reopen the app. `loadAppState()` pulls `window._initialAppState` from the seeded state JSON — count survives. |
| Update flow | "Update" in the top bar opens `SavedAppUpdateOverlay`. Submit calls `SavedAppsStore.requestUpdate(...)` → `AppClient.updateSavedApp(...)`. WebView dims + shimmer overlay. On success: dismiss + detail re-fetch (state preserved because `replace_html` doesn't touch `state/<id>.json`). Failure: retry inline + toast. |
| Origin server routing | Update RPC prefers `originThreadId`'s server → active thread's server → any local server → any connected. No connected server → clear error message. |
| View Conversation | Top bar has a chat-bubble icon (`Icons.AutoMirrored.Filled.Chat`) that pushes `Route.Conversation(originThreadKey)`. Only rendered when `originThreadId` still resolves to a `ThreadKey` in the current snapshot — gone otherwise. |
| Rename / delete | Top bar title tap → rename dialog → `savedAppRename`. Overflow "Delete" → destructive confirmation → `savedAppDelete` → pop back to list. |
| Same slug in two threads | Model emitting `app_id = "fitness-tracker"` in two different origin threads creates two independent saved apps (distinct UUIDs, separate state files). The Apps list shows both; home-row takeover on each thread points at its own. |
| Regression: timeline widgets with no slug | A `show_widget` call that omits `app_id` (or is pre-R2) renders with the baseline `wrapWidgetHtml(html)` shell, does not trigger auto-save, and shows no Saved-as chip. |
| Regression: thread delete | Deleting an `originThreadId` thread does not affect saved apps; the `View Conversation` button becomes hidden for those apps but update/state flows still work. |

## Timeline WebView shell (SW-A0)

The timeline widget WebView shell (`wrapWidgetHtml` in `ConversationTimeline.kt`) is kept at parity with iOS's `WidgetWebView.buildShellHTML`. The shell is loaded **once** per WebView; subsequent content changes push through `window._setContent(html)` / `window._runScripts()` via `evaluateJavascript`, never a full page reload. This is the foundation for SW-A1 (partial widget-HTML streaming) and the fix for the prior "widgets pop in" behavior.

| Area | Check |
|---|---|
| Shell parity | `wrapWidgetHtml` emits the full iOS-parity document: theme vars in `:root`, `@keyframes _fadeIn` + `onNodeAdded` fade-in hook, morphdom CDN (`morphdom@2.7.4/dist/morphdom-umd.min.js`), and the `_morphReady` / `_pending` / `_setContent` / `_runScripts` / `_reportHeight` / `_attachHeightObserver` / `sendPrompt` / `openLink` JS. App-mode injection splices before `window._morphReady = false;`. |
| Bridge | JS posts `{_type, ...}` messages through `__postWidgetMessage`, which routes: `saveAppState` → `__LitterAppBridge`, `height`/`sendPrompt`/`openLink`/`ready` → `__LitterWidgetBridge`, with the iOS `webkit.messageHandlers.widget` fallback (no-op on Android). Both bridges coexist on the saved-app WebView. |
| Shell loads once | `AndroidView.factory` calls `loadDataWithBaseURL(..., wrapWidgetHtml(""), ...)` exactly once. `WebViewClient.onPageFinished` flips a per-WebView `widget_webview_shell_ready` tag to `true` and flushes any buffered HTML through `pushWidgetContent`. |
| Content push | Subsequent HTML changes in `AndroidView.update` call `pushWidgetContent(webView, html, runScripts = isFinalized)`, which runs `webView.evaluateJavascript("window._setContent('${escaped}'); window._runScripts();", null)`. `escapeJsString` matches iOS's `escapeJS` (backslash, single-quote, newline, CR, `</script>` → `<\/script>`). |
| Pre-ready queue | HTML changes that arrive before `onPageFinished` are stored on the WebView via the `widget_webview_pending_html` tag; `onPageFinished` flushes them. No content is dropped. |
| Dynamic height | `__LitterWidgetBridge.height(px)` posts the reported height through a main-thread `Handler`, clamped to `[200dp, 720dp]`. Compose's `mutableStateOf<Dp>(initial)` drives the WebView's `.height(widgetHeight)` modifier and animates smoothly as the widget grows/shrinks. Initial seed is the declared `data.height`. |
| sendPrompt | A widget button that calls `window.sendPrompt(text)` routes through `__LitterWidgetBridge.sendPrompt` → `onWidgetPrompt` callback in `WidgetRow` → `ConversationScreen` builds an `AppComposerPayload` and calls `appModel.startTurn(threadKey, payload)` — parity with iOS's `sendWidgetPrompt` which also submits a turn immediately. |
| openLink | Widget call to `window.openLink(url)` routes through the bridge to an `Intent.ACTION_VIEW` in the host Activity, opening the URL in the default browser. |
| Save-as-App bubble | `show_widget` finalize → auto-save (Rust-side) → Saved-as chip appears under the WebView. Shell lifecycle unchanged. |
| Saved app detail | `SavedAppScreen` uses the same shell through `wrapWidgetHtml("", AppStateInjection(...))` loaded once, then pushes `payload.widgetHtml` via `pushWidgetContent`. `loadAppState`/`saveAppState` still work. State persists across cold relaunch. |
| Regression: finalized timeline widget | An existing finalized `show_widget` renders identically to pre-refactor — fade-in animation, tap routing, state persistence of saved-app mode all preserved. |

## Realtime Voice (WebRTC transport)

Replaces the prior WebSocket + base64-PCM audio pump with a platform-native WebRTC peer connection on both iOS and Android. Upstream `thread/realtime/start` receives a client offer SDP via `AppRealtimeStartTransport.Webrtc`; the app-server responds with an answer SDP via `ThreadRealtimeSdpNotification`. All other realtime notifications (transcripts, item-added, handoff, closed, error) continue over the existing RPC WebSocket — only the audio byte path moved to the peer connection.

| Area | iOS | Android |
|---|---|---|
| Start request carries Webrtc transport | `AppStartRealtimeSessionRequest.transport == .webrtc(sdp:)` with a non-empty offer SDP (log at session start) | Same — `AppRealtimeStartTransport.Webrtc(sdp)` |
| Answer SDP applied | `AppStoreUpdateRecord.realtimeSdp` → `RealtimeWebRtcSession.applyAnswer(_:)` → `setRemoteDescription` succeeds | `AppStoreUpdateRecord.RealtimeSdp` → `RealtimeWebRtcSession.applyAnswer` → `setRemoteDescription` succeeds |
| Peer connection reaches connected state | `RTCPeerConnectionState.connected` observed via delegate | `PeerConnection.IceConnectionState.CONNECTED` observed |
| Bidirectional audio | Assistant voice plays back; mic input produces responses | Same |
| Transcript deltas (RPC path) | `ThreadRealtimeTranscriptDelta`/`Done` notifications still render | Same |
| Client-controlled handoff during voice | `HandoffManager` receives `HandoffRequested`, `resolveHandoff` / `finalizeHandoff` round-trip completes | Same |
| Dynamic tool call during voice | Argument deltas stream via RPC `ConversationItemAdded`; tool output returns via `resolveHandoff` | Same |
| Session stop | `RealtimeWebRtcSession.stop()` closes peer + data channel, deactivates `RTCAudioSession` | `stop()` disposes peer, restores audio mode, abandons audio focus |
| Session cycle (start/stop x5) | No leaked peer connections, microphone releases between sessions | No leaked peer, mic indicator clears between sessions |
| Known non-blockers | Per-frame input/output meter animation no longer drives — requires `RTCRtpReceiver.stats` polling to restore (follow-up) | Same flat meter behavior; speaker toggle currently stubbed to a boolean — follow-up to honor runtime routing |
| Regression: custom AEC path | Retired — `codex-ios-audio` crate + `AecBridge.swift` / `VoiceSessionAudioCodec.swift` were deleted; libwebrtc AEC3 handles echo cancellation natively | Retired — `AecBridge.kt` deleted; `JavaAudioDeviceModule` enables the hardware AEC + NS |
| Regression: SSH-tunneled codex server | RPC still flows through SSH; WebRTC peer goes direct to OpenAI edge from device. If client runs in fully air-gapped network, realtime voice will not establish | Same |

## NeCode Desktop connection UI — current verification boundary

- Android `DesktopHostTileVisualTest`: 7/7 on `emulator-5564` after rebuilding `:app:assembleDebug` and `:app:assembleDebugAndroidTest`. The host-picker fixture was captured and inspected at 1080×2400 (light) and 840×1867 with 1.3× font scale (dark). On the narrow screen the sheet expands to available height; other hosts remain in the scrollable list and the account action remains pinned below it.
- The captures use fixture hosts, not a signed-in Connect account. They prove picker layout only, not live host selection, session navigation, message sync, or the whole connected home screen. The login sheet was captured from the running app without credentials, but login/host-list transition was not retested.
- iOS picker and action styling were mirrored in SwiftUI source, but Windows cannot run Xcode or inspect an iPhone render. Compile and inspect both first-login and switch-host sheets on iOS before release. CLI QR pairing was not retested in this pass.
- The emulator was restored to its original 1080×2400 size, 1.0 font scale, and automatic night mode after the layout check. Temporary screenshot-delay code was removed from the test.

### Desktop conversation timeline (current worktree)

- Android `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:assembleDebug --offline` passed after adding text/tool/file summaries. `DesktopMessageParsingTest` checks ordered text → running tool → text → attachment and ignored/plain-text-file filtering. The rebuilt debug APK is under `app/build/outputs/apk/debug/`.
- Android host-picker fixture captures were inspected for standard and narrow widths. The rebuilt APK was installed on `emulator-5564`; the unconnected home, add-device chooser, and password-login sheet were captured from the running app. Those screens and fixture hosts are not a live signed-in session; they do not verify the connected home, list, message details, or switching a real desktop. The new timeline cards have not been visually inspected on a connected device.
- iOS source now mirrors ordered tool/attachment summaries, but the Windows environment cannot compile with Xcode or verify rendering. Before release, inspect connected-home/list/detail, long tool titles, dynamic type, light/dark mode, reconnect, and multiple-host switching on an iPhone.
- Tool cards intentionally show only title and execution state, not complete tool output; this is not full CLI timeline parity. QR-based CLI connection is unchanged and was not regression-tested in this pass.

### Desktop add-device chooser refinement

- Android chooser now presents Desktop login as the primary warm-accent action, with CLI QR pairing as a quieter secondary row. The Desktop login and QR screens replace chooser content in the *same* `DiscoveryScreen` sheet, rather than stacking a second `ModalBottomSheet` above a blank underlying sheet.
- Installed the rebuilt debug APK on `emulator-5564`. Inspected light 1080×2400 chooser and login, and dark 840×1867 chooser at 1.3× font scale; the title, primary action and QR row remain visible. The login screenshot confirms a single sheet after tapping the primary action. Opening QR reaches the Android camera-permission prompt; QR pairing itself was not exercised.
- iOS chooser mirrors the entry hierarchy in SwiftUI source. Windows still cannot compile or render iOS; verify sheet presentation, Dynamic Type and dark appearance on an iPhone. Connected-home/list/detail visual acceptance still requires a signed-in Desktop host; these chooser captures do not prove it.

### Desktop session navigation refresh (September 29, 2026)

- Android and iOS connected-session surfaces now default to a chronological recent-conversation list with project context; a separate Project tab shows every project (including empty ones), and selecting one filters its conversations. The global new-conversation action still lets the user choose a project. Search follows the visible tab.
- Android `:app:compileDebugKotlin --offline` and `:app:assembleDebug --offline` passed; the APK installed on `emulator-5564`. This is build/install verification only: no live Connect account or connected-host screenshot was used in this pass. Re-check the full connected list, project filter, long titles, detail composer and keyboard on an actual connection before calling the UI product-ready.
- iOS has matching source changes, but Windows cannot run Xcode; compile and inspect on an iPhone, including Dynamic Type and dark appearance. CLI QR pairing is unchanged and remains unverified in this pass.

### Connected session browser visual fixture (September 29, 2026)

- The Android recent-session/project browser is now a shared production composable used by the actual Desktop screen and a focused fixture test. The fixture checks recent sessions, all projects including an empty project, project filtering, and compact Chinese timestamps; it does not fake a separate list design.
- `:app:connectedDebugAndroidTest` ran the two browser tests on `emulator-5564`. I pulled and inspected the 1080×2400 fixture captures, then inspected the recent-session capture at 840×1867 and 1.3× font scale; title, project, and timestamp remain readable without overlapping. The emulator size and font scale were restored.
- The debug fixture Activity now uses `Theme.App` rather than the launch splash theme, so the `NeCode` splash no longer obscures the browser controls. These component captures still do not validate the full production shell.
- Session cards now reserve full width for up to two title lines and place project and localized relative time together beneath the title. iOS source mirrors the same hierarchy, but it has not been compiled or rendered on a Mac/iPhone. Real Desktop login, host switching, detail keyboard/composer, and CLI QR regression remain unverified in this visual pass.

### Desktop picker row refinement (September 29, 2026)

- Android/iOS host rows now show device name and a single inline status; the repeated “桌面端” subtitle and colored status pill were removed. The current host keeps its explicit continue/reconnect action, while busy/offline rows stay disabled.
- Android's focused picker/browser instrumentation tests passed on `emulator-5564` (9 tests). Picker fixture captures were inspected in light appearance at 1080×2400 and at 840×1867 with 1.3× font scale. The emulator's size, font scale, and night mode were restored. Fixture background is not the connected home.
- iOS source was updated to the same hierarchy but cannot be compiled or rendered on Windows. Live Connect login, real host switching, connected home, detail composer/keyboard and CLI QR remain outside this component fixture and need device acceptance before calling the UI release-ready.

### Desktop project directory navigation (October 8, 2026)

- Android/iOS distinguish Add project (browse folders on the connected computer) from New session (inside an existing project). The directory picker supports editable absolute paths, parent navigation, Windows drive selection, and explicit single-child folder creation. Using a directory registers it and opens its session list without creating a session or initializing Git. The `/` global placeholder is hidden, not deleted.
- Folder identities combine actual project ID and directory; session queries pass both, so different non-Git folders sharing the global project ID remain separate. The assistant brand row is removed, back-header spacing is reduced while keeping platform touch targets, and the old project-menu duplicate title/root blank row is removed. Existing warm/light colors and CLI QR connection are preserved.
- Verified: directory/project HTTP tests (3 passed, 34 assertions), earlier combined directory/session HTTP tests (7 passed, 78 assertions), backend `bun typecheck`, Host Rust tests (12 passed, 1 ignored), shared Desktop Connect source harness (37 passed). Android compile, focused Desktop unit tests (10 passed), and instrumentation-source compilation succeeded. Updated Android native bridge and Kotlin/Swift bindings include all four typed directory/project methods.
- This pass did not run instrumentation on a device, install an APK, perform connected-device visual acceptance, or regression-test CLI QR pairing. iOS source/bindings are synchronized but Windows cannot compile with Xcode or validate iPhone behavior. Network reconnect reliability is not newly validated by this project-navigation work.
- Desktop backend and Connect Host require a complete stop/restart of the isolated development environment; frontend hot reload is insufficient. Final APK packaging is recorded only after the assemble/signature/native-library checks succeed.

- Final Android artifact: `:app:assembleDebug --offline --console=plain` succeeded in 44s. APK built October 8, 2026 at 14:59:06 (local time), 186172537 bytes, package `com.aegean.necode.mobile`, arm64-v8a, debug v2 signature verified. SHA256: `5660050D0A5A37993525941C2F6DDD596AB24BAEB5BDE958D3DD478090A8CBF4`. Source JNI library matches Gradle merged library; APK Rust bridge matches Gradle stripped library (SHA256 `E149055F0FCF16B4ED16A290BEC119ED5E5E058F02CE81A7AAFA26A75EB0F271`). APK was not installed automatically.

### Desktop model/send feedback and reusable pagination (October 8, 2026)

- Connect Host now applies Desktop explicit provider enablement, disabled-provider settings and model visibility, returns a usable default (configured, NE, recent, other), and sorts NE first. Environment-only DeepSeek/Vertex entries are not product-enabled. Config/credential payloads remain internal to the Host. Existing V2-compatible model restrictions are retained. Host tests: 16 passed, 1 ignored (real sidecar integration requires separate runtime setup).
- Android/iOS show a UI-only outgoing preview immediately, dismiss the keyboard before network acknowledgement, retain the durable prompt journal, reconcile preview by exact prompt ID, and expose rejection/unknown delivery. Reply updates follow the actual bottom only until a user scrolls into history, with an explicit return-to-latest action. No new push/token-stream transport was introduced.
- Reusable native lists: Android ui/common/PaginatedLazyColumn.kt and iOS Views/PaginatedScrollView.swift. Recent and project session lists replace the normal load-more button with an upward gesture at the tail. Each gesture loads at most one cursor page, with loading/retry/end states and caller-owned generation fencing. Opening a list never automatically drains pages; first page stays ten rows. No unrelated CLI list migration.
- Android focused unit tests: 14 passed (5 message-follow/preview, 3 message parsing, 3 host action, 1 project identity, 2 paging gesture). Kotlin production and instrumentation sources compile. Pagination gesture regression was observed failing before implementation, then passing.
- This pass does not install an APK or run device instrumentation/visual acceptance. iOS source is synchronized but Windows has no Xcode/Swift compiler; Mac/iPhone compile and gesture acceptance remain required. Desktop Connect Host changes require a full development-process restart, not frontend hot reload. No shared Rust bridge ABI change or Android Rust-core rebuild is required by this pass.

- Final Android artifact for this pass: the combined focused unit-test, instrumentation-source compile and assembleDebug run succeeded (43s). APK built October 8, 2026 at 16:17:40 (local time), 186173771 bytes, at apps/android/app/build/outputs/apk/debug/app-debug.apk. SHA256: `BC86DA38799C52B8F6FE718AE9FC3BAF43279278312A343481B21DB5D038AEDA`. Debug APK v2 signature verifies with one signer. Source JNI matches Gradle merged native library; APK native bridge matches Gradle stripped library (SHA256 `EB51B9A8B5CDAA845972E244959564289EE436EB52095EF713AAE140A76C3843`). No Rust-core compilation or automatic device installation was performed by this pass.
- Delivery recheck: Android focused test/compile tasks are up-to-date and BUILD SUCCESSFUL; the five test XML suites total 14 tests, zero failures/errors. Host cargo tests were rerun: 16 passed, 1 ignored. APK SHA256, timestamp, size and signature were checked again. Backend connectSessions still defaults to ten rows per cursor page.
### NE DeepSeek 4.1 new-session default (October 8, 2026)

- Connect Host default priority: explicit valid configured model, visible/usable ne/deepseek-v4.1-flash, existing NE/recent/provider fallbacks. Hidden, disabled or missing models are never forced into the catalog. Existing mobile session/manual selection priority is unchanged.
- Focused regression failed with qwen3.8 before the change, then passed. Host suite: 17 passed, 1 ignored. No mobile ABI, Rust-core or APK changes; full Desktop dev restart and mobile new-session acceptance remain required.


### Desktop streaming APK and iOS source parity (October 9, 2026)

- Shared ARM64 Rust android-dev build succeeded (4m21s); source hashes remained unchanged throughout packaging. Built library/JNI/Gradle merged hashes match; stripped library/APK ZIP entry hashes match independently.
- Android assembleDebug succeeded (1m23s). Five focused Android test suites were explicitly rerun: 21 tests, zero failures/errors. Shared projection harness, extracted from current production source: 5 passed. git diff --check passed with existing line-ending warnings.
- Final APK: apps/android/app/build/outputs/apk/debug/necode-mobile-streaming-20261009.apk, 186196124 bytes, SHA256 1D7110CD405A20DCF7BC8406D1A1AE97F14A7DDE3C5D4920E6DFE25804F2D112. Identical to this build's app-debug.apk; not the earlier attachments APK. Debug v2 signature verified; com.aegean.necode.mobile, NeCode 1.0.0, ARM64, minSDK26/target35.
- Native built/JNI/merged SHA256: 19FE22772AB03FCBA97B80C86860A76C5142A517C7DC535481A86E78FB7FE761. Stripped/packaged SHA256: 42417A9D92F1DF1952F1D7B6539FB65DCE9178A3C80B6E29C3A7E21D64ACAE41.
- iOS source audit confirmed attachment previews/style/order, recent-project sorting, composer stop, model placement, live replies and history reconciliation. The previously Android-only reading pass now has iOS compact header, refresh menu, friendly default titles, default system reading font and independent monospaced code fonts. Existing font preferences are preserved; platform-native Markdown typography is retained rather than copying Android parameters.
- No automatic installation, connected-device visual acceptance, iOS build/native rebuild or public-relay end-to-end streaming verification was performed. Windows has no Xcode; Mac rebuild of iOS Rust artifacts and App is still required. Live replies also require an updated Desktop backend/Connect Host and full process restart; none was performed here.

### Desktop streaming redraw and waiting-label regression (October 9, 2026)

- Connected Samsung SM-S9280 reproduction used a separate test conversation in `D:\testmobileproject`, with a prompt explicitly forbidding tools/file edits. The recording confirmed that “思考中…” remained after visible reply text. No whole-body blank frame was established in the captured run; this does not rule out other flicker on the device.
- Waiting is now a display-only rule for the latest user question: nonblank assistant text hides the waiting label; tools/files/empty text do not. Earlier replies cannot suppress the next question's waiting label. Composer awaiting/busy/stop lifecycle is unchanged. Android and iOS display rules are aligned.
- Android Markdown keeps its TextView and does not reset identical content for unrelated control/parent updates; a changed renderer (font size/theme) still updates spans. Both live snapshot display loops yield 100ms between changed snapshots to reduce full-body re-layout bursts; absolute snapshots retain accumulated deltas, without fake typewriter output or trailing debounce.
- RED: the original waiting condition failed 2 of 4 new unit tests. The previously delivered streaming APK failed the production Compose/TextView regression on a temporary read-only emulator: unchanged Markdown was reassigned when only a control changed (TextWatcher count 2 -> 3).
- GREEN: 14 focused unit tests passed (4 waiting, 4 composer control, 6 message follow). Debug production/test Kotlin compilation and assembleDebug/assembleDebugAndroidTest succeeded offline (52s). The real Compose/TextView regression passed on emulator-5580 after the change, including content append with stable TextView identity and renderer invalidation after font-size change.
- A local validation build was generated only to run the rendering regression, reusing existing JNI; no shared Rust or Desktop backend edits/rebuild/restart. The previously named `necode-mobile-streaming-20261009.apk` remains the earlier delivered artifact; `app-debug.apk` now contains this newer local test build and is no longer identical to that named APK.
- The temporary emulator used `-read-only -no-snapshot`; it did not alter physical-device app data. No physical-device overwrite installation or post-fix end-to-end visual acceptance has been performed; await user confirmation for a data-preserving install. iOS is source-synchronized only: no Xcode build/iPhone verification on Windows.

- APK delivery follow-up: rechecked assembleDebug offline (BUILD SUCCESSFUL; all tasks up-to-date), copied the verified build to `apps/android/app/build/outputs/apk/debug/necode-mobile-streaming-fix-20261009.apk`. Size 186196124 bytes; SHA256 `14A1FF96CCCE86082A1E839418BBA869572D77449DED48CBB9C1D6E990DA26B1`, identical to current app-debug.apk but different from the earlier named streaming APK. APK v2 signature passed; package com.aegean.necode.mobile, version 1.0.0, ARM64, min26/target35. Packaged bridge hash remains `42417A9D92F1DF1952F1D7B6539FB65DCE9178A3C80B6E29C3A7E21D64ACAE41`. No physical-device installation.


### Desktop send-time layout jitter (October 9, 2026)

- Physical Samsung recording in a separate no-tool/file-edit test conversation showed two header-driven offsets: the pending-journal warning disappeared on admission, then the generic desktop-busy banner appeared on the attention refresh. Evidence: `D:\codex\.codex\tmp\mobile-send-jitter-20261009\send-before.mp4` and `contact-0.png`.
- Normal in-flight sends no longer show the unresolved-prompt warning; persisted unresolved prompts remain actionable once submission ends. Android uses the existing session-draft submission flag; iOS tracks the flag on the sending draft and clears it with defer, including errors/session changes.
- Removed the redundant ordinary-busy banner on both platforms. Retry labels, permissions/questions, disconnect catch-up, idle stop confirmation, waiting-for-first-body indicator, and composer stop remain. Message IDs, scrolling, admission/reconciliation, shared Rust and Desktop backend are unchanged.
- RED: new in-flight-warning regression failed (1 of 6 composer tests) with the old pending-only display rule. GREEN: 16 focused tests passed (6 composer, 4 waiting, 6 message-follow), including production/test Kotlin compilation, using offline Gradle.
- No new APK generated or physical-device installation. ADB no longer listed a connected phone at the verification step, so post-fix physical-device recording remains pending. iOS source synchronized, without Xcode build/iPhone validation on Windows.


### Desktop conversation edge and composer polish (October 9, 2026)

- Android/iOS conversation viewports now use 14dp/pt low-opacity theme-aware gradient edge overlays. These are decorative overlays, not layout rows: they do not change viewport height or the existing follow-tail algorithm. iOS overlays explicitly ignore hit testing and accessibility.
- Model labels sit approximately 6dp/pt lower within the existing minimum-height target, closer to the composer; picker actions/loading/error behavior remain unchanged.
- Return-to-latest now uses a neutral downward arrow inside a 40dp/pt circle with a subtle border, matching the supplied reference. The visible text is removed, but the accessibility label remains “回到最新” and the target is 48dp/pt. Visibility and click-to-follow rules are unchanged.
- Offline production/unit-test Kotlin compilation succeeded. 16 existing focused tests passed (composer 6, reply-waiting 4, message-follow 6), with zero failures/errors. Reviewed per-turn source diffs against saved pre-edit files; shared Rust/PC code untouched.
- No APK generated, no overwrite install or post-change device visual acceptance in this pass. iOS source only: Windows has no Xcode/iPhone compilation/visual verification. Next device check: light/dark edges, model spacing with long names, keyboard open/closed, attachment composer, upward reading and arrow-to-latest touch behavior.

- APK delivery follow-up: requested debug APK built successfully offline (9s, existing JNI reused) and copied to `apps/android/app/build/outputs/apk/debug/necode-mobile-conversation-polish-20261009.apk`. Size 186196124 bytes; SHA256 `8BD550F0D66C34C19630D3176174F690A96852576B127A40CE8B68B208EE8CE9`, identical to current app-debug.apk. APK v2 signature verified; com.aegean.necode.mobile 1.0.0, ARM64, min26/target35. Packaged libcodex_mobile_client.so SHA256 remains `42417A9D92F1DF1952F1D7B6539FB65DCE9178A3C80B6E29C3A7E21D64ACAE41`. Includes both send-time banner jitter fix and this edge/model/arrow polish. No automatic installation; user will perform visual acceptance.


### Desktop-matched background edge fade (October 9, 2026)

- Corrected the prior dark edge treatment following user visual feedback. Inspected PC production `packages/app/src/pages/session/workflow-chat-input-dock.tsx`: a 24px transparent-to-panel-background transition, not a dark shadow; the timeline title uses a matching background-to-transparent gradient.
- Android/iOS now fade content into the same page background across 24dp/pt. Blank space keeps exactly the page color instead of creating darker separators. Added 24dp/pt vertical timeline content padding so initial/final text can remain readable outside the fade. Overlays remain decorative; list measurement/follow-tail actions and model/arrow controls are unchanged.
- Offline Android production/unit-test compilation and assembleDebug passed (20s); 16 focused tests passed (6 composer, 4 waiting, 6 message-follow), zero failures/errors. Reviewed the small per-turn diffs against pre-edit snapshots. No Rust rebuild or PC edits/restart.
- Delivered `apps/android/app/build/outputs/apk/debug/necode-mobile-conversation-fade-20261009.apk`, 186196124 bytes, SHA256 `51A776F378D5526A5FFE82569889DF9A4FFFFE27E0EB26D93727BEA06F4CFDF2`, identical to current app-debug.apk and different from prior polish APK. APK v2 signature verified; packaged libcodex_mobile_client.so remains `42417A9D92F1DF1952F1D7B6539FB65DCE9178A3C80B6E29C3A7E21D64ACAE41`.
- No automatic device install or post-fix visual acceptance; user to validate continuity while scrolling and final-line readability. iOS source synchronized only, without Xcode/iPhone build or verification on Windows.

- Latest-arrow transparency follow-up: Android/iOS circle background now uses 80% opacity; arrow glyph, thin border, accessibility label, 48dp/pt touch target and scrolling action remain unchanged. Offline Android production Kotlin compilation passed. No new APK or device visual acceptance in this pass; awaiting clarification whether softer reply output means append presentation or completed-response typography before packaging the combined changes. iOS source only.

### Phone-sized streaming presentation pacing (October 9, 2026)

- User clarified that the issue is text appearing too quickly, not completed typography or whole-body opacity. Inspected PC production PacedMarkdown/createPacedValue: 24ms presentation pacing, but an immediate shortcut for backlogs <=512 characters. No fade-span experiment was retained.
- Android/iOS Desktop assistant replies now use a stable 24ms presentation clock across source snapshots. Phone-sized increments are split into 2/4/8-character steps, with up to 256-character catch-up steps for larger backlogs; <=8-character tails finish on the next tick. Existing displayed prefixes remain stable. Source history, corrections, completion/stop and disabled-system-motion snapshots appear in full. Emoji boundaries are preserved (UTF-16 surrogate pairs on Android, Characters on iOS).
- Only current-turn assistant bodies are paced; user bubbles and old history are immediate. Renderer/view caching, selectable text, Markdown normalization, provider response, Rust and transport are unchanged. Source updates do not restart the pacing clock. Text-height/content-height changes feed the existing guarded follow-tail request so paced line wraps remain visible, without pulling readers who have scrolled upward back down.
- Includes the preceding 80%-opacity latest-arrow background; arrow glyph/border/accessibility label and 48dp/pt target are unchanged. Theme-background edge fades remain as delivered previously.
- RED: 3 new step/boundary/catch-up unit regressions failed against the old instant-snapshot policy. GREEN: 19 focused unit tests passed (pacing 3, composer 6, reply-waiting 4, message-follow 6), zero failures/errors. Offline assembleDebug/assembleDebugAndroidTest succeeded. On the temporary read-only emulator, 2 real Compose/Markwon instrumentation tests passed: incremental snapshots produce multiple monotonic updates while keeping the same TextView, rapid snapshot updates catch up, stop/completion flush full content, corrections show exact content, unchanged controls retain span/selection caching, and font changes still rerender. Emulator was closed after testing.
- Delivered apps/android/app/build/outputs/apk/debug/necode-mobile-streaming-smooth-20261009.apk; SHA256 08F713D1D56DE6409D1609701F0D3F75A1A4482C3A764C93C429FBA7BA3BDFFF, identical to app-debug.apk. APK v2 signature verified; com.aegean.necode.mobile 1.0.0, ARM64, min26/target35. Packaged libcodex_mobile_client.so SHA256 remains 42417A9D92F1DF1952F1D7B6539FB65DCE9178A3C80B6E29C3A7E21D64ACAE41; no native rebuild or PC edits/restart.
- Physical phone was not auto-installed or visually accepted. User to validate perceived speed on real desktop replies, short/long chunks, Markdown/code/table wraps, upward reading and arrow-to-latest behavior. iOS pacing/geometry handling is source-synchronized only: no Xcode build/iPhone verification on Windows. System reduced-motion bypass was source-reviewed, not separately device-tested.


### Slower phone streaming and premeasured table rows (October 9, 2026)

- User reported that pacing was improved but still fast, especially table flicker/overlapping rows. Paused the requested mobile-kernel size analysis to address presentation first.
- Android/iOS presentation cadence is now 32ms (previously 24ms); normal step sizes are 1/2/4 characters, tail shortcut is limited to 2 characters, and large-backlog catch-up is capped at 32 characters instead of 256. Stop/completion/corrections/history still flush exact source snapshots; emoji boundaries remain intact.
- Inspected the installed Markwon 4.6.2 table implementation: fresh TableRowSpan layouts are initialized during draw, and TableRowsScheduler posts setText to repair their height afterward. Android now initializes table-cell layouts on an offscreen recording canvas before assigning an updated parsed snapshot; initial attachment and width changes also prepare rows before TextView measurement. No reflection, bitmap screenshot, provider buffering or native changes. Unchanged-source/renderer caching remains in place.
- RED: all 3 updated pacing unit regressions failed against the prior policy; the new table-height instrumentation regression failed on the previous smooth APK due to rows initially lacking measured width/height. GREEN: 19 focused unit tests (pacing 3, composer 6, reply-waiting 4, message-follow 6) passed; 3 real Compose/Markwon emulator tests passed, including repeated live table snapshots with multiline cells, unchanged controls/span caching, stable TextView identity, and stop/correction behavior. Android offline assembleDebug/assembleDebugAndroidTest succeeded.
- Delivered apps/android/app/build/outputs/apk/debug/necode-mobile-streaming-table-20261009.apk, 186196124 bytes, SHA256 18876AD8ED2493CE2763567577B1BA91F40F3F0AC1F23612F9A4FC8EAE6CEECC, identical to app-debug.apk. APK v2 signature verified; every packaged native library matches the previous smooth APK, with libcodex_mobile_client.so still 42417A9D92F1DF1952F1D7B6539FB65DCE9178A3C80B6E29C3A7E21D64ACAE41.
- Used a temporary read-only emulator only; no physical-device installation or PC edits/restart. Real phone visual acceptance remains required. iOS pacing was source-synchronized only, without Xcode build/iPhone verification; the Android-specific table measurement fix does not modify the iOS renderer.


- Streaming speed acceptance follow-up (October 9, 2026): user accepted the table/pacing fix and requested one further slower setting. Android/iOS presentation interval changed only from 32ms to 40ms (20% lower character throughput at equal step size/backlog). Step sizes, table premeasurement, follow-tail, stop/completion/correction behavior and all native code are unchanged. Offline assembleDebug and 19 focused unit tests passed. Delivered apps/android/app/build/outputs/apk/debug/necode-mobile-streaming-slower-20261009.apk, 186196124 bytes, SHA256 DE13908CC4AE1FAD40906C793D489348B8D398A6E6D83662283FA1274E643563, identical to app-debug.apk; APK v2 signature verified and all packaged native libraries byte-identical to the preceding table APK. No new emulator/physical-device run or installation; visual acceptance remains with the user. iOS source only, not built on Windows.

### PC-generated artifact cards and safe mobile preview (October 9, 2026)

- Shared Rust now projects completed tool `metadata.artifacts` into stable FILE rows after the tool record/group. Validates path/name/mime, deduplicates normalized Windows paths within a tool, and does not infer deliverables from plain reply/tool-output text. Existing registered artifacts in fetched history use the same projection. Existing pacing/table behavior is untouched.
- Android/iOS consume the same optional artifact path. Previews are loaded only when opened, through the existing authenticated `/file/content` request with the originating host and project directory. File-content normalization and 4 MB limit remain shared Rust policy. Missing/cleared files, invalid responses/encoding and oversize content produce visible errors. Normal inline attachments retain their existing behavior.
- HTML is displayed as a static page in a restricted WebView/WKWebView, not as source code. Scripts, network resources and local file/content access are blocked; the UI explains these restrictions. Other formats reuse image/text/external-app or QuickLook/share previews.
- Verified: ARM64 native library rebuilt offline; Kotlin/Swift UniFFI generated from its matching Rust archive metadata. 7 focused source-extracted Rust implementation tests, 13 Android unit tests and 4 Android35 emulator instrumentation tests passed. The artifact instrumentation test calls the newly packaged Rust projection and file-normalization exports through UniFFI and verifies HTML rendering with script/file/network privileges disabled; it uses a supplied file response, not a live relay/PC file request. The 3 prior streaming/table View tests also passed.
- APK: `apps/android/app/build/outputs/apk/debug/necode-mobile-artifact-preview-20261009.apk`, byte-identical to app-debug.apk; signature/package/native-library chain verified. No installation on the physical phone and no PC code/process changes. iOS source/bindings synchronized, but no iOS native/Xcode build or device acceptance is claimed on Windows. Remaining acceptance: open the user's original PC session on the phone, click its existing HTML card and verify the authenticated PC file request/actual page; also verify offline/moved-file errors.


### Artifact file-request route guards (October 10, 2026)

- Fixed the observed `Desktop route not allowed` before any file request reached the PC: the shared mobile Rust allowlist omitted GET `/file/content`. The PC connect-host independently omitted the same route; its previous test listed this route among rejected operations, not supported operations. Both boundaries now allow only this existing read endpoint. Existing method/route/query/size checks, Connect lease validation, authenticated sidecar proxy and file API permissions are unchanged.
- RED: two implementation-level route regressions failed on the original mobile/host guards. A new Android instrumentation test called `requestDesktopFor` through the previous APK's real UniFFI/native library and reproduced `InvalidParams: Desktop route not allowed`. GREEN: 9 focused source-extracted route checks passed; PC host tests passed (19 passed, 1 existing ignored), including local Iroh requests proving invalid leases cannot read files and valid leases forward the encoded project/file query with sidecar authentication.
- Rebuilt the ARM64 native library offline (4m44s), with no UniFFI public-interface changes or binding regeneration. Fresh Android unit tests: 13 passed. On the temporary emulator, 5 instrumentation tests passed: the packaged native request guard admits a Chinese-name artifact request through to the expected disconnected-state check and rejects POST/unknown query; static HTML preview remains restricted; 3 streaming/table regressions remain passing. This does not claim a real remote relay/PC file-read test.
- APK: `apps/android/app/build/outputs/apk/debug/necode-mobile-artifact-route-fix-20261010.apk`, SHA256 `41B099686C18E14CB03B2D46D9B486EB08A2A37F980AF02D39C3D77D79BF7919`, 186180456 bytes, byte-identical to app-debug.apk. V2 signature, application ID, ARM64 ABI and JNI/merged/stripped/packaged native hashes verified. Packaged native SHA256 `3288E8EF2A41821662616B9F4602F1211B18500356F6C3B715796F82727ADDD8`.
- PC host replacement binary was built separately without overwriting the running executable. Existing host process was not restarted, so its old route policy remains active pending user authorization. No physical-phone installation; iOS consumes the fixed shared source but its native library still needs a Mac build. Remaining acceptance: replace/restart PC connect-host, install the new phone APK, then reopen the original HTML card over the actual desktop connection.

### Attachment preview safe area (October 10, 2026)

- Full-screen Android attachment preview now uses explicit edge-to-edge Dialog layout with the host's live `safeDrawing` insets, plus the existing 16dp content spacing. Capture the inset padding before entering the Dialog: Compose 1.7 Dialog decor exposes consumed/zero insets on this Android35 emulator, so adding `safeDrawingPadding()` inside the Dialog alone did not fix the overlap. Background remains continuous; preview/file-open behavior is unchanged.
- The actual layout regression fails on the previous APK (button bottom 2347px overlaps the navigation-safe content boundary), and passes on the new APK. Android35 emulator: 6 instrumentation tests passed in three-button navigation (126px navigation inset), and all 3 artifact/preview tests passed in gesture navigation. These include the packaged native route guard, restricted HTML WebView rendering, bottom-action layout, and existing streaming/table tests. Fresh focused Android unit tests: 13 passed.
- APK: `apps/android/app/build/outputs/apk/debug/necode-mobile-preview-safe-area-20261010.apk`; SHA256 `DC0903ED5CDE942E3A68E9A4A2AF8548CA45FA3466AC2EF8B26C313B2569A888`; byte-identical to app-debug.apk. V2 signature, package/ARM64 metadata and native-library chain verified. Native JNI and packaged library hashes match the prior route-fix build exactly; no Rust rebuild, PC modification/restart, or physical-phone installation. iOS is not changed by this Android-only layout correction. Physical-phone visual acceptance remains with the user.
### Attachment preview bottom-spacing refinement (October 10, 2026)

- Increased only the preview dialog bottom content padding from 16dp to 24dp, in addition to the existing host safeDrawing inset. Top and horizontal padding remain 16dp; file loading/opening and native routes are unchanged.
- Updated the existing bottom-action layout regression to require 24dp spacing. It fails on the previous 16dp APK and passes on this build. Android35 emulator: 6 preview/native-guard/streaming instrumentation tests passed under three-button navigation; the bottom-spacing regression also passed under gesture navigation. No additional unit suite was run for this padding-only change.
- APK: `apps/android/app/build/outputs/apk/debug/necode-mobile-preview-bottom-space-20261010.apk`; SHA256 `4C2D5D5A769FB767F89F675EE6DEF619793556667DA723B1F3B94D2854962555`. Signature/package/native-chain checks passed, and the named APK is byte-identical to app-debug.apk. JNI and packaged native hashes remain unchanged. No Rust rebuild, PC restart, physical-phone installation or iOS changes. Phone visual acceptance remains with the user.
### Project-list compose icon refinement (October 10, 2026)

- Android project-row trailing orange "新会话" text is replaced with a secondary-color 20dp outlined note/pencil icon in a 48dp IconButton. iOS uses the existing SF Symbol square.and.pencil with the same visual hierarchy and 48pt hit area. Project-body navigation, per-project create actions, disabled/busy behavior, top-level Add Project and sorting remain unchanged.
- Expanded the existing production-browser instrumentation fixture: no repeated create text, retained Add Project, project-specific accessible label, exact 48dp bounds, correct create target without project navigation, busy-state disabling, and independent project-body navigation. RED: the old APK fails because it still exposes three create-text buttons. A stale fixture assertion for a back button outside DesktopSessionBrowser was corrected to check browser state and its actual project-session heading; no production back-navigation code changed.
- Fresh Android35 emulator run: 8 instrumentation tests passed (2 browser, 3 artifact/preview/native-guard, 3 streaming/table). Production-browser screenshots with fixed test data were captured and visually inspected; these are not live PC-connected screenshots. No additional unit suite for this UI-only change.
- APK: `apps/android/app/build/outputs/apk/debug/necode-mobile-project-compose-icon-20261010.apk`; SHA256 `3E32F8A7D82D2E2DDC8D11F16DEB04EADDBF6E217DA11FC7E05CE113AFAE2848`, 186180456 bytes. Named APK and app-debug.apk are byte-identical; V2 signature, package/ARM64 metadata and native-library chain verified. JNI and packaged native hashes match the previous APK exactly. No Rust rebuild, PC restart or physical-phone installation. iOS source is synchronized but has not been compiled or visually verified on Mac/Xcode. Physical-phone acceptance remains with the user.

### Project-list new-chat icon and scoped loading feedback (October 10, 2026)

- Android trailing create action now uses a 20dp outlined AddComment (chat bubble plus) icon; iOS uses plus.bubble. Both retain the existing 48dp/pt hit area and separate project-body navigation.
- Preserve the existing action-owner duplicate-creation guard. Track the creating project by action owner, show progress only on that row, and clean up on success, failure or cancellation. Other Android project icons keep their original secondary color while disabled; iOS removes the explicit whole-column busy opacity. No change to request, sorting, connection or attachment logic.
- RED: previous APK fails the unchanged-other-icon bitmap regression. GREEN: 8 Android35 emulator instrumentation tests passed (2 production-browser, 3 preview/native-guard, 3 streaming/table), including single-row progress, disabled duplicate actions, 48dp bounds, cleanup and independent project navigation. Production-browser idle/creating screenshots with fixed test data were captured and visually inspected; these are not live PC-connected screenshots. No additional unit suite for this UI-only change.
- APK: `apps/android/app/build/outputs/apk/debug/necode-mobile-project-new-chat-20261010.apk`; SHA256 `0E39E30768DC3F5A60E735FF23571E142F9D22E43D02CBB48648F483451909CF`, 186180456 bytes. Named APK and app-debug.apk are byte-identical; V2 signature, package/ARM64 metadata and native-library chain verified. Native library hashes match the previous APK. No Rust rebuild, PC restart or physical-phone installation. iOS source synchronized, but not compiled or visually verified on Mac/Xcode. Physical-phone visual acceptance remains with the user.
### Project-list plain chat bubble refinement (October 10, 2026)

- Only replaced the create-action icon: Android ChatBubbleOutline and iOS bubble, with no plus or internal lines. Kept the 20dp icon, 48dp/pt hit area, accessible create label, action guard and current-row progress unchanged.
- Fresh offline Android assembleDebug passed. APK signature, package/ABI and native-library-chain verification passed; named APK is identical to app-debug.apk and packaged Rust hash is unchanged. No new test added for this icon-only substitution; instrumentation and device visual acceptance were not rerun this iteration. iOS source synchronized but not compiled or visually verified in Xcode.
- APK: apps/android/app/build/outputs/apk/debug/necode-mobile-project-chat-bubble-20261010.apk; SHA256 0CFF2A4058E1CDD95B2F987C88639146A62A96CEE05177C010D8D711D2668967. No Rust rebuild, PC restart or physical-phone installation.
### Small chat-bubble icon and release-readiness audit (October 10, 2026)

- Android project-row chat bubble changed from 20dp to 18dp; iOS bubble font changed from 20pt to 18pt. Both retain the existing 48dp/pt hit target, current-row progress indicator and duplicate-submit guard. Simulator screenshot checked; no physical-phone installation or Rust rebuild performed.
- Final Android command `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :core:bridge:testDebugUnitTest` succeeded. App unit results: 133 tests across 33 suites, 0 failures/errors/skips; bridge has no test cases. Release output is unsigned; this is not a publishable signed release artifact.
- Full Android lint FAILED: 15 errors, 163 warnings, 18 hints. Findings include microphone permission analysis, service super-call, generated Cleaner API guards, local Windows property escaping, custom TextView, CameraX opt-in, Compose StateFlow reads, unused constraints scope and WebView interface analysis. These must be triaged; not all findings prove runtime defects. No checks disabled and no baseline added.
- Full Android35 emulator instrumentation: 19 tests, 16 passed, 3 failed. Passed coverage includes project-row actions, attachment HTML preview/bottom safe spacing, incremental Markdown/table rendering and native context guards. Failures: DesktopHostTileVisualTest expects old host-error text; ScreenshotTest searches English Appearance against Chinese UI; SelectableConversationTextTest expects LinkMovementMethod after enabling selectable text. The last failure requires real link-click/text-selection validation; no production workaround applied merely to satisfy the type assertion. Captured application crash buffer was empty during this run; this is not a stability guarantee.
- PC core attachment regression: 13 passed/0 failed. PC Connect plus crash-recovery combined suite: 15 passed/1 failed (session pagination expected 2 entries, received 5). The session test file passes all 4 tests in a fresh isolated process. Fixture uses an in-memory DB while reset removes disk files; cross-file DB state contamination is suspected and remains unresolved. Combined suite is NOT fully green.
- APK: `apps/android/app/build/outputs/apk/debug/necode-mobile-project-chat-bubble-small-20261010.apk`; named APK is byte-identical to app-debug.apk. V2 debug signature, app ID/ARM64 metadata and JNI -> merged -> stripped -> APK library chain verified. Existing native library unchanged.
- Evidence folder: `D:/codex/.codex/tmp/mobile-release-check-20261010`. Local audit emulator stopped after checks; connected physical phone untouched. No PC restart, commit, push or publication.
- Release verdict: NOT cleared for formal release. Full lint and instrumentation need resolution; formal signing and release version configuration need confirmation. iOS source is synchronized for this size change but no Mac/Xcode build/device validation was possible. Real 4G/5G, public relay, desktop crash/reconnect and full end-to-end attachment flows were not retested on physical devices in this audit.
- APK SHA256: `70E9800333DE50436FEDC3BB649352C5BF3C9DBF3F9F567C64429D80E3701CFD`; bytes: 186180456.

### Release-check closeout and remaining native test blocker (October 10, 2026)

- Supersedes the previous audit's unresolved Lint/interaction/Connect-fixture results; historical entries above remain unchanged. Worktrees: D:/project/litter-mobile-desktop-connect and D:/codex/.codex/worktrees/mobile-desktop-connect/necode-desktop-gitlab, both mobile-desktop-connect. Existing unrelated changes preserved.
- Fixed actionable Android Lint findings with explicit microphone permission guard, service super-call, AppCompatTextView, CameraX opt-in, reactive StateFlow reads, removal of unused constraints scope, concrete WebView bridge registration, and Android-aware UniFFI Kotlin Cleaner generation. No Lint suppression, baseline, or disabled checks added. Current full lint: 0 errors, 163 warnings, 18 hints.
- Real Markdown interaction red/green verification exposed link movement being overwritten by selectable-text initialization. Setting LinkMovementMethod afterward restored actual link taps while preserving native long-press selection/copy. Updated the outdated host-error/settings-text test expectations.
- UniFFI generation now uses shared/rust-bridge/codex-mobile-client/uniffi.toml with android=true and cdylib_name=codex_mobile_client. Native API/checksums and packaged Rust library unchanged; no Rust rebuild. Temporary metadata-restored generation library was not packaged.
- CameraX core/camera2/lifecycle/view updated together from 1.3.4 to 1.4.2 after finding 4KB ELF alignment in the old ARM64 image-processing library. Actual Debug and unsigned Release APKs pass both 16KB ELF LOAD-segment checks and ZIP alignment; Debug contains 11 ARM64 native libraries. Lint's cached JNA x86_64 alignment warnings do not refer to an ABI packaged in these ARM64-only APKs. Hardware camera scanning and real 16KB-page devices remain unverified.
- Latest Debug assemble, test-APK assemble, unit tests and full lint command succeeded; standalone latest Release assemble succeeded. App unit tests: 133 passed across 33 suites, no failures/errors/skips. Bridge unit task has no test source. Release output is unsigned, versionCode=1/versionName=1.0.0; not a signed publishable release.
- Instrumentation earlier completed all 20 tests on Android35 emulator, including Markdown links/native selection, streaming/table rendering, project actions and artifact preview spacing. IMPORTANT: final handoff rerun completed only 15 tests and blocked at test 16, NativeContextInitTest.listAlleycatAgentsDoesNotPanicWhenAndroidContextIsInitialized, for over 90 seconds. Last native log: binding shared iroh endpoint. It was manually terminated; the resulting Process crashed instrumentation message reflects force-stop, not proof of a spontaneous app crash. Emulator is x86_64 using libndk_translation.so for the ARM64 APK. Cause is NOT established, and earlier passing runs do NOT clear this repeated hang. SIGQUIT/Logcat evidence retained; native debuggerd requires unavailable root access. No test skipped or disabled and no retry added to hide the issue.
- PC Connect/crash-recovery focused suite: 16 passed/0 failed; core attachment suite: 13 passed/0 failed; packages/opencode bun typecheck exit 0 (rerun at handoff). Fixed resetDatabase to clear tables through the live AppRuntime database transaction rather than disk deletion that cannot reset an in-memory connection. Broader fixture regression: 48 passed, 1 skipped, 4 failed. The same four compression/provider-config failures reproduce with original HEAD fixture (9 passed/4 failed); not claimed as fixed or globally green.
- Deliverable: D:/project/litter-mobile-desktop-connect/apps/android/app/build/outputs/apk/debug/necode-mobile-release-check-20261010.apk, 186180629 bytes, SHA256 C5E37B1EDDFBD5F53E8B6B06158A5353B9BF7C2931BA59FEF220F8222C3F65F4. Byte-identical to current app-debug.apk. V2 Debug signature, application ID com.aegean.necode.mobile, min26/target35, ARM64-only ABI and JNI -> merged -> stripped -> APK Rust hash chain verified. Do not use the intermediate necode-mobile-final-check-20261010.apk, which predates the CameraX alignment fix.
- Evidence/report: D:/codex/.codex/tmp/mobile-release-finish-20261010/release-check-report.md. Only temporary emulator-5582 used; physical phone untouched. No commit/push/publication, no PC-process restart, no physical installation, and no Rust rebuild.
- Verdict: build and targeted source-fix closeout complete, but formal release NOT cleared. Remaining gates: repeated native bind hang investigated/validated on real ARM64 hardware; formal signing and version configuration; iOS Mac/Xcode build; physical WiFi/cellular/public relay and desktop crash/reconnect E2E; camera/16KB-device verification. iOS source parity is not equivalent to compiled or device-tested parity.
### New-key signed Release delivery (October 10, 2026)

- At the user's explicit request, generated a new RSA3072 PKCS12 release key outside the repository with restricted local ACLs; no old key was replaced. Private key/password were not printed, committed or uploaded. User-managed encrypted off-machine backup remains required.
- Signed the existing verified unsigned Release candidate without recompilation: D:\project\litter-mobile-desktop-connect\apps\android\app\build\outputs\apk\release\necode-mobile-release-signed-20261010.apk, 133591156 bytes, SHA256 95709A3CF19C8D8EFD2D614FFD60077D5CBF3D6890A18DABEFDDD29865BFA118. Verified V2/V3 signing and certificate match, com.aegean.necode.mobile, 1.0.0/build1, min26/target35, ARM64-only and non-debuggable. Certificate SHA256: 44A9B38CF301996FF8951AF022BB97AB8388B0C3659818E3BE6625A6AAAC4F07.
- ELF and ZIP16KB alignment passed for signed output. All2483 ZIP-entry payload hashes equal the unsigned candidate; Rust unchanged. This iteration did not rerun runtime tests or fix the repeated native bind hang described above.
- New certificate differs from current Debug; user chose uninstall/reinstall, with warning that local phone data is removed and login/connection may need restoration. No automatic physical-device installation, uninstall, distribution upload, commit or push.
- This iteration used offline apksigner; Gradle persistent signing configuration was NOT altered. Future Release builds must supply the existing supported LITTER_UPLOAD_* signing variables or sign unsigned output with this SAME key. Release stable-use gates remain separate from signature correctness.
- Evidence: D:\codex\.codex\tmp\mobile-release-signing-20261010-140531/signed-release-report.md.

### iOS simulator microphone and artifact preview fixes (October 10, 2026)

- The user's simulator hit an Objective-C `IsFormatSampleRateAndChannelCountValid(format)` exception when starting voice input. iOS now checks input availability and finite, positive sample rate/channel count before installing the audio tap. Failed engine starts remove the tap and release the audio session; the composer only records ownership after recording starts. Unavailable input is reported in the composer rather than treated as a successful recording.
- HTML preview used unsupported WebKit regex alternation in its content rule. Split HTTP(S), file and FTP blocking into separate rules; JavaScript stays disabled, and remote/local resource access remains blocked. Rule compilation failures now log the underlying WebKit error.
- Attachment sharing now explicitly presents a file-based `UIActivityViewController` inside the preview, checks the temporary file is readable and displays activity completion errors. The earlier ShareLink no-response root cause is not confirmed; this presentation change still needs Mac acceptance.
- Verified on Windows: the focused release source guard fails on the previous implementations and passes on the patched implementations; offline rule JSON and scheme coverage, microphone preconditions and failure cleanup are checked. No Swift compilation, actual WebKit rule compilation, simulator execution or real-device acceptance is claimed. Android and Rust are unchanged; no native-library rebuild is needed for these Swift-only fixes.
- Pending on Mac: run Debug on the simulator; check microphone success or visible input-unavailable error, reopen static HTML, and verify the system share panel. Before release, repeat voice input and file sharing on a real iPhone, including an unavailable/disconnected microphone route.
