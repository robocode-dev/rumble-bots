import { Bot, type ScannedBotEvent } from "@robocode.dev/tank-royale-bot-api";

class StarterBot extends Bot {
    override run() {
        while (this.isRunning()) {
            this.turnGunRight(10);
        }
    }

    override onScannedBot(event: ScannedBotEvent) {
        this.fire(1);
    }
}

if (process.env.RUMBLE_SMOKE === "1") {
    console.log("RUMBLE_SMOKE_READY StarterBot");
} else {
    new StarterBot().start();
}
