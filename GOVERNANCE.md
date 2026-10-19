# Governance

The `robocode-dev` organization owns this repository. At least three moderators should retain ownership and CODEOWNERS review rights before ranked submissions open. One moderator approval and successful validation are required for a new bot; trusted owners may receive auto-merge eligibility for version bumps after repeated clean submissions.

Moderators maintain `bots/banned.json` through reviewed pull requests. A ban can cover an account or a bot and can be temporary. CI prevents a banned account from submitting and excludes disqualified bots from the generated catalog. Facts in the future `rumble-data` repository are never deleted merely because an entry is disqualified.

Moderators resolve name-squatting, lost-account recovery, license complaints, and appeal requests case by case. A credible copyright complaint removes the bot from the working tree and disqualifies it pending resolution.

Once each quarter, a moderator performs a fork drill: fork the repository, enable Actions, run validation, and regenerate the catalog without credentials other than the forge-provided token. Record the outcome in a GitHub issue.

## GitHub Actions permissions

The pull-request validation workflow requests `contents: read`. Public fork pull requests receive a read-only `GITHUB_TOKEN` even if a workflow asks for write access, and GitHub may require a maintainer with write permission to approve a first-time contributor's workflow run. Approve the validation run only after checking the proposed source; never grant the validation workflow write access to the upstream repository.

After a bot pull request is merged, the publisher runs on a push to `main` that changes `bots/**`. It uses the associated merged pull request to check that the submission's `validate` check passed, then checks out trusted `main` and runs static catalog generation without `--smoke`. The publisher must never execute a submitted bot launcher with its write token. Its `GITHUB_TOKEN` needs `contents: write`, `pull-requests: read`, and `checks: read`, as declared in `.github/workflows/publish-catalog.yml`; it uses no personal access token or repository secret.

For publication to work, GitHub Actions must be enabled and the repository or organization policy must permit these token scopes. The repository's **Settings → Actions → General → Workflow permissions** should allow read and write permissions for `GITHUB_TOKEN` (currently enabled for this repository); the workflow itself still grants only the scopes listed above. If `main` is protected by branch rules, allow `github-actions[bot]` to push the generated catalog files, or publishing will fail. Recheck these settings if organization policy or branch rules change.

GitHub may hold a fork contributor's validation run for approval by a maintainer with write access. The publisher does not need `pull_request_target` or a privileged fork workflow. On a `main` push, it retries the commit-to-PR lookup for up to 55 seconds to allow GitHub to publish the association. To recover a merged submission whose publication still failed, a maintainer can run **Publish bot catalog** from the `main` branch with its merged pull-request number. The dispatch verifies that it merged into `main` and that its head commit has a successful `validate` check before it regenerates the catalog using the PR author's account as owner.
