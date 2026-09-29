package fnl.bots.framework.adapter.tankroyale;

import dev.robocode.tankroyale.botapi.Bot;
import dev.robocode.tankroyale.botapi.BotException;
import dev.robocode.tankroyale.botapi.BotInfo;
import dev.robocode.tankroyale.botapi.BulletState;
import dev.robocode.tankroyale.botapi.events.BotDeathEvent;
import dev.robocode.tankroyale.botapi.events.BulletFiredEvent;
import dev.robocode.tankroyale.botapi.events.BulletHitBotEvent;
import dev.robocode.tankroyale.botapi.events.GameEndedEvent;
import dev.robocode.tankroyale.botapi.events.GameStartedEvent;
import dev.robocode.tankroyale.botapi.events.HitBotEvent;
import dev.robocode.tankroyale.botapi.events.HitByBulletEvent;
import dev.robocode.tankroyale.botapi.events.RoundEndedEvent;
import dev.robocode.tankroyale.botapi.events.ScannedBotEvent;
import dev.robocode.tankroyale.botapi.events.SkippedTurnEvent;
import dev.robocode.tankroyale.botapi.events.TeamMessageEvent;
import fnl.bots.framework.arbitration.Intent;
import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemyView;
import fnl.bots.framework.pipeline.BotProgram;
import fnl.bots.framework.pipeline.Pipeline;
import fnl.bots.framework.pipeline.Warmup;
import fnl.bots.framework.rules.Rules;
import fnl.bots.framework.sense.BulletRecord;
import fnl.bots.framework.sense.GameInfo;
import fnl.bots.framework.sense.ScanRecord;
import fnl.bots.framework.sense.TurnInput;
import fnl.bots.framework.stats.MovementProbe;
import fnl.bots.framework.stats.StatsWriter;
import fnl.bots.framework.stats.TimingProbe;
import fnl.bots.framework.stats.TurnStats;
import fnl.bots.framework.stats.WaveProbe;
import fnl.bots.framework.team.TeamOutbox;

import java.nio.file.Path;
import java.util.Set;

/**
 * The Tank Royale adapter: the only code that touches the Tank Royale Bot API. Event handlers translate events into
 * the preallocated {@link TurnInput} (degrees become radians); each turn the run loop steps the {@link Pipeline} and
 * translates its {@link Intent} back into Bot API setters.
 * <p>
 * The Bot API dispatches a turn's events on the bot thread inside {@code go()}, after waiting for the next tick, so
 * when {@code go()} returns, the input holds exactly that turn's events.
 */
public final class TankRoyaleBot extends Bot {

    /** Synthetic turns run before connecting, to compile and JIT-warm the pipeline (see {@link Warmup}). */
    private static final int WARMUP_TURNS = 20_000;

    private final String name;
    private final BotProgram program;
    private final TurnInput input = new TurnInput();
    private final GameInfo game = new GameInfo();
    private final TimingProbe probe = new TimingProbe();
    private final WaveProbe waveProbe = new WaveProbe();
    private final MovementProbe movementProbe = new MovementProbe();
    private Pipeline pipeline;
    private TurnStats stats;
    private Path statsFile;
    private int roundsRecorded;

    private TankRoyaleBot(BotInfo info, BotProgram program) {
        super(info);
        this.name = info != null ? info.getName() : envOrDefault("BOT_NAME", "bot");
        this.program = program;
    }

    /**
     * Starts a bot on Tank Royale. The config file is read from the working directory (the booter starts bots in
     * their own directory); without it, the Bot API falls back to the booter's environment variables.
     */
    public static void start(String configFile, BotProgram program) {
        Warmup.run(program, WARMUP_TURNS);
        new TurnStats(1).recordCompute(0);
        new TimingProbe().observe(new TurnInput());
        new TankRoyaleBot(loadBotInfo(configFile), program).start();
    }

    /** Builds the pipeline as soon as the game setup is known, before the first tick. */
    @Override
    public void onGameStarted(GameStartedEvent e) {
        createPipeline();
    }

    /** Called from the event thread (game started) and the bot thread (round start); builds once. */
    private synchronized void createPipeline() {
        if (pipeline != null) {
            return;
        }
        readGameInfo();
        pipeline = program.createPipeline(game, input);
        stats = new TurnStats(game.numberOfRounds);
        stats.setBudget(game.turnTimeoutMicros, pipeline.blackboard().turn().computeBudgetMicros());
        statsFile = StatsWriter.statsFile(name, game.myId);
        movementProbe.setArena(game.arenaWidth, game.arenaHeight);
    }

    @Override
    public void run() {
        createPipeline();
        // Decouple body, gun and radar: each turn rate is then independent (Rules.ACTUATORS_DECOUPLED).
        setAdjustGunForBodyTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForBodyTurn(true);
        setFireAssist(false);

        input.clearEvents();
        pipeline.onRoundStart();

        while (isRunning()) {
            long start = System.nanoTime();
            readState();
            probe.observe(input);
            movementProbe.observe(input);
            Intent intent = pipeline.step(input);
            waveProbe.observe(pipeline.blackboard().enemyWaves(), input.turn);
            apply(intent);
            movementProbe.onIntent(input, intent.targetSpeed, intent.bodyTurnRate);
            stats.recordCompute((System.nanoTime() - start) / 1000);
            input.clearEvents();
            go();
        }
    }

    private void readGameInfo() {
        game.myId = getMyId();
        game.arenaWidth = getArenaWidth();
        game.arenaHeight = getArenaHeight();
        game.numberOfRounds = getNumberOfRounds();
        game.gunCoolingRate = getGunCoolingRate();
        game.turnTimeoutMicros = getTurnTimeout();
        Set<Integer> teammates = getTeammateIds();
        game.teammateIds = teammates.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    private void readState() {
        input.round = getRoundNumber();
        input.turn = getTurnNumber();
        input.x = getX();
        input.y = getY();
        input.heading = TankRoyaleAngles.headingToCore(getDirection());
        input.gunHeading = TankRoyaleAngles.headingToCore(getGunDirection());
        input.radarHeading = TankRoyaleAngles.headingToCore(getRadarDirection());
        input.speed = getSpeed();
        input.energy = getEnergy();
        input.gunHeat = getGunHeat();
        input.enemyCount = getEnemyCount();
        input.timeLeftMicros = getTimeLeft();
    }

    private void apply(Intent intent) {
        setTargetSpeed(intent.targetSpeed);
        setTurnRate(TankRoyaleAngles.turnRateToPlatform(intent.bodyTurnRate));
        setGunTurnRate(TankRoyaleAngles.turnRateToPlatform(intent.gunTurnRate));
        setRadarTurnRate(TankRoyaleAngles.turnRateToPlatform(intent.radarTurnRate));
        // setFire persists until a bullet is fired, so a cold gun that holds fire must clear it explicitly.
        if (intent.firepower > 0) {
            if (setFire(intent.firepower)) {
                EnemyView target = pipeline.blackboard().targetEnemy();
                probe.onFireRequested(input.turn, input.x, input.y, input.gunHeading,
                        target != null ? target.x() : Double.NaN, target != null ? target.y() : Double.NaN);
            }
        } else if (getGunHeat() == 0) {
            setFire(0);
        }
        TeamOutbox outbox = pipeline.outbox();
        for (int i = 0; i < outbox.count(); i++) {
            broadcastTeamMessage(outbox.message(i));
        }
        outbox.clear();
    }

    // ---- Events -> TurnInput -----------------------------------------------------------------------------------

    @Override
    public void onScannedBot(ScannedBotEvent e) {
        ScanRecord scan = input.addScan();
        if (scan != null) {
            scan.botId = e.getScannedBotId();
            scan.x = e.getX();
            scan.y = e.getY();
            scan.heading = TankRoyaleAngles.headingToCore(e.getDirection());
            scan.speed = e.getSpeed();
            scan.energy = e.getEnergy();
        }
    }

    @Override
    public void onBotDeath(BotDeathEvent e) {
        input.addDeath(e.getVictimId());
    }

    @Override
    public void onBulletFired(BulletFiredEvent e) {
        fillBullet(input.addBulletFired(), e.getTurnNumber(), e.getBullet());
    }

    @Override
    public void onBulletHit(BulletHitBotEvent e) {
        BulletRecord hit = input.addBulletHit();
        if (fillBullet(hit, e.getTurnNumber(), e.getBullet())) {
            hit.victimId = e.getVictimId();
            hit.victimEnergy = e.getEnergy();
        }
    }

    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        fillBullet(input.addHitTaken(), e.getTurnNumber(), e.getBullet());
    }

    @Override
    public void onHitBot(HitBotEvent e) {
        input.addCollision(e.getVictimId(), e.isRammed());
    }

    @Override
    public void onTeamMessage(TeamMessageEvent e) {
        input.addTeamMessage(e.getSenderId(), String.valueOf(e.getMessage()));
    }

    @Override
    public void onSkippedTurn(SkippedTurnEvent e) {
        input.skippedTurns++;
        if (stats != null) {
            stats.recordSkipped(getRoundNumber(), e.getTurnNumber());
        }
    }

    @Override
    public void onRoundEnded(RoundEndedEvent e) {
        roundsRecorded = e.getRoundNumber();
        writeStats();
    }

    @Override
    public void onGameEnded(GameEndedEvent e) {
        writeStats();
    }

    private void writeStats() {
        if (stats != null) {
            Blackboard bb = pipeline.blackboard();
            StatsWriter.write(statsFile, name, game.myId, roundsRecorded, stats, probe, bb.enemyWaves(), waveProbe,
                    movementProbe, bb.gunStats());
        }
    }

    private static boolean fillBullet(BulletRecord record, int turn, BulletState bullet) {
        if (record == null) {
            return false;
        }
        record.bulletId = bullet.getBulletId();
        record.turn = turn;
        record.x = bullet.getX();
        record.y = bullet.getY();
        record.heading = TankRoyaleAngles.headingToCore(bullet.getDirection());
        record.power = Rules.clampFirepower(bullet.getPower());
        record.ownerId = bullet.getOwnerId();
        return true;
    }

    private static BotInfo loadBotInfo(String configFile) {
        try {
            return BotInfo.fromFile(configFile);
        } catch (BotException e) {
            return null;
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
