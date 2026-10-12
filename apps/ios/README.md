# NeCode iOS

`project.yml` is the source of truth. After pulling changes, prepare dependencies
and regenerate the Xcode project before building from Xcode or `xcodebuild`:

```bash
bash apps/ios/scripts/regenerate-project.sh
```

This requires XcodeGen, Git, and Python 3.9 or newer. `make xcgen`, the Makefile
build verification step, and the TestFlight archive script use the same entry point.
This step does not rebuild Rust or upload anything.

## Hairball compatibility

The preparation script fetches Hairball revision
`42d52f5a5aa788b49b02f96c682949478cf01e91` into
`artifacts/ios/dependencies/hairball` and qualifies ambiguous `Document`
references as `Hairball.Document` in four HairballUI files. The generated project
uses this local Swift package for both command-line and Xcode builds. This avoids
editing Xcode's DerivedData checkouts and does not change Markdown behavior.

Repeated preparation reuses the pinned checkout without network access. Failed
fetches do not leave a partially prepared package. Unexpected source edits or a
revision mismatch stop preparation rather than being overwritten. Package sources
and their Git metadata stay in the ignored artifacts directory, not in this repo's
tracked source files.

Keep this package directory while using the generated Xcode project. If build
artifacts are removed, rerun project regeneration to prepare it again. Changing
the pinned revision requires reviewing the compatibility patch and preparing a
fresh dependency directory; do not blindly update the upstream branch.

Focused verification (does not require Xcode):

```bash
python3 -m unittest discover -s apps/ios/scripts/tests -p 'test_prepare_hairball.py' -v
```

Swift compilation, signing, and archive validation must still run on a Mac.
