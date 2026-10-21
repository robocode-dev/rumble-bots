import dev.robocode.tankroyale.botapi.Bot;
import dev.robocode.tankroyale.botapi.events.ScannedBotEvent;

public class RumbleTestScout extends Bot {
    public static void main(final String[] args) {
        if ("1".equals(System.getenv("RUMBLE_SMOKE"))) {
            System.out.println("RUMBLE_SMOKE_READY RumbleTestScout");
            return;
        }
        new RumbleTestScout().start();
    }

    @Override
    public void run() {
        while (isRunning()) {
            turnGunRight(14);
        }
    }

    @Override
    public void onScannedBot(final ScannedBotEvent event) {
        fire(1);
    }
}
