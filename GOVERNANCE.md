# Governance

The `robocode-dev` organization owns the Rumble bot catalog. Moderators review submissions and policy changes through pull requests. At least three moderators should retain organization ownership and CODEOWNERS review rights.

## Submission decisions

A new bot or TwinDuel team requires successful validation and one moderator approval. First submissions receive closer review. Trusted owners may receive auto-merge eligibility for later version bumps after repeated clean submissions.

Moderators apply the published source, dependency, licensing, ownership, versioning, and slot rules consistently. Passing automation is required, but moderators remain responsible for questions that need human judgment, including impersonation, confusing names, suspicious code, and authorship claims.

## Bans, complaints, and appeals

Moderators maintain `bots/banned.json` through reviewed pull requests. A ban may cover an account or bot and may be temporary. CI prevents a banned account from submitting and excludes disqualified bots from the generated catalog. Existing result facts in `rumble-data` are not deleted merely because an entry is disqualified.

Moderators resolve name squatting, lost-account recovery, license complaints, and appeals case by case. A credible copyright complaint removes the bot from the working tree and disqualifies it while the complaint is resolved. Record the reason and outcome in the pull-request history without publishing sensitive information.

## Continuity

Once each quarter, a moderator forks the repository, enables Actions, runs validation, and regenerates the catalog using only the forge-provided token. Record the outcome in a GitHub issue. This drill verifies that the catalog can be recovered from public source and documentation.

For the day-to-day review checklist, see [Moderate the Rumble](https://robocode.dev/rumble/moderator-guide).
