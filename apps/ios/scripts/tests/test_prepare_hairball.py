"""Focused tests for the pinned iOS Markdown dependency preparation."""

import contextlib
import importlib.util
import io
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "prepare-hairball.py"
SPEC = importlib.util.spec_from_file_location("prepare_hairball", SCRIPT)
hairball = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(hairball)


class PrepareHairballTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.upstream = self.root / "upstream"
        self.upstream.mkdir()
        hairball.git(self.upstream, "init", "--quiet")
        for name in hairball.FILES:
            path = self.upstream / "Sources/HairballUI/Views" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(
                "import SwiftUI\nimport Hairball\n"
                "let document: Document = Document(blocks: [])\n"
                "let qualified: Hairball.Document = document\n",
                encoding="utf-8",
            )
        (self.upstream / "Package.swift").write_text("// package manifest\n", encoding="utf-8")
        hairball.git(self.upstream, "add", ".")
        hairball.git(
            self.upstream, "-c", "user.name=Hairball Test", "-c", "user.email=test@example.invalid",
            "commit", "--quiet", "-m", "fixture",
        )
        self.revision = hairball.git(self.upstream, "rev-parse", "HEAD").strip()
        self.package = self.root / "dependencies/hairball"

    def prepare(self, repository=None):
        with contextlib.redirect_stdout(io.StringIO()):
            hairball.prepare_hairball(
                self.package, str(repository or self.upstream), self.revision,
            )

    def test_first_run_patches_and_repeat_is_offline_and_idempotent(self):
        self.prepare()
        snapshots = {}
        for name in hairball.FILES:
            path = self.package / "Sources/HairballUI/Views" / name
            text = path.read_text(encoding="utf-8")
            self.assertIsNone(hairball.DOCUMENT.search(text))
            self.assertNotIn("Hairball.Hairball.Document", text)
            snapshots[path] = (path.read_bytes(), path.stat().st_mtime_ns)
        # An invalid repository must not matter once the pinned checkout is prepared.
        self.prepare(self.root / "missing-upstream")
        for path, snapshot in snapshots.items():
            self.assertEqual(snapshot, (path.read_bytes(), path.stat().st_mtime_ns))
        self.assertEqual("// package manifest\n", (self.package / "Package.swift").read_text())
        with self.assertRaisesRegex(RuntimeError, "revision mismatch"):
            hairball.prepare_hairball(self.package, str(self.upstream), "0" * 40)

    def test_unexpected_edits_are_not_overwritten_or_partially_patched(self):
        self.prepare()
        hairball.git(self.package, "restore", "--", "Sources")
        paths = [self.package / "Sources/HairballUI/Views" / name for name in hairball.FILES]
        with paths[-1].open("a", encoding="utf-8") as output:
            output.write("// user change\n")
        before = [path.read_bytes() for path in paths]
        with self.assertRaisesRegex(RuntimeError, "refusing to overwrite"):
            self.prepare()
        self.assertEqual(before, [path.read_bytes() for path in paths])
        hairball.git(self.package, "restore", "--", "Sources")
        (self.package / "Package.swift").write_text("// unexpected change\n")
        with self.assertRaisesRegex(RuntimeError, "Unexpected Hairball edits"):
            self.prepare()

    def test_failed_fetch_leaves_no_partial_package(self):
        with self.assertRaises(subprocess.CalledProcessError):
            self.prepare(self.root / "missing-upstream")
        self.assertFalse(self.package.exists())
        self.assertEqual([], list(self.package.parent.iterdir()))

    def test_project_and_regeneration_use_the_prepared_package(self):
        project = (hairball.ROOT / "apps/ios/project.yml").read_text(encoding="utf-8")
        regeneration = SCRIPT.with_name("regenerate-project.sh").read_text(encoding="utf-8")
        self.assertIn("path: ../../artifacts/ios/dependencies/hairball", project)
        self.assertNotIn("branch: main", project.split("  Hairball:", 1)[1].split("  Nuke:", 1)[0])
        self.assertLess(
            regeneration.index('python3 "$SCRIPT_DIR/prepare-hairball.py"'),
            regeneration.index('if [[ "$REPAIR_ONLY" -eq 1'),
        )
        self.assertIn('grep -Fq "../../artifacts/ios/dependencies/hairball"', regeneration)
        makefile = (hairball.ROOT / "Makefile").read_text(encoding="utf-8")
        self.assertIn(
            "xcgen: $(STAMP_XCGEN)\n\t@$(IOS_SCRIPTS)/regenerate-project.sh --repair-only",
            makefile,
        )


if __name__ == "__main__":
    unittest.main()
