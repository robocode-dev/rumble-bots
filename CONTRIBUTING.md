# Contributing a ranked bot

Submit source code only. Bots must use an official Tank Royale Bot API for Java, C#, Python, or TypeScript and run through the checked-in booter-convention scripts. Do not submit binary artifacts, generated dependency folders, custom protocol clients, process launchers, raw sockets, or code that writes outside its temporary directory.

Each bot directory's `license` field is its license grant for the complete directory. By opening a pull request, you certify that you have the right to publish the submitted code under that SPDX license, in the spirit of the Developer Certificate of Origin. Include a full license file if it helps users, but it is optional.

The first merged pull request for a bot name reserves that name for its submitting forge account. Only that account, or an account later registered in `bots/owners.json` by an already registered account, may submit later versions. A source change requires a version bump; only the latest version remains active. Each owner has five active entries by default, including a TwinDuel team.

Use `python scripts/validate_bot.py --root . --owner <your-forge-account> --smoke` before opening a pull request. CI is authoritative. A green check does not replace moderator review, especially for a first submission.

Copyright complaints, unsafe code, impersonation, and disputes are handled under [GOVERNANCE.md](GOVERNANCE.md). Do not include secrets in source, bot configuration, issues, or pull-request text.
