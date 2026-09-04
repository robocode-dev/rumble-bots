# Contributing a ranked bot

The full walkthrough for bot authors is [Submit a bot to the Rumble](https://robocode.dev/rumble/bot-author-guide). The rules below are the review contract enforced by this repository.

## Submit source that can run safely

Bots must use the official Tank Royale Bot API for Java, C#, Python, or TypeScript and run through the checked-in booter-convention scripts. Submit source code only.

Do not include binary artifacts, generated dependency folders, custom protocol clients, process launchers, raw sockets, or code that writes outside its temporary directory. A validator diagnostic for a restricted construct must be resolved before merge.

## Declare the license

Each bot directory's `license` field grants the selected license for the complete directory. Allowed SPDX identifiers are `MIT`, `Apache-2.0`, `BSD-3-Clause`, and `GPL-3.0-or-later`.

By opening a pull request, you certify that you have the right to publish the submitted code under that license, in the spirit of the Developer Certificate of Origin. A full license file is optional.

## Respect ownership and versions

The first merged pull request for a bot or team name reserves that name for the submitting GitHub account. Only that account, or an account later registered to the same owner in `bots/owners.json`, may submit another version.

A source change requires a version increase. Only the latest version remains active in the ranked pool; previous versions and their results remain part of the history.

Each owner has five active entry slots by default. An individual bot or a TwinDuel team each uses one slot. A version update to an existing entry does not use another slot.

## Define TwinDuel teams

A TwinDuel team configuration contains exactly two member slots in `teamMembers`, using active `<name> <version>` identities. Both slots may name the same individual entry. The team directory contains only `<TeamName>.json`, and teams cannot contain other teams.

The catalog derives the team's platform from its members. A member version is immutable team membership, so changing either member version requires a new team version.

## Validate and open the pull request

Run:

```shell
python scripts/validate_bot.py --root . --owner <your-github-account> --smoke
```

CI is authoritative and repeats the validation. A moderator also reviews every new bot, especially first submissions. A green check is required but does not replace that review.

Do not edit `bots/index.json` or `bots/owners.json`; CI regenerates them after merge. Do not include secrets in source, bot configuration, issues, or pull-request text.

Copyright complaints, unsafe code, impersonation, ownership disputes, and appeals are handled under [GOVERNANCE.md](GOVERNANCE.md).
