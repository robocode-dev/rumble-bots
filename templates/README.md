# Start a ranked bot from a template

The templates live outside `bots/`, so they do not reserve names or enter the catalog. Each individual template includes a bot configuration, Unix and Windows launchers, pinned official Bot API dependency and source entry point. The default bot turns its gun and fires when it scans an opponent.

| Language | Copy this directory | Source file to rename | Prerequisites |
|---|---|---|---|
| Java | [java/StarterBot](java/StarterBot) | `src/StarterBot.java` | Java 25 and curl |
| C# | [csharp/StarterBot](csharp/StarterBot) | `src/StarterBot.cs` | .NET SDK 10 |
| Python | [python/StarterBot](python/StarterBot) | `src/StarterBot.py` | Python 3.11+ with the package from `requirements.txt` installed |
| TypeScript | [typescript/StarterBot](typescript/StarterBot) | `src/StarterBot.ts` | Node.js 24 and npm |

The templates pin Bot API version 1.3.1 to match the current Rumble client's engine. Java and C# restore the official package from their launchers. Python uses the environment you prepare below. TypeScript uses Node's built-in TypeScript support and installs the API and its declared `ws` runtime peer with `npm ci` in a temporary run directory. Dependency caches and generated output stay outside the submitted bot directory.

## Copy and rename

Choose one language and a unique bot name using letters and digits, beginning with a letter. The example uses Java and `MyBot`; substitute your language and name throughout.

On a Unix shell:

```shell
mkdir -p bots/java
cp -R templates/java/StarterBot bots/java/MyBot
```

On PowerShell:

```powershell
New-Item -ItemType Directory -Force bots/java | Out-Null
Copy-Item -Recurse templates/java/StarterBot bots/java/MyBot
```

In the copied directory, replace every `StarterBot` identifier in file contents with `MyBot`, including the class name, entry-point paths and smoke marker. Rename `StarterBot.json`, `StarterBot.sh`, `StarterBot.cmd` and the source file to use `MyBot`. For C#, also rename `StarterBot.csproj`. Keep the `src/` directory name unchanged.

For TypeScript, retain both `package.json` and `package-lock.json`. The package name is private and need not match the bot name. If you intentionally change a dependency version, regenerate the lockfile with `npm install --package-lock-only --ignore-scripts --no-audit --no-fund --prefix bots/typescript/MyBot` and submit it for review. The allowed direct dependency set is the official API and its declared `ws` runtime peer; adding a different library is rejected.

Edit the JSON `authors`, `description` and `license` to describe your bot. Start at version `1.0.0`. The directory, JSON filename and JSON `name` must match exactly, including case. The declared SPDX license applies to the complete submitted directory; retain a license you have the right to grant.

For Python, create an environment outside the bot directory and install the declared API. On a Unix shell:

```shell
python -m venv .venv
.venv/bin/python -m pip install -r bots/python/MyBot/requirements.txt
.venv/bin/python scripts/validate_bot.py --root . --owner YOUR_GITHUB_ACCOUNT --smoke
```

On PowerShell:

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r bots/python/MyBot/requirements.txt
.venv/Scripts/python.exe scripts/validate_bot.py --root . --owner YOUR_GITHUB_ACCOUNT --smoke
```

The validator passes its own Python interpreter to Python launchers through `RUMBLE_PYTHON`. Running it from the environment above makes both the validation script and the bot use the installed API.

## Smoke check and executable scripts

Install Git and make `sh` available; on Windows, use Git for Windows with Git Bash's `sh` on `PATH`. Run the validator from the repository root:

```shell
python scripts/validate_bot.py --root . --owner YOUR_GITHUB_ACCOUNT --smoke
```

The validator checks the whole catalog checkout, so its existing entries' runtimes must also be available. CI explicitly installs Python, Java 25, .NET 10 and Node.js 24. A template's smoke branch loads its API, prints `RUMBLE_SMOKE_READY MyBot` and exits successfully. The validator sets `RUMBLE_SMOKE=1` and allows 20 seconds per launcher. This checks the source entry point and dependency loading; it does not connect to a battle server. Test your strategy in the Tank Royale GUI before submitting it.

Initial Java and NuGet downloads require network access. If a first download exceeds the smoke timeout, run the launcher once with `RUMBLE_SMOKE=1` to populate its external cache, then rerun validation. Preserve the smoke branch when editing your strategy; normal battle execution leaves `RUMBLE_SMOKE` unset.

Stage the bot and set the Unix launcher's executable bit in Git on every platform:

```shell
git add bots/java/MyBot
git update-index --chmod=+x bots/java/MyBot/MyBot.sh
git ls-files --stage bots/java/MyBot/MyBot.sh
```

The last command must show mode `100755`. On Linux and macOS, also run `chmod +x bots/java/MyBot/MyBot.sh` before local validation. On Windows, the Git index mode is what the Linux CI checkout will receive. Keep generated dependencies and output outside `bots/`; submit no `node_modules`, virtual environment, `bin`, `obj`, JAR or DLL files.

## Submit and update

Use the [bot submission checklist](../.github/PULL_REQUEST_TEMPLATE/bot-submission.md) when opening the pull request. A moderator reviews the source; after merge, the publication workflow generates the catalog. Never edit `bots/index.json` or `bots/owners.json` in the PR.

For the next version, edit your source and bump the JSON `version`, for example from `1.0.0` to `1.0.1`, then rerun validation and submit another PR. Changing source without a version bump is rejected. Verify that the generated catalog contains the new active identity after merge; older versions remain historical records.

## JSON-only TwinDuel team

Copy [team/StarterTeam/StarterTeam.json](team/StarterTeam/StarterTeam.json) into `bots/<platform>/<TeamName>/<TeamName>.json`. Replace `StarterTeam` with the team name, set your authors and license, and put the full active `name version` identity in each of the two `teamMembers` slots. Both slots may name the same individual bot. Members can come from different languages.

The team directory contains only that JSON file: no `src/`, project file or launchers. Its member bots must already be in the catalog or in the same validated submission. A later member-version change requires an explicit update to `teamMembers` and a bump of the team's own version. Use the team-specific items in the submission checklist.
