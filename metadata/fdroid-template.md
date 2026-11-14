# F-Droid metadata template (for gitlab.com/fdroid/fdroiddata)

Copy `metadata/com.kaustubhtripathi.jaruri.yml` into fdroiddata.
`commit` must be the full 40-character hash of the release tag, not the tag name.
Do not put a real name, email, summary, or description in that file. Store listing text lives in `fastlane/metadata/android/`.

The app has a single build (Tunnel is part of it). For releases that include Tunnel, set the build's
`gradle:` to `yes` and add:

    gradleprops:
      - jaruri.tunnel.unifiedList=false
      - jaruri.stt.bundleModels=false

The first leaves out the StevenBlack list, which bundles non-commercial sources (see `docs/tunnel.md`).
The second skips the build-time download of the Listener speech models (F-Droid builds are offline);
users of that build import a model by hand (see `docs/listener.md`).
The app then holds INTERNET (Tunnel's DNS forwarding only); no anti-feature applies to that.
