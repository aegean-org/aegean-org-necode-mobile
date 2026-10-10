# Desktop Connect reliability and first-page plan

> Status: approved direction; execute in the existing mobile-desktop-connect workspaces. Preserve existing dirty work. No merge, release or APK until the stage is verified.

## Scope and order
1. Mobile transport: short active-lock snapshots, generation fencing, cancellable connect reservation; release/close outside the state lock. Test status/disconnect during stalled I/O and cancellation of a pending attempt.
2. Host transport: bounded concurrent streams, stream deadlines and task teardown on connection close. Test a stalled stream does not block a completed stream. Preserve authentication and size limits.
3. Host errors/observability: preserve authentication vs lease conflict vs transient validate errors; log phase timings without credentials/paths. Never turn every failure into 403.
4. First-page API: add a desktop adapter route for global/project recent sessions with updated+id cursor, page-size bounds, root/archive filtering and response-body metadata. Reuse the real Session query and retain legacy semantics. Test tied timestamps, pinning, project scoping and page size.
5. Shared mobile browser: Rust owns page parsing/query/cursor policy; native shells render recent/projects/project sessions and append a page, rather than scanning every project. Fence responses by connection/query identity. Implement Android and iOS together.
6. Follow-up stages: unique connection orchestrator, message tail/updates, prompt admission reconciliation, permissions/questions and native visual polish. Track explicitly; do not claim the whole audit fixed after steps 1-5.

## Files
Mobile: shared/rust-bridge/codex-mobile-client/src/ffi/desktop_connect.rs; Android ui/desktop/DesktopSessionsScreen.kt; iOS Views/DesktopSessionsView.swift; necessary focused tests/bindings.
Desktop: packages/desktop-connect-host/src/main.rs and protocol.rs; packages/opencode/src/session/session.ts; HTTP API experimental group/handler; focused session and route tests.
Connect/MySQL main remains untouched in this stage. Identity-cache/security changes require trace evidence and a defined revocation policy.

## Verification
Run existing focused host Rust tests from its package using cached artifacts. Avoid a full mobile upstream Rust-core rebuild: use existing focused harness if available or a small temporary harness compiling the actual desktop module. Run Bun focused session/route tests and bun typecheck from packages/opencode. Compile Kotlin only after shared/native API changes settle. iOS compile/device testing needs a Mac; disclose this limitation.
Record red/green evidence for confirmed risks. Slow network controls remain test-only and deterministic. Do not print tokens, password environment values, ticket or raw request bodies.

## Acceptance
- Stalled request cannot hold the connection state lock; disconnect cancels network and late old-generation responses cannot update new state.
- Stalled QUIC stream does not block other streams; concurrency is bounded and teardown aborts stream tasks.
- Validate service failure yields retryable service error, not fake permission denial.
- Recent first page is 10 true recently updated root sessions across projects, not 18 sequential project requests; project sessions load on demand.
- Equal updated timestamps have stable ID boundaries; recent ignores pin priority; archived/child sessions do not silently contaminate the list.
- UI route, draft and connection lifecycles are not declared complete until the follow-up state work and device checks pass.

## Execution checkpoint — 2026-09-30
Steps 1–6 have source changes and focused validation. The final closure includes a shared recovery owner; host/session-scoped drafts and durable prompt journals; exact receipt reconciliation; message tails, stable-ID projection and bounded staged gap fill; real stop/permission/question actions; desktop-visible model selection; and shared NE ASR on Android/iOS. See docs/architecture/2026-09-30-desktop-connect-stage-one.md for evidence and limitations.

Current source gates: shared desktop/voice harness 35 passed; host 11 passed/1 environment-dependent test ignored; backend 13 passed/119 assertions; packages/opencode bun typecheck exit 0. Android production and instrumentation sources compile and the 9 Desktop unit tests pass after regenerated bindings and final native fencing changes (46s). The real Host/local Connect controller integration passed again (1 test, 66 assertions).

Final bridge/build gates passed: Android arm64 codex-mobile-client build, 656 UniFFI exports, final assembleDebug (49s), APK signature/package checks and packaged-native hash provenance. The final APK was generated at 2026-09-30 20:45:09; see the stage-one closure for its full path and hashes. No intermediate package was delivered or installed. iOS Xcode/device, real-device reconnect/ANR and latency measurement remain acceptance tasks, not verified outcomes. No commit, merge, push or publication.
