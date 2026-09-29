package fnl.bots.retcon;

import fnl.bots.framework.adapter.tankroyale.TankRoyaleBot;
import fnl.bots.framework.blackboard.ModeKind;
import fnl.bots.framework.pipeline.BotProgram;
import fnl.bots.framework.pipeline.Pipeline;
import fnl.bots.framework.roles.AimModel;
import fnl.bots.framework.roles.Gun;
import fnl.bots.framework.roles.StrategySet;
import fnl.bots.framework.sense.GameInfo;
import fnl.bots.framework.sense.TurnInput;
import fnl.bots.framework.stages.StandardPipeline;
import fnl.bots.framework.targeting.VirtualBullets;
import fnl.bots.retcon.firepower.BasicFirePower;
import fnl.bots.retcon.gun.BestHitRateSelector;
import fnl.bots.retcon.gun.HeadOnAimModel;
import fnl.bots.retcon.gun.LinearAimModel;
import fnl.bots.retcon.mode.AliveCountMode;
import fnl.bots.retcon.movement.OrbitMovement;
import fnl.bots.retcon.movement.WaveSurfMovement;
import fnl.bots.retcon.radar.PerfectLockRadar;
import fnl.bots.retcon.radar.SpinRadar;
import fnl.bots.retcon.target.ClosestTargetSelector;
import fnl.bots.retcon.team.NoOpTeamComms;

import java.util.EnumMap;
import java.util.Map;

/**
 * Retcon, a Tank Royale Rumble bot built to prove Retroactive Hit Analysis. Milestone 1: a perfect radar lock and a
 * head-on gun on top of the framework pipeline. See {@code docs/design.md}.
 */
public final class Retcon implements BotProgram {

    static final String NAME = "Retcon";

    public static void main(String[] args) {
        if ("1".equals(System.getenv("RUMBLE_SMOKE"))) {
            System.out.println("RUMBLE_SMOKE_READY " + NAME);
            return;
        }
        TankRoyaleBot.start(NAME + ".json", new Retcon());
    }

    @Override
    public Pipeline createPipeline(GameInfo game, TurnInput input) {
        // Linear first: the selector starts with model 0 until the virtual bullets have results.
        Gun gun = new Gun(new VirtualBullets(), new AimModel[]{new LinearAimModel(), new HeadOnAimModel()},
                new BestHitRateSelector());
        BasicFirePower power = new BasicFirePower();
        ClosestTargetSelector target = new ClosestTargetSelector();
        NoOpTeamComms comms = new NoOpTeamComms();

        // Preferred ranges from the book's distancing chapter; orbiting in melee and 2v2 is a stopgap until M4.
        OrbitMovement duelOrbit = new OrbitMovement(450, 1);
        OrbitMovement meleeOrbit = new OrbitMovement(650, 2);
        WaveSurfMovement duelSurf = new WaveSurfMovement(450, duelOrbit);

        StrategySet duel = new StrategySet(new PerfectLockRadar(), duelSurf, target, gun, power, comms);
        StrategySet melee = new StrategySet(new SpinRadar(), meleeOrbit, target, gun, power, comms);

        Map<ModeKind, StrategySet> sets = new EnumMap<>(ModeKind.class);
        sets.put(ModeKind.ONE_VS_ONE, duel);
        sets.put(ModeKind.MELEE, melee);
        sets.put(ModeKind.TWO_VS_TWO, melee);
        return StandardPipeline.create(game, input, new AliveCountMode(), sets, StandardPipeline.DEFAULT_BUDGET_SHARE);
    }
}
