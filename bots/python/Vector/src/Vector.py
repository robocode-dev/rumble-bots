"""A compact Rumble catalog bot used for the first live ranked 1v1 proof."""

import os


if os.environ.get("RUMBLE_SMOKE") == "1":
    print("RUMBLE_SMOKE_READY Vector")
    raise SystemExit(0)

from robocode_tank_royale.bot_api.bot import Bot
from robocode_tank_royale.bot_api.events import ScannedBotEvent


class Vector(Bot):
    """Sweeps the radar while travelling in a wide arc."""

    def run(self) -> None:
        self.turn_radar_right(360)
        while self.running:
            self.set_turn_right(30)
            self.forward(10_000)

    def on_scanned_bot(self, event: ScannedBotEvent) -> None:
        """Aim at the detected opponent and fire before continuing the scan."""
        self.turn_gun_right(self.gun_bearing_to(float(event.x), float(event.y)))
        self.fire(3)
        self.rescan()


def main() -> None:
    Vector().start()


if __name__ == "__main__":
    main()
