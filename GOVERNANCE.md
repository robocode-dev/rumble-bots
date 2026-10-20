# Governance

The `robocode-dev` organization owns this repository. Flemming is currently the human moderator; recruiting at least three people with ownership and CODEOWNERS review rights remains a future goal, not a condition for accepting ranked submissions. GitHub moderation automatically approves and merges a new bot or version update only when the pull request changes one bot directory, the source and dependency checks find no restricted behavior, and the bot completes one round against a trusted reference. A failed bot receives a changes-requested review with a diagnostic. Security uncertainty, infrastructure failure, TwinDuel team metadata, and non-bot changes stay blocked for Flemming's decision.

Moderators maintain `bots/banned.json` through reviewed pull requests. A ban can cover an account or a bot and can be temporary. CI prevents a banned account from submitting and excludes disqualified bots from the generated catalog. Facts in the future `rumble-data` repository are never deleted merely because an entry is disqualified.

Moderators resolve name-squatting, lost-account recovery, license complaints, and appeal requests case by case. A credible copyright complaint removes the bot from the working tree and disqualifies it pending resolution.

Once each quarter, a moderator performs a fork drill: fork the repository, enable Actions, run validation, and regenerate the catalog. Record the outcome in a GitHub issue. A fork can run the source validator without moderator credentials; automatic review and merge require the installed moderation App.

## Moderator App registration

Create the organization-owned GitHub App at **robocode-dev → Settings → Developer settings → GitHub Apps → New GitHub App**. Record the following registration values so a replacement moderator can reproduce the setup:

| Field | Value |
| --- | --- |
| GitHub App name | `Tank Royale Rumble Moderator` |
| Description (displayed to users) | `Reviews Rumble bot submissions, runs a test battle, and automatically merges submissions that pass. Escalates uncertain cases to the human moderator.` |
| Homepage URL | `https://github.com/robocode-dev/rumble-bots` |
| Redirect URI / Callback URL | Leave empty; the workflow uses installation tokens, not user OAuth. |
| Request user authorization during installation | Off. |
| Enable Device Flow | Off. |
| Setup URL | Leave empty. |
| Webhook | Inactive; this App receives no webhook events. |
| Where can this GitHub App be installed? | Only on this account (`robocode-dev`). |

Set repository permissions **Contents**, **Pull requests**, **Commit statuses**, and **Issues** to **Read & write**. Leave other permissions at their defaults and subscribe to no webhook events. Create the App, then install it with **Only select repositories → `robocode-dev/rumble-bots`**. Do not grant it access to `tank-royale`, `rumble-data`, or all organization repositories.

Generate one private key under the App's **Credentials → Key pairs** page. Store the App ID in the `rumble-bots` Actions secret `RUMBLE_MODERATOR_APP_ID` and the full PEM contents in `RUMBLE_MODERATOR_PRIVATE_KEY` at **rumble-bots → Settings → Secrets and variables → Actions → Repository secrets**. Never commit the PEM file. After the secrets are saved, check the installation and secret names, then delete the downloaded local PEM copy if it is no longer needed. Rotate the key through the same App settings and update the secret before revoking the old key.

The organization installation created on 2026-10-20 has App ID `5402066`, slug `tank-royale-rumble-moderator`, and installation ID `173538338`. GitHub reports selected-repository access and the four write permissions above. Both named repository secrets were set on 2026-10-20; their values are not recorded here. The [documentation PR's moderation run](https://github.com/robocode-dev/rumble-bots/actions/runs/39499311781) successfully obtained an App token, set `bot-moderation` to pending, and commented as `tank-royale-rumble-moderator[bot]` because the PR changes protected documentation.

## GitHub Actions permissions

The `pull_request_target` moderation workflow runs trusted code from `main` for every pull request. Its assessment job has a read-only token and no secrets. It fetches the exact PR commit as data, checks eligibility and source, then runs one real Battle Runner round in a pinned, non-root container with a read-only bot mount, limited resources, no capabilities and no GitHub credentials. Its separate verdict job never checks out or executes PR content. The repository's Actions event policy must explicitly allow this trusted workflow's `pull_request_target` event. Restrict this policy to this workflow; never run untrusted source in the verdict job.

Repository Actions policy `8130`, **Allow trusted bot moderation event**, was activated on 2026-10-20. It targets only `.github/workflows/moderate.yml` and permits only `pull_request_target` for that workflow.

Install a dedicated GitHub App on **only** `robocode-dev/rumble-bots`, with Contents, Pull requests, Commit statuses and Issues repository permissions set to write. Store its ID and private key as `RUMBLE_MODERATOR_APP_ID` and `RUMBLE_MODERATOR_PRIVATE_KEY` repository secrets. The verdict job obtains a short-lived installation token. It approves and merges only the tested head SHA after the required `bot-moderation` status succeeds; new commits invalidate that result. A failed bot gets a failing status and `REQUEST_CHANGES`. Ambiguous and non-bot PRs receive a pending status and a comment naming Flemming and the reason. Flemming may review a pending PR and comment `/moderation approve <reason>` to make its current head green; Flemming still merges that PR manually. Do not add the moderation App to branch-rule bypasses.

Protect `main` with a required `bot-moderation` status from the moderation App, a pull-request requirement, and code-owner review for the protected paths listed in `CODEOWNERS`. Bot directories have no human code owner, so a clean bot can merge after the App's approval. Flemming needs a pull-request-only bypass for policy changes he authors, because GitHub will not count an author's approval of their own pull request; use this exception only after human review of the change. Do not grant any bypass to the moderation App. Verify the App, status source, branch rule and publisher on a test PR before enabling auto-merge. The automatic security screen is a conservative filter, not proof that arbitrary bot source is harmless; clients still execute bots in their own sandbox.

**Deployment hold:** The current catalog publisher pushes generated files directly to `main` using `GITHUB_TOKEN`. GitHub rejected `github-actions[bot]` as a ruleset bypass actor with HTTP 422 (`Actor GitHub Actions integration must be part of the ruleset source or owner organization`). No required-status branch ruleset is active yet. The publisher must move to a protected integration path before this rule can be enabled; until then, do not claim that a failing bot is blocked from manual merge by GitHub.

After a bot pull request is merged by the App, the publisher runs on a push to `main` that changes `bots/**`. It checks that the merged PR's exact head had a successful `bot-moderation` status, then checks out trusted `main` and runs static catalog generation without executing a submitted launcher. Its `GITHUB_TOKEN` needs `contents: write`, `pull-requests: read`, and `statuses: read`, as declared in `.github/workflows/publish-catalog.yml`; it uses no personal access token or repository secret.

For publication to work, GitHub Actions must be enabled and the repository or organization policy must permit these token scopes. The repository's **Settings → Actions → General → Workflow permissions** should allow read and write permissions for `GITHUB_TOKEN` (currently enabled for this repository); the workflow itself still grants only the scopes listed above. If `main` is protected by branch rules, allow `github-actions[bot]` to push the generated catalog files, or publishing will fail. Recheck these settings if organization policy or branch rules change.

On a `main` push, the publisher retries the commit-to-PR lookup for up to 55 seconds to allow GitHub to publish the association. To recover a merged submission whose publication still failed, a maintainer can run **Publish bot catalog** from `main` with its merged PR number. The dispatch verifies that it merged into `main` and that its head commit passed moderation before it regenerates the catalog using the PR author's account as owner.
