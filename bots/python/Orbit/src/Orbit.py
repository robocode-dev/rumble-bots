"""Minimal smokeable Rumble bot entry point."""

import os


if os.environ.get("RUMBLE_SMOKE") == "1":
    print("RUMBLE_SMOKE_READY Orbit")
    raise SystemExit(0)

from robocode_tank_royale.bot_api.bot import Bot


class Orbit(Bot):
    """A deliberately small official-API bot used to exercise the submission pipeline."""

    def run(self) -> None:
        self.turn_radar_left(360)


def main() -> None:
    Orbit().start()


if __name__ == "__main__":
    main()
