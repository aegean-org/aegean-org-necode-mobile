# Mobile model and send feedback

Preserve warm UI, isolated checkouts and CLI QR flow.

1. Align Connect catalog with Desktop explicit provider visibility, model visibility and NE-first grouping. Return a valid Desktop default, preserving session/manual selections. Add focused Host tests for env providers, config, custom providers and default priority.
2. Immediately hide the keyboard and show a clearly marked local outgoing bubble before network acknowledgement. Reconcile by exact prompt message ID, retain durable pending journal and visible rejection/unknown state. No fake success or automatic duplicate send.
3. Follow output only while the user is at the bottom; scrolling into history pauses follow. Provide a return-to-latest button. Fix Android bottom-item targeting and viewport/keyboard changes; synchronize iOS behavior.
4. Focused Host/Android tests and compile; no shared native rebuild unless ABI changes. One final APK, no auto-install/commit/push. Windows iOS/device visual verification limitations must be stated.

## Added: reusable upward pagination

Android/iOS use a native reusable paginated list with an upward user gesture at the footer, one page per gesture, loading/error retry/end states. Opening the list never chains pages. Recent and project session lists retain the ten-row cursor query and scope/generation fences. Network loading stays owned by the caller. No unrelated CLI list migration.

## Follow-up: NE DeepSeek 4.1 default

For new sessions without an explicit valid configured default, prefer visible/usable ne/deepseek-v4.1-flash before the generic NE default. Preserve session/manual selections and existing fallback behavior when unavailable. Host-only change; no APK/native bridge rebuild.

## Follow-up: popovers and pre-prompt model selection

- Android popup menus share warm theme surfaces, 16dp corners, light borders/shadows, constrained width and 48dp action targets. Preserve caller-owned actions and dismissal; add attachment/action icons rather than a new navigation flow.
- Keep long model catalogs in a searchable bottom sheet with provider grouping, a visible selected row/checkmark, loading/error/retry/empty states and an explicit close action. iOS uses its native sheet presentation.
- Store Android catalog/error/loading in each host/session composer draft. A new-session recomposition must not replace the async load target. Always expose model selection before the first prompt; retain the Desktop catalog/default and pass the chosen model on the first send.
- Disable prompt send during initial model loading. Preserve existing reconnect, durable prompt, attachment and CLI behavior. Android compilation/unit tests/APK are verified separately from visual/device acceptance; iOS requires Xcode verification.
