package fnl.bots.framework.pipeline;

import fnl.bots.framework.sense.GameInfo;
import fnl.bots.framework.sense.ScanRecord;
import fnl.bots.framework.sense.TurnInput;

/**
 * Runs a bot's pipeline on synthetic turns before the bot connects. Under the Rumble's source launcher every class
 * is compiled in memory the first time it is loaded, and the JIT has not run yet; doing both before the game starts
 * keeps round 1 from skipping turns. Platform-free, allocation outside the measured game.
 */
public final class Warmup {

    private Warmup() {
    }

    /**
     * Runs {@code turns} synthetic turns in total, split over a 1v1, a melee and a 2v2 game, so each strategy set a
     * {@code Mode} can pick is compiled and warm before the bot connects.
     */
    public static void run(BotProgram program, int turns) {
        int perGame = Math.max(1, turns / 3);
        runGame(program, perGame, new int[0], 1);
        runGame(program, perGame, new int[0], 2);
        runGame(program, perGame, new int[]{2}, 2);
    }

    private static void runGame(BotProgram program, int turns, int[] teammateIds, int enemies) {
        GameInfo game = new GameInfo();
        game.myId = 1;
        game.arenaWidth = 800;
        game.arenaHeight = 600;
        game.numberOfRounds = 1;
        game.gunCoolingRate = 0.1;
        game.turnTimeoutMicros = 30_000;
        game.teammateIds = teammateIds;
        int firstEnemyId = 2 + teammateIds.length;
        TurnInput input = new TurnInput();
        Pipeline pipeline = program.createPipeline(game, input);
        pipeline.onRoundStart();
        for (int turn = 1; turn <= turns; turn++) {
            double angle = turn * 0.05;
            input.clearEvents();
            input.round = 1;
            input.turn = turn;
            input.x = 400;
            input.y = 300;
            input.gunHeading = angle;
            input.radarHeading = angle * 2;
            input.energy = 100;
            input.gunHeat = turn % 16 == 0 ? 0 : 0.5;
            input.enemyCount = enemies;
            addScan(input, firstEnemyId, 400 + Math.cos(angle) * 250, 300 + Math.sin(angle) * 200);
            if (enemies > 1 && turn % 3 == 0) {
                addScan(input, firstEnemyId + 1, 400 - Math.cos(angle) * 150, 300 + Math.sin(angle) * 100);
            }
            pipeline.step(input);
        }
    }

    private static void addScan(TurnInput input, int id, double x, double y) {
        ScanRecord scan = input.addScan();
        scan.botId = id;
        scan.x = x;
        scan.y = y;
        scan.energy = 100;
    }
}
