# How CoinePro is versioned

One file is edited — `version.properties`. Everything else is derived from it.

```
version.properties          MAJOR / MINOR / PATCH / PRE_RELEASE
        │
        ├── scripts/release/version.py   →  name, full, tag, code   (CI, and the command line)
        └── app/build.gradle.kts         →  versionName, versionCode (the build)
```

## The two numbers Android carries

Android asks for two versions and they answer different questions.

`versionName` is for the reader. It is what the profile screen shows, what a release is called, and
what somebody says out loud when they report a bug.

`versionCode` is for the package manager, and it is asked exactly one question: **is this higher
than the code already installed?** If it is not, the install is refused with "app not installed"
and nothing anywhere says why.

Tracking those two by hand fails in one direction. Somebody bumps the name, forgets the code, and
every device in the field quietly stops accepting updates — with no error the owner will ever see,
because the failure happens on other people's phones. So only the name is written down, and the
code is computed:

```
versionCode = MAJOR × 10,000,000
            + MINOR ×    100,000
            + PATCH ×      1,000
            + BUILD
```

## Why those widths

Read the formula right to left. Each field is wide enough to contain everything the fields below it
can reach, so **a bump anywhere is strictly larger than anything reachable underneath it**.

| Field | Range | Weight | Contains |
| --- | --- | --- | --- |
| `BUILD` | 0–999 | 1 | — |
| `PATCH` | 0–99 | 1,000 | 999 builds |
| `MINOR` | 0–99 | 100,000 | 99 patches × their builds = 99,999 |
| `MAJOR` | 0–200 | 10,000,000 | 99 minors × their patches = 9,999,999 |

`MAJOR ≤ 200` is not a preference. `200 × 10,000,000 = 2,000,000,000`, and Google Play rejects any
`versionCode` above `2,100,000,000`. The top field is bounded by the platform, not by taste.

Both `version.py` and `app/build.gradle.kts` enforce those ranges, and the build fails rather than
silently wrapping — a wrap would produce a *lower* code, which is the one outcome that breaks
updates on every device at once.

## BUILD

`BUILD` is **the number of commits since `version.properties` last changed.**

That means every push produces a code strictly above the one before it without anyone having to
remember anything, and the moment the version is bumped the counter resets — the bump having
already jumped further than 999 builds could.

It is asked of git rather than stored, because a stored counter would have to be committed, and
committing it would change `version.properties`, which resets the very thing it counts.

Consequences worth knowing:

* CI checks out with `fetch-depth: 0`. A shallow clone cannot count commits, and `actions/checkout`
  fetches one commit by default.
* Gradle uses `BUILD = 0`. A build that shells out to git behaves differently in a source tarball
  than in a checkout; CI computes the real number and passes the whole code in with `-P`. A local
  build therefore gets the base code, which is correct — a local build is not something anybody
  installs over.
* Past 999 commits without a version bump the build **fails**, and says to bump. That is the right
  failure: 999 commits with no release worth naming is the actual problem.

## The three names

`version.py` emits three spellings of the same release, because three readers want different
things.

| Output | Example | Who reads it |
| --- | --- | --- |
| `name` | `1.0.0` | `CHANGELOG.md`, and people |
| `full` | `1.0.0+4` | the device — `versionName`, and the APK's filename |
| `tag` | `v1.0.0-b4` | git, and the GitHub Release |

`+4` is semver *build metadata*: by the specification it does not affect precedence, which is
exactly right here, because precedence is already carried by `versionCode`. It is on the device so
that a bug report names the exact build rather than the nearest version.

The tag says the same thing in the characters a refname and a URL both accept unescaped. `+` is
legal in a git tag and then needs percent-encoding in every link to it.

## Bumping

```bash
python3 scripts/release/version.py --bump patch    # a fix; nothing new to learn
python3 scripts/release/version.py --bump minor    # a capability that was not there before
python3 scripts/release/version.py --bump major    # something a reader has to relearn
```

Then write the entry in `CHANGELOG.md` — under its number, saying what changed and for whom. A
version with no changelog entry is a number nobody can act on.

Never edit a field downwards. Android will not install a lower code over a higher one, and the only
way back from a version that went out too high is a new, higher one.

## Pre-releases

`PRE_RELEASE=rc.1` makes the name `1.2.0-rc.1`. It changes the name only: the code is unaffected,
because Android has nowhere to put a pre-release tag and a release candidate still has to install
over the build before it. Clear it to `PRE_RELEASE=` for the final.

## Checking

```bash
python3 scripts/release/version.py            # 1.0.0 (10000000)
python3 scripts/release/version.py --json     # every field
python3 scripts/release/version.py --check    # validate, print nothing
```

`scripts/release/validate-version.sh` stays as the gate for `internal-release.yml`, which takes an
explicit name and code as workflow inputs for a Play upload rather than reading this file.

## What was here before

CI used to compute `run_number + 1000` and call the result `0.1.<code>`. It was monotonic, which
was the only thing it had to be — but the number told the reader nothing. `0.1.1004` was not a
patch of `0.1.1003`; it was whatever happened to land that day.

The `+1000` offset existed because APKs handed over by hand carried `versionCode 1`, and every
automated build had to sit above them. `1.0.0` lands at `10,000,000`, which clears the whole of
that old series with a great deal of room, so nothing in the field is stranded by the change.
`version.py` asserts it rather than trusting it.
