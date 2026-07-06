# iOS NeCode Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the iOS app in line with the Android NeCode Mobile path that is already working.

**Architecture:** Keep shared behavior in `shared/rust-bridge/codex-mobile-client` and keep SwiftUI thin. iOS changes should mostly wire existing UniFFI APIs, update NeCode-specific presentation, and remove Litter-first copy from the main pairing/chat flow.

**Tech Stack:** SwiftUI, AVFoundation, UniFFI Swift bindings, shared Rust `codex-mobile-client`.

## Global Constraints

- Do not duplicate shared runtime state logic in Swift.
- Voice transcription must send recorded WAV bytes to the paired desktop daemon through `AppClient.transcribeVoice`, not directly to OpenAI or ChatGPT from the phone.
- Chat model selectors must hide ASR-only models.
- NeCode should be the preferred visible agent when a paired host exposes it.
- Final pass should remove obvious Litter/kittylitter user-facing wording from the NeCode mobile path.

---

### Task 1: iOS Voice Transcription Uses Daemon

**Files:**
- Modify: `apps/ios/Sources/Litter/Models/VoiceTranscriptionManager.swift`
- Modify: `apps/ios/Sources/Litter/Models/AppModel.swift`
- Modify: `apps/ios/Sources/Litter/Views/HomeComposerView.swift`
- Modify: `apps/ios/Sources/Litter/Views/ConversationView.swift`

**Steps:**
- [ ] Add a Swift helper that builds `AppVoiceTranscriptionRequest` from recorded WAV bytes.
- [ ] Replace direct mobile HTTP transcription with `appModel.transcribeVoice(...)`.
- [ ] Select the preferred ASR model from server models and pass `agentRuntimeKind: "necode"`.
- [ ] Preserve current mic permission, WAV encoding, and text insertion behavior.

### Task 2: Model Picker Filters ASR Models

**Files:**
- Modify: `apps/ios/Sources/Litter/Views/HeaderView.swift`

**Steps:**
- [ ] Add iOS equivalents of Android's ASR model detection.
- [ ] Update `isVisibleModelOption` to hide ASR-only models and keep Amp visible-mode filtering.

### Task 3: Pairing Flow Defaults To NeCode

**Files:**
- Modify: `apps/ios/Sources/Litter/Views/AlleycatAddServerSheet.swift`
- Modify: `apps/ios/Sources/Litter/Views/DiscoveryView.swift`
- Modify: `apps/ios/Sources/Litter/Models/SavedServerStore.swift`

**Steps:**
- [ ] Change pairing copy from kittylitter/Litter to NeCode Mobile.
- [ ] Prefer `necode` in agent ordering and default selection.
- [ ] Avoid exposing stale `This Device` local-Codex wording in the NeCode path.

### Task 4: Brand Assets And Visible Logo

**Files:**
- Modify: `apps/ios/Sources/Litter/Views/BrandLogo.swift`
- Modify: `apps/ios/Sources/Litter/Assets.xcassets/brand_logo.imageset/*`
- Modify: `apps/ios/Sources/Litter/Assets.xcassets/AppIcon.appiconset/*`

**Steps:**
- [ ] Reuse the current Android/home NeCode logo styling where possible.
- [ ] Replace obvious app/home logo references that still show old Litter branding.

### Task 5: Chinese Copy Sweep

**Files:**
- Modify focused SwiftUI files touched above first.
- Only broaden to nearby settings/conversation files if hardcoded English is on the primary NeCode path.

**Steps:**
- [ ] Translate pairing, model, composer, loading, and connection status strings.
- [ ] Keep internal debug and advanced legacy screens unchanged unless they are visible in normal use.
- [ ] Run focused searches for `Litter`, `kittylitter`, `This Device`, and direct transcription endpoints.
