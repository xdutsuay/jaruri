# F-Droid metadata template (for gitlab.com/fdroid/fdroiddata)

Copy `metadata/com.kaustubhtripathi.jaruri.yml` into fdroiddata.
`commit` must be the full 40-character hash of the release tag, not the tag name.
Do not put a real name, email, summary, or description in that file. Store listing text lives in `fastlane/metadata/android/`.

For releases made after the `offline`/`tunnel` flavors exist, set the build's `gradle:` to `- offline`.
Never build the `tunnel` flavor for F-Droid: it adds INTERNET and bundles a blocklist with non-commercial sources (see `docs/tunnel.md`).
