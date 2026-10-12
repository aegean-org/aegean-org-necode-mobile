#!/usr/bin/env python3
"""Prepare the pinned Hairball package with the SwiftUI.Document compatibility fix."""

from __future__ import annotations

import re
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
PACKAGE_DIR = ROOT / "artifacts/ios/dependencies/hairball"
REPOSITORY = "https://github.com/dnakov/hairball.git"
REVISION = "42d52f5a5aa788b49b02f96c682949478cf01e91"
FILES = (
    "MarkdownImageHandler.swift",
    "MarkdownPreviewView.swift",
    "MarkdownView.swift",
    "StreamingMarkdownRenderer.swift",
)
DOCUMENT = re.compile(r"(?<![\w.])Document\b")


def git(directory: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-c", "core.autocrlf=false", "-C", str(directory), *args],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return result.stdout


def patch_package(directory: Path, revision: str) -> None:
    if not (directory / ".git").is_dir():
        raise RuntimeError(f"Expected a prepared Git checkout at {directory}")
    if Path(git(directory, "rev-parse", "--show-toplevel").strip()).resolve() != directory.resolve():
        raise RuntimeError(f"Unexpected checkout root at {directory}")
    if git(directory, "rev-parse", "HEAD").strip() != revision:
        raise RuntimeError(f"Hairball revision mismatch at {directory}; refusing to overwrite it")

    paths = [f"Sources/HairballUI/Views/{name}" for name in FILES]
    unexpected = set(git(directory, "diff", "--name-only", "HEAD").splitlines()) - set(paths)
    if unexpected:
        raise RuntimeError(f"Unexpected Hairball edits: {sorted(unexpected)}")

    changes = []
    for name in paths:
        # Compare against the pinned source rather than repeatedly rewriting a cache.
        original = git(directory, "show", f"{revision}:{name}")
        if "import SwiftUI" not in original or "import Hairball" not in original:
            raise RuntimeError(f"Unexpected Hairball imports in {name}")
        patched, count = DOCUMENT.subn("Hairball.Document", original)
        if count == 0:
            raise RuntimeError(f"Expected unqualified Document references in {name}")
        path = directory / name
        current = path.read_text(encoding="utf-8")
        if current not in (original, patched):
            raise RuntimeError(f"Unexpected edits in {path}; refusing to overwrite them")
        if current != patched:
            changes.append((path, patched))

    # Validate every file before changing any of them.
    for path, patched in changes:
        with path.open("w", encoding="utf-8", newline="\n") as output:
            output.write(patched)
    print(f"==> Hairball {revision[:12]} ready ({len(changes)} files patched): {directory}")


def prepare_hairball(
    directory: Path = PACKAGE_DIR,
    repository: str = REPOSITORY,
    revision: str = REVISION,
) -> None:
    if directory.exists():
        patch_package(directory, revision)
        return

    directory.parent.mkdir(parents=True, exist_ok=True)
    # Failed downloads never leave a half-prepared package at the project path.
    with tempfile.TemporaryDirectory(prefix=".hairball-", dir=directory.parent) as temporary:
        checkout = Path(temporary) / "package"
        checkout.mkdir()
        git(checkout, "init", "--quiet")
        git(checkout, "remote", "add", "origin", repository)
        print(f"==> Fetching pinned Hairball {revision[:12]}", flush=True)
        git(checkout, "fetch", "--depth", "1", "origin", revision)
        git(checkout, "checkout", "--quiet", "--detach", "FETCH_HEAD")
        patch_package(checkout, revision)
        checkout.rename(directory)
    print(f"==> Local Hairball package prepared at {directory}")


if __name__ == "__main__":
    try:
        prepare_hairball()
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"error: {error}", file=sys.stderr)
        if isinstance(error, subprocess.CalledProcessError) and error.stderr:
            print(error.stderr.rstrip(), file=sys.stderr)
        sys.exit(1)
