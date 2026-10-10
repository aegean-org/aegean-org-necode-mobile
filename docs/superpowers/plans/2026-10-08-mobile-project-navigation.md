# Mobile project navigation

Approved scope: preserve existing light/warm theme and CLI QR flow. Work only in the two mobile-desktop-connect checkouts; preserve dirty work.

1. Add authenticated Desktop Connect directory browse, single-child folder creation, project registration and folder-based project listing. Use filesystem APIs, no command execution, no implicit Git init or session creation. Test actual HTTP handlers, validation, persistence and non-Git directories.
2. Add typed shared Rust bridge operations and synchronized Host/mobile allowlists; reuse endpoint ownership fencing. Test actual source in the focused harness.
3. Android/iOS: explicit Add project -> directory picker -> Use directory -> project session list; New session actions stay distinct. Preserve independent folder identity for non-Git projects. Remove global root placeholder from project choices, not session history. Fix header inset and remove assistant brand badge.
4. Verify backend typecheck, Host/shared focused tests and Android compile/unit tests. Generate bindings, rebuild only necessary cached native bridge, then one final APK. Do not install/commit/push. iOS build and device visual acceptance require external testing.
