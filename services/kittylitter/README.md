# kittylitter

`kittylitter` is the GPL daemon wrapper used by the NeCode Mobile development workspace. It re-exports the Alleycat daemon under the historical binary name used by the mobile app tooling.

All daemon behavior lives in [`aegean-org/alleycat`](https://github.com/aegean-org/alleycat). This wrapper exists so the mobile repo can build and test the daemon alongside the Android/iOS app.

## NeCode User Flow

End users should normally use the NeCode CLI wrapper:

```powershell
necode mobile serve
necode mobile qr
necode mobile status
```

## Local Development

```powershell
cd services\kittylitter
cargo run -- serve
cargo run -- pair --qr
cargo run -- status
```

The wrapper depends on the open-source daemon repository by default:

```toml
alleycat = { git = "https://github.com/aegean-org/alleycat.git", branch = "main" }
```

For local daemon changes, use Cargo's local git override in `.cargo/config.toml` or temporarily patch the dependency in a private working copy.

## Release Boundary

This crate is GPL-3.0-only. The separately licensed `necode-cli` package should invoke or download the daemon as a separate GPL component, not vendor it into the CLI package.
