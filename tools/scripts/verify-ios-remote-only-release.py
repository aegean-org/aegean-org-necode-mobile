#!/usr/bin/env python3
"""Validate that the iOS release lane stays remote-only for NeCode Mobile."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def forbid(text: str, path: str, fragments: list[str]) -> list[str]:
    return [
        f"{path}: remove `{fragment}`"
        for fragment in fragments
        if fragment in text
    ]


def require(text: str, path: str, fragments: list[str]) -> list[str]:
    return [
        f"{path}: add `{fragment}`"
        for fragment in fragments
        if fragment not in text
    ]


def section_between(text: str, start: str, end: str) -> str:
    start_index = text.index(start)
    end_index = text.index(end, start_index)
    return text[start_index:end_index]


def make_rule_body(makefile: str, target: str) -> str:
    start = makefile.index(f"{target}:")
    next_rule = makefile.find("\n\n", start)
    if next_rule == -1:
        return makefile[start:]
    return makefile[start:next_rule]


def main() -> int:
    failures: list[str] = []

    project_yml = read("apps/ios/project.yml")
    packages = section_between(project_yml, "packages:\n", "targets:\n")
    ios_app_target = section_between(project_yml, "  Litter:\n", "  LitterMac:\n")
    failures.extend(
        forbid(
            packages,
            "apps/ios/project.yml packages",
            [
                "WebRTC:",
            ],
        )
    )
    failures.extend(
        forbid(
            ios_app_target,
            "apps/ios/project.yml",
            [
                "- path: Resources/fs",
                "product: WebRTC",
                "-lghostty",
                "sdk: CarPlay.framework",
                "- path: Sources/LitterWatch/",
                "- path: Sources/LitterWatchComplications/\n",
                "- path: Sources/LitterWatchComplications\n",
            ],
        )
    )
    failures.extend(
        require(
            ios_app_target,
            "apps/ios/project.yml",
            [
                "- path: Sources/LitterWatchComplications/LitterRunningTurnPayload.swift",
                "CarPlay/**",
                "Bridge/GhosttyBridge.m",
                "Bridge/GhosttyRendererBackendBridge.swift",
                "Models/IshFS.swift",
                "Models/RealtimeWebRtcSession.swift",
                "Models/TerminalSessionController.swift",
                "Models/UserMountStore.swift",
                "Models/VoiceRuntimeController.swift",
                "Models/WatchApprovalNotification.swift",
                "Models/WatchCompanionBridge.swift",
                "Models/WatchProjection.swift",
                "Views/GhosttyTerminalView.swift",
                "Views/MountedFoldersView.swift",
                "Views/RealtimeVoiceScreen.swift",
                "Views/TerminalScreen.swift",
                "Views/VoiceCallView.swift",
            ],
        )
    )

    extensions_path = "apps/ios/Sources/Litter/Extensions.swift"
    theme = section_between(read(extensions_path), "enum LitterTheme {", "\nenum FontFamilyOption")
    theme_members = set(re.findall(r"static (?:var|let|func) (\w+)", theme))
    for path in (ROOT / "apps/ios/Sources/Litter").rglob("*.swift"):
        if ".generated." in path.name:
            continue
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            for member in re.findall(r"\bLitterTheme\.(\w+)", line):
                if member not in theme_members:
                    failures.append(f"{path.relative_to(ROOT)}:{number}: unknown LitterTheme.{member}")
    connect_path = "apps/ios/Sources/Litter/Views/DesktopConnectSheet.swift"
    failures.extend(forbid(read(connect_path), connect_path, ["DesktopConnectCredentialStore.preferredDesktopID()"]))

    # Runtime availability alone cannot protect APIs absent from the build SDK.
    for path in (
        extensions_path,
        "apps/ios/Sources/Litter/Views/ConversationComposerContentView.swift",
    ):
        compiler_guards: list[bool] = []
        for number, line in enumerate(read(path).splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith("//"):
                continue
            if stripped.startswith("#if "):
                compiler_guards.append(stripped == "#if compiler(>=6.2)")
            elif stripped.startswith(("#else", "#elseif")) and compiler_guards:
                compiler_guards[-1] = False
            elif stripped == "#endif" and compiler_guards:
                compiler_guards.pop()
            elif any(api in line for api in (".glassEffect(", ".glassEffectID(", "GlassEffectContainer(")):
                if not any(compiler_guards):
                    failures.append(f"{path}:{number}: guard Liquid Glass APIs with #if compiler(>=6.2)")

    voice_path = "apps/ios/Sources/Litter/Models/VoiceTranscriptionManager.swift"
    start_recording = section_between(read(voice_path), "    func startRecording()", "    func stopAndTranscribe(")
    before_tap = start_recording.split("inputNode.installTap", 1)[0]
    failures.extend(require(before_tap, voice_path, [
        "guard !isRecording, !isTranscribing else",
        "guard session.isInputAvailable else",
        "guard format.sampleRate.isFinite, format.sampleRate > 0, format.channelCount > 0 else",
        "teardownEngine()",
    ]))
    engine_start = start_recording.split("try engine.start()", 1)[1]
    failures.extend(require(engine_start, voice_path, ["teardownEngine()", "error.localizedDescription"]))
    if "audioEngine = engine" not in start_recording.split("try engine.start()", 1)[0]:
        failures.append(f"{voice_path}: retain the tapped engine before start so failure cleanup removes the tap")

    preview_path = "apps/ios/Sources/Litter/Views/DesktopSessionsView.swift"
    preview_source = read(preview_path)
    html_preview = section_between(preview_source, "struct DesktopArtifactHTMLPreview:", "struct DesktopAttachmentQuickLook:")
    rules_match = re.search(r'encodedContentRuleList:\s*#"(.*?)"#', html_preview, re.DOTALL)
    if not rules_match:
        failures.append(f"{preview_path}: missing offline HTML content rules")
    else:
        rules = json.loads(rules_match.group(1))
        filters = [rule["trigger"]["url-filter"] for rule in rules]
        if any("|" in pattern for pattern in filters):
            failures.append(f"{preview_path}: WebKit URL filters do not support regex alternation")
        for url in ("http://example.com/a", "https://example.com/a", "file:///secret", "ftp://example.com/a"):
            if not any(rule["action"]["type"] == "block" and re.search(rule["trigger"]["url-filter"], url) for rule in rules):
                failures.append(f"{preview_path}: offline HTML preview must block {url}")
        for url in ("about:blank", "data:image/png;base64,AA=="):
            if any(re.search(pattern, url) for pattern in filters):
                failures.append(f"{preview_path}: offline HTML preview must allow {url}")
    failures.extend(require(html_preview, preview_path, ["allowsContentJavaScript = false"]))
    attachment_preview = section_between(preview_source, "struct DesktopMobileAttachmentPreview:", "struct DesktopArtifactHTMLPreview:")
    failures.extend(require(attachment_preview, preview_path, [".sheet(isPresented: $showShareSheet)", "DesktopAttachmentShareSheet(url: fileURL)"]))
    failures.extend(forbid(attachment_preview, preview_path, ["ShareLink(item: fileURL)"]))

    info_plist = read("apps/ios/Sources/Litter/Info.plist")
    failures.extend(
        forbid(
            info_plist,
            "apps/ios/Sources/Litter/Info.plist",
            [
                "CPTemplateApplicationSceneSessionRoleApplication",
                "CarPlaySceneDelegate",
            ],
        )
    )

    source_forbids = {
        "apps/ios/Sources/Litter/Models/LitterPlatform.swift": [
            "supportsLocalRuntime = !isCatalyst",
            "supportsVoiceRuntime = !isCatalyst",
            "ishBootstrap(",
            "ishDefaultCwd()",
        ],
        "apps/ios/Sources/Litter/Views/DirectoryPickerView.swift": [
            "IshFS.",
        ],
        "apps/ios/Sources/Litter/Views/HomeDashboardView.swift": [
            "MountedFoldersView()",
        ],
        "apps/ios/Sources/Litter/Views/ConversationInfoView.swift": [
            "MountedFoldersView()",
            "isShowingMountedFolders = true",
        ],
    }
    for path, fragments in source_forbids.items():
        failures.extend(forbid(read(path), path, fragments))

    testflight_script = read("apps/ios/scripts/testflight-upload.sh")
    failures.extend(
        forbid(
            testflight_script,
            "apps/ios/scripts/testflight-upload.sh",
            [
                "WATCH_PROVISIONING_PROFILE_SPECIFIER",
                "WATCH_COMP_PROVISIONING_PROFILE_SPECIFIER",
                "WATCH_CODE_SIGN_IDENTITY",
                "WATCH_COMP_CODE_SIGN_IDENTITY",
                "WATCH_BUNDLE_ID",
                "WATCH_COMP_BUNDLE_ID",
            ],
        )
    )

    mobile_release = read(".github/workflows/mobile-release.yml")
    ios_release_prep = section_between(mobile_release, "  ios-release-prep:\n", "  upload-testflight:\n")
    ios_testflight = read(".github/workflows/ios-testflight.yml")
    ios_testflight_prep = section_between(ios_testflight, "  prepare-release-assets:\n", "  upload-testflight:\n")

    for workflow, text in (
        (".github/workflows/mobile-release.yml", mobile_release),
        (".github/workflows/ios-testflight.yml", ios_testflight),
    ):
        failures.extend(
            forbid(
                text,
                workflow,
                [
                    "IOS_WATCH_APP_STORE_PROFILE_B64",
                    "IOS_WATCH_COMPLICATIONS_APP_STORE_PROFILE_B64",
                    "WATCH_PROVISIONING_PROFILE_SPECIFIER",
                    "WATCH_COMP_PROVISIONING_PROFILE_SPECIFIER",
                ],
            )
        )

    for workflow, text in (
        (".github/workflows/mobile-release.yml ios-release-prep", ios_release_prep),
        (".github/workflows/ios-testflight.yml prepare-release-assets", ios_testflight_prep),
    ):
        failures.extend(
            forbid(
                text,
                workflow,
                [
                    "make alpine-fs",
                    "apps/ios/Resources/fs",
                ],
            )
        )
        failures.extend(require(text, workflow, ["--remote-only-ios"]))

    makefile = read("Makefile")
    for target in (
        "rust-ios-device-release",
        "rust-ios-device-fast",
        "rust-ios-sim-fast",
    ):
        failures.extend(forbid(make_rule_body(makefile, target), f"Makefile {target}", ["$(STAMP_GHOSTTY_IOS)"]))
        failures.extend(require(make_rule_body(makefile, target), f"Makefile {target}", ["--remote-only-ios"]))

    build_rust = read("apps/ios/scripts/build-rust.sh")
    failures.extend(
        require(
            build_rust,
            "apps/ios/scripts/build-rust.sh",
            [
                "REMOTE_ONLY_IOS=0",
                "--remote-only-ios",
                'CARGO_FEATURES_REMOTE_ONLY="--no-default-features"',
                'if [ "$REMOTE_ONLY_IOS" -eq 0 ]; then',
            ],
        )
    )

    cargo_toml = read("shared/rust-bridge/codex-mobile-client/Cargo.toml")
    failures.extend(
        require(
            cargo_toml,
            "shared/rust-bridge/codex-mobile-client/Cargo.toml",
            [
                'default = ["local-ios-runtime"]',
                'local-ios-runtime = ["dep:codex-apply-patch", "dep:ish-embed-host"]',
                "optional = true",
            ],
        )
    )

    rust_lib = read("shared/rust-bridge/codex-mobile-client/src/lib.rs")
    failures.extend(
        require(
            rust_lib,
            "shared/rust-bridge/codex-mobile-client/src/lib.rs",
            [
                'feature = "local-ios-runtime"',
                'detail: "iSH local runtime is not available in this build".into()',
            ],
        )
    )

    if failures:
        print("iOS remote-only release verification failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("iOS remote-only release verification passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
