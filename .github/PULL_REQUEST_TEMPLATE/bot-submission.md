## Bot submission

- [ ] I added source only under `bots/<platform>/<BotName>/`.
- [ ] For an individual bot, I included `<BotName>.json`, `<BotName>.sh`, `<BotName>.cmd`, and source, with the `.sh` committed as executable (`100755`).
- [ ] For a TwinDuel team, the directory contains only `<TeamName>.json`, with exactly two active `name version` member identities and no launchers or source.
- [ ] The config declares an allowed SPDX `license` and the source uses an official Tank Royale Bot API.
- [ ] I ran `python scripts/validate_bot.py --root . --owner <my-forge-account> --smoke` successfully.
- [ ] For a source or member change to an existing entry, I increased its version.
- [ ] I certify I can publish this source under the declared license.
