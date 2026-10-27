import dev.robocode.tankroyale.botapi.Bot;
import dev.robocode.tankroyale.botapi.events.ScannedBotEvent;

public class RumbleOnboardingScout extends Bot {
    public static void main(final String[] args) {
        if ("1".equals(System.getenv("RUMBLE_SMOKE"))) {
            System.out.println("RUMBLE_SMOKE_READY RumbleOnboardingScout");
            return;
        }
        new RumbleOnboardingScout().start();
    }

    @Override
    public void run() {
        while (isRunning()) {
            turnGunRight(10);
        }
    }

    @Override
    public void onScannedBot(final ScannedBotEvent event) {
        fire(1);
    }
}
