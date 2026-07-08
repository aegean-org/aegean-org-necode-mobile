#!/usr/bin/env python3
"""Validate that the iOS release lane stays remote-only for NeCode Mobile."""

from __future__ import annotations

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
                "- path: Sources/LitterWatchComplications/",
            ],
        )
    )
    failures.extend(
        require(
            ios_app_target,
            "apps/ios/project.yml",
            [
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

    if failures:
        print("iOS remote-only release verification failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("iOS remote-only release verification passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
