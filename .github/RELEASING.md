# Release workflow

The **Build and release** action validates pushes and pull requests. Publishing is manual: select **Actions → Build and release → Run workflow** on the repository's default branch. The workflow must already be present on that branch.

Choose a version increase:

| Input   | Example           | Effect                                                         |
|---------|-------------------|----------------------------------------------------------------|
| `patch` | `1.2.3` → `1.2.4` | Increment patch.                                               |
| `minor` | `1.2.3` → `1.3.0` | Increment minor; reset patch.                                  |
| `major` | `1.2.3` → `2.0.0` | Increment major; reset minor and patch.                        |
| `NONE`  | `1.2.3` → `1.2.3` | Release the existing project version without a version commit. |

With **auto-increase** enabled (the default), patch and minor roll over at 10: `1.0.9` → `1.1.0`, `1.9.9` → `2.0.0`. Disable it to allow values such as `1.0.10` or `1.10.0`. Versions use only `MAJOR.MINOR.PATCH`; `NONE` leaves the version unchanged.

The version source is `projectVersion` in `gradle.properties`. To publish the initial `0.1.0` version, select `NONE`; the default `patch` selection would publish `0.1.1`. Tags contain only the version, with no prefix.

Before publishing, the workflow validates the version, rejects existing tags, and builds/tests the proposed version on Java 21. It then commits only `gradle.properties` if changed and pushes that commit and its annotated tag atomically. If the default branch advances while the build runs, the workflow fails rather than releasing stale code. Release runs share a concurrency group. Starting a new release cancels older running or pending release jobs, keeping the newest request. Cancellation does not undo a version commit, tag, or release that has already been published.

The workflow uses the repository's `GITHUB_TOKEN` with `contents: write`. Repository rules must permit that token to push the version commit and create release tags. A rejected push stops release creation; there is no force push or automatic branch-protection bypass.

The GitHub release contains generated release notes and the main, sources, and documentation JARs. JitPack separately builds the tag when requested; it uses that tag as its dependency version. Consumers can then use:

```kotlin
detektPlugins("com.github.Stone-Soup-Tech:custom-detekt-rules:1.2.3")
```

If GitHub release creation fails after the commit/tag push, the tag remains. After confirming the pushed tag, use `gh release create <version> --verify-tag --generate-notes` and attach the matching JARs, or upload them to an existing release with `gh release upload`. Do not bump again solely to retry that final operation.

GitHub does not trigger another push workflow for commits/tags created with `GITHUB_TOKEN`. The release job itself runs all checks before publishing.
