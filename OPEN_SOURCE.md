# Open Source Policy

NeCode Mobile is the GPLv3 mobile app for controlling a local NeCode session from a phone.

## License

This repository is licensed under the GNU General Public License version 3 with the additional permission under GPLv3 section 7 for Apple App Store and Google Play distribution. See [LICENSE](LICENSE).

Any distributed APK, IPA, or other binary built from this repository must provide the corresponding source code under the same GPLv3 terms and preserve required notices.

## Upstream

This repository is derived from Litter. NeCode-specific changes are maintained under `aegean-org/aegean-org-necode-mobile`.

## Repository Role

```text
aegean-org/aegean-org-necode-mobile
  -> Android/iOS mobile app source
  -> shared mobile Rust client
  -> app store and APK release workflows

aegean-org/alleycat
  -> GPL desktop daemon source
  -> iroh relay pairing and local agent bridge

aegean-org/necode-cli
  -> separately licensed CLI package
  -> invokes/downloads the GPL daemon instead of vendoring it
```

The mobile app and daemon stay open-source GPL components. The NeCode CLI may interoperate with them through command execution and release downloads, but should not copy GPL code into a differently licensed package.
