# F-Droid metadata template (for gitlab.com/fdroid/fdroiddata)

Copy `metadata/com.kaustubhtripathi.jaruri.yml` into fdroiddata.
`commit` must be the full 40-character hash of the release tag, not the tag name.
Do not put a real name, email, summary, or description in that file. Store listing text lives in `fastlane/metadata/android/`.

The app has a single build (Tunnel is part of it). For releases that include Tunnel, set the build's
`gradle:` to `yes` and add:

    gradleprops:
      - jaruri.tunnel.unifiedList=false

That leaves out the StevenBlack list, which bundles non-commercial sources (see `docs/tunnel.md`).
The app then holds INTERNET (Tunnel's DNS forwarding only); no anti-feature applies to that.
