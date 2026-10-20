import dev.robocode.tankroyale.runner.BattleResults;
import dev.robocode.tankroyale.runner.BattleRunner;
import dev.robocode.tankroyale.runner.BattleSetup;
import dev.robocode.tankroyale.runner.BotEntry;

import java.nio.file.Path;
import java.util.List;

/** Runs submitted source against a trusted reference through the real embedded server. */
public final class BattleSmoke {
    public static void main(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Expected submitted path, reference path, bot name and version");
        }
        var setup = BattleSetup.oneVsOne(builder -> {
            builder.setNumberOfRounds(1);
            builder.setReadyTimeoutMicros(10_000_000);
        });
        BattleResults results;
        try (BattleRunner runner = BattleRunner.create(builder -> builder.embeddedServer())) {
            results = runner.runBattle(setup, List.of(
                    BotEntry.of(Path.of(args[0])), BotEntry.of(Path.of(args[1]))));
        }
        if (results.getNumberOfRounds() != 1 || results.getResults().size() != 2 ||
                results.getResults().stream().noneMatch(result ->
                        result.getName().equals(args[2]) && result.getVersion().equals(args[3]))) {
            throw new IllegalStateException("The submitted bot did not complete one full round");
        }
        System.out.println("RUMBLE_BATTLE_READY " + args[2] + " " + args[3]);
    }
}
