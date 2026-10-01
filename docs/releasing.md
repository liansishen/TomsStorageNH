# Release workflow

Releases follow this sequence: open a pull request to `master`, wait for its CI checks to pass, merge the pull request, wait for CI on the merge commit, then push a version tag on that commit.

## Verify and merge

1. Create a working branch and open a pull request targeting `master`.
2. Wait for the `CI` workflow to finish successfully. It runs `setupCIWorkspace build`, including Java compilation, tests, formatting checks and Modernity resource-pack packaging.
3. Merge the pull request after verification. Wait for the resulting commit's `CI` run on `master` to finish successfully.
4. Update the local `master` branch and confirm the commit to be released matches the verified merge commit.

## Publish a version

Choose the next available version and tag the verified merge commit. For example:

```shell
git switch master
git pull --ff-only
git tag -a 0.2.2 -m "Release 0.2.2"
git push origin 0.2.2
```

The `Release tagged build` workflow verifies that the tag points to a merged pull request's commit on `master` with successful master-branch CI. It then publishes the mod through the GTNH release workflow and uploads the Modernity adapter to the same GitHub Release.

Wait for the entire release workflow to succeed and verify these four attachments:

- `tomsstorage-<version>.jar`
- `tomsstorage-<version>-dev.jar`
- `tomsstorage-<version>-sources.jar`
- `Modernity-TomsStorage-<version>.zip`

The resource-pack version is set explicitly from the release tag. Its ZIP contains `pack.mcmeta`, `pack.png` and `assets/` at the archive root, plus the pack's documentation.

## Build the adapter locally

```shell
./gradlew -PresourcePackVersion=0.2.2 packageResourcePacks
```

The ZIP is written to `build/resourcepacks/`. A regular `build` or `assemble` also packages the adapter, using the Gradle project version when `resourcePackVersion` is omitted. ZIP entry ordering and timestamps are reproducible.
