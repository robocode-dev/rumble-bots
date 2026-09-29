package fnl.bots.framework.stats;

import fnl.bots.framework.blackboard.EnemyWavesView;
import fnl.bots.framework.blackboard.GunStatsView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * Writes a game's stats as one JSON file, for the battle harness. It writes only when the environment variable
 * {@value #ENV_DIR} is set and points inside {@code java.io.tmpdir}, so ranked Rumble runs never write anything and
 * the bot never writes outside its temporary directory.
 */
public final class StatsWriter {

    public static final String ENV_DIR = "RETCON_STATS_DIR";

    private StatsWriter() {
    }

    /** Returns the stats directory, or null when stats are disabled or the directory is not inside the temp dir. */
    public static Path statsDirectory() {
        String dir = System.getenv(ENV_DIR);
        if (dir == null || dir.isBlank()) {
            return null;
        }
        Path path = Path.of(dir).toAbsolutePath().normalize();
        Path temp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        return path.startsWith(temp) ? path : null;
    }

    /**
     * Returns the file this game's stats go to, or null when stats are disabled. One file per game, rewritten after
     * every round, so the harness still gets the stats if the bot process is stopped right as the game ends.
     */
    public static Path statsFile(String botName, int botId) {
        Path dir = statsDirectory();
        if (dir == null) {
            return null;
        }
        return dir.resolve(botName.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + botId + "-" + System.nanoTime()
                + ".json");
    }

    /** Atomically (re)writes the stats file; does nothing when {@code file} is null. */
    public static void write(Path file, String botName, int botId, int roundsRecorded, TurnStats stats,
                             TimingProbe probe, EnemyWavesView waves, WaveProbe waveProbe,
                             MovementProbe movementProbe, GunStatsView gunStats) {
        if (file == null) {
            return;
        }
        String json = "{"
                + "\"bot\":\"" + botName.replace("\"", "'") + "\","
                + "\"botId\":" + botId + ","
                + "\"roundsRecorded\":" + roundsRecorded + ","
                + "\"turnTimeoutMicros\":" + stats.turnTimeoutMicros() + ","
                + "\"computeBudgetMicros\":" + stats.computeBudgetMicros() + ","
                + "\"turns\":" + stats.turns() + ","
                + "\"computeP50Micros\":" + stats.percentileMicros(50) + ","
                + "\"computeP99Micros\":" + stats.percentileMicros(99) + ","
                + "\"computeMaxMicros\":" + stats.maxMicros() + ","
                + "\"overBudgetTurns\":" + stats.overBudget() + ","
                + "\"skippedTurns\":" + stats.skippedTotal() + ","
                + "\"skippedPerRound\":" + Arrays.toString(stats.skippedPerRound()) + ","
                + "\"skippedTurnList\":\"" + stats.skippedTurnList() + "\","
                + "\"computeHistogram10us\":\"" + stats.histogram() + "\","
                + "\"timing\":{"
                + "\"firesChecked\":" + probe.firesChecked + ","
                + "\"firesNotCreated\":" + probe.firesNotCreated + ","
                + "\"createdTurnMatches\":" + probe.createdTurnMatches + ","
                + "\"originMatches\":" + probe.originMatches + ","
                + "\"originMatchesAfterFirstMove\":" + probe.originMatchesAfterFirstMove + ","
                + "\"headingMatches\":" + probe.headingMatches + ","
                + "\"stillHitsChecked\":" + probe.stillHitsChecked + ","
                + "\"stillHitMatches\":" + probe.stillHitMatches + ","
                + "\"hitsOnMovingTargets\":" + probe.hitsOnMovingTargets + ","
                + "\"mismatches\":\"" + probe.mismatches.toString().replace("\"", "'") + "\""
                + "},"
                + "\"waves\":{"
                + "\"firesDetected\":" + waves.firesDetected() + ","
                + "\"unanchoredShots\":" + waves.unanchoredShots() + ","
                + "\"hitsTaken\":" + waves.hitsTaken() + ","
                + "\"hitsOnWave\":" + waves.hitsOnWave() + ","
                + "\"wavePowerCorrected\":" + waves.powerCorrected() + ","
                + "\"waveHitsExact\":" + waveProbe.exact + ","
                + "\"waveHitsOffByOneStep\":" + waveProbe.offByOneStep + ","
                + "\"waveHitsOtherResidual\":" + waveProbe.otherResidual + ","
                + "\"waveMismatches\":\"" + waveProbe.mismatches.toString().replace("\"", "'") + "\""
                + "},"
                + "\"movement\":{"
                + "\"moveTurnsChecked\":" + movementProbe.turnsChecked + ","
                + "\"moveMatches\":" + movementProbe.matches + ","
                + "\"moveWallHitsChecked\":" + movementProbe.wallHitsChecked + ","
                + "\"moveNotChecked\":" + movementProbe.notChecked + ","
                + "\"moveMismatches\":\"" + movementProbe.mismatches.toString().replace("\"", "'") + "\""
                + "},"
                + "\"gun\":{"
                + "\"bulletsFired\":" + probe.bulletsFired + ","
                + "\"bulletHits\":" + probe.bulletHits + ","
                + "\"virtualGuns\":\"" + virtualGuns(gunStats) + "\""
                + "}}";
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Could not write stats: " + e);
        }
    }

    /** "Model:hits/shots" per aim model, space-separated. */
    private static String virtualGuns(GunStatsView stats) {
        StringBuilder sb = new StringBuilder();
        for (int m = 0; m < stats.models(); m++) {
            if (m > 0) {
                sb.append(' ');
            }
            sb.append(stats.modelName(m)).append(':').append(stats.virtualHits(m)).append('/')
                    .append(stats.virtualShots(m));
        }
        return sb.toString();
    }
}
