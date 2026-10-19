import os

from robocode_tank_royale.bot_api.bot import Bot
from robocode_tank_royale.bot_api.events import ScannedBotEvent


class StarterBot(Bot):
    def run(self) -> None:
        while self.running:
            self.turn_gun_right(10)

    def on_scanned_bot(self, event: ScannedBotEvent) -> None:
        self.fire(1)


def main() -> None:
    if os.environ.get("RUMBLE_SMOKE") == "1":
        print("RUMBLE_SMOKE_READY StarterBot")
        return
    StarterBot().start()


if __name__ == "__main__":
    main()
