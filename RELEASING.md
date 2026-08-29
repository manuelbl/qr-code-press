# Releasing

QR Code Press is published to Maven Central under the verified namespace `net.codecrete`. A release
is a single `workflow_dispatch` of [`.github/workflows/release.yml`](.github/workflows/release.yml),
with two manual gates that both come after the artifacts exist.

## What a release changes

The repository documents one version and builds another. After a release of `0.9.0`, `main` looks
like this:

| | Version |
|---|---|
| Every `pom.xml` (library, examples, profiling) | `0.10.0-SNAPSHOT` |
| The dependency snippet in `README.md` | `0.9.0` |
| The example links in `README.md` | `.../tree/v0.9.0/examples/...` |

The poms serve contributors, who build against the unreleased library. The README serves users, who
install a release, and its example links are pinned to the tag so that following one lands on
examples that resolve that release from Maven Central. The tag `v0.9.0` itself points at a commit
whose poms all carry `0.9.0`.

`release/set-version.sh` is what keeps that straight, and it is the only thing that edits a version.
It is a pure, idempotent text rewrite that touches no git state, so it can be run locally to inspect
a release diff before dispatching:

```sh
./release/set-version.sh 0.9.0                        # poms and README
./release/set-version.sh --poms-only 0.10.0-SNAPSHOT  # poms only, README keeps the release
git diff
git checkout -- .
```

It discovers the poms rather than listing them, so a new example under `examples/` is picked up on
its own. It deliberately leaves `.claude/skills/adding-an-example/SKILL.md` alone, whose version is a
template for contributors working on `main` and must stay a snapshot.

It reads back every version it writes and fails if one does not match. A text rewrite is silent
when its pattern stops matching — after a pom is reformatted, or a README link reworded — and a
version left unchanged would otherwise reach the tag with a green build.

## One-time setup

1. **Publish the signing key.** Upload the public half of the signing key to a keyserver Central
   checks, for instance `keys.openpgp.org` or `keyserver.ubuntu.com`:

   ```sh
   gpg --keyserver keys.openpgp.org --send-keys <key-id>
   ```

2. **Create the environment.** In the repository settings, add an environment named
   `maven-central`. Require a reviewer, and restrict its deployment branches to `main` so that no
   branch or fork can reach the signing key.

3. **Add the environment secrets:**

   | Secret | What it is |
   |---|---|
   | `GPG_PRIVATE_KEY` | ASCII-armored private key, `gpg --armor --export-secret-keys <key-id>` |
   | `GPG_PASSPHRASE` | passphrase of that key |
   | `CENTRAL_TOKEN_USERNAME` | user token name from the Central Portal |
   | `CENTRAL_TOKEN_PASSWORD` | user token password from the Central Portal |

   The portal tokens come from **View Account → Generate User Token** at
   [central.sonatype.com](https://central.sonatype.com).

## Releasing

1. Make sure `main` is green and holds everything that goes into the release.
2. Run the **Release** workflow from `main`, with the release version, for instance `0.9.0`. Leave
   *next-version* empty to get the next minor, `0.10.0-SNAPSHOT`; in `0.x` that is where breaking
   changes go, and they are still expected.
3. The workflow refuses a version that is not `x.y.z`, a tag that already exists, and a dispatch
   from a branch other than `main`. It then builds and tests the library on JDK 17 and 25 against
   the release version.
4. **Approve the `maven-central` environment.** The publish job signs, builds the source and javadoc
   jars, and uploads to the Central Portal with `autoPublish` off. Nothing is public yet.
5. The workflow pushes the release commit, the tag, and the commit that moves `main` on to the next
   snapshot, and creates a **draft** GitHub Release with generated notes.
6. **Confirm the deployment** in the Central Portal, at *Deployments*. A published release can never
   be deleted or overwritten.
7. Edit the draft release notes and publish the release.
8. Go to https://javadoc.io/versions/net.codecrete.qrcodepress/qr-code-press, press *Sync from Maven*
   and upload the new version's Javadoc.

## If it fails

Nothing is pushed to git until after the upload, so a failure before step 5 leaves no trace in the
repository.

| Where | What to do |
|---|---|
| Before the upload | Fix and re-run. Nothing happened. |
| Upload failed validation | Read the error in the Central Portal, drop the deployment, fix, re-run. |
| After the upload, before the tag | Drop the unpublished deployment in the portal, then re-run. |
| `main` moved during the run | The workflow refuses to push. Drop the deployment and re-run. |
| Already confirmed in the portal | The version is permanent. Release the fix as the next patch. |

## Scope of the release build

The release workflow verifies the **library only**, on both supported JDKs. The examples and the
profiling harness are not built there: the push that ends the release runs
[`build.yml`](.github/workflows/build.yml), which builds them against the new snapshot. The gap this
leaves is narrow — a version rewrite that breaks an example pom would ship in the tag and be caught
one commit later on `main`. Adding a new example therefore touches `build.yml` only, as
`.claude/skills/adding-an-example/SKILL.md` describes.
