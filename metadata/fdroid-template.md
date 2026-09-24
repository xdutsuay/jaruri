# F-Droid metadata template (for gitlab.com/fdroid/fdroiddata)

Copy into `metadata/com.kaustubhtripathi.jaruri.yml` when opening an MR.
Update `commit` to the release tag SHA after tagging `v1.5.0`.

```yaml
Categories:
  - Money
License: Apache-2.0
AuthorName: Kaustubh Tripathi
SourceCode: https://github.com/xdutsuay/jaruri
IssueTracker: https://github.com/xdutsuay/jaruri/issues

AutoName: Jaruri

RepoType: git
Repo: https://github.com/xdutsuay/jaruri

Builds:
  - versionName: 1.5.0
    versionCode: 6
    commit: v1.5.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.5.0
CurrentVersionCode: 6
```
