# Governance

The `robocode-dev` organization owns this repository. At least three moderators should retain ownership and CODEOWNERS review rights before ranked submissions open. One moderator approval and successful validation are required for a new bot; trusted owners may receive auto-merge eligibility for version bumps after repeated clean submissions.

Moderators maintain `bots/banned.json` through reviewed pull requests. A ban can cover an account or a bot and can be temporary. CI prevents a banned account from submitting and excludes disqualified bots from the generated catalog. Facts in the future `rumble-data` repository are never deleted merely because an entry is disqualified.

Moderators resolve name-squatting, lost-account recovery, license complaints, and appeal requests case by case. A credible copyright complaint removes the bot from the working tree and disqualifies it pending resolution.

Once each quarter, a moderator performs a fork drill: fork the repository, enable Actions, run validation, and regenerate the catalog without credentials other than the forge-provided token. Record the outcome in a GitHub issue.
