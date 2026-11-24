## Git

Do not git commit or push unless I said so. Before a commit, tidy the
CHANGELOG as [CHANGELOG](#changelog) says.

## Status

The Android app is not started; [README.md](README.md) says what is here and
where the work begins. spectra's `AGENTS.md` applies here too once the app
builds against core: Rule 0 and Funds and keys there cover everything here.

## CHANGELOG

[CHANGELOG.md](docs/CHANGELOG.md) records what each commit changed and why,
newest first. Record a change under `## Uncommitted` as it is made. A section
written before the split covers only this repository's part of that commit;
spectra keeps its own for core.

**Tidy it before a commit.** When I am about to commit, or ask for the
CHANGELOG to be tidied, check `## Uncommitted` against the actual change
(`git diff`). Every sub-item matches one change in the diff: delete one that
does not. Add what the diff has and the entries lack. Numbers match the code,
and no claim goes beyond what was done. Then rename the heading to the
commit's title, such as `## Beta Commit 252`.

How to write it: as spectra's `AGENTS.md` "CHANGELOG" says, with one
difference: `Total:` counts .kt files, and tests passing before → after.
