package fnl.bots.framework.pipeline;

import fnl.bots.framework.sense.GameInfo;
import fnl.bots.framework.sense.TurnInput;

/**
 * A bot, seen from an adapter: something that builds its pipeline once the game setup is known. Platform-free, so
 * the same program can run behind any platform adapter.
 */
public interface BotProgram {

    /**
     * Builds the pipeline for a game.
     *
     * @param game  the game setup
     * @param input the adapter's preallocated per-turn input, reused every turn
     */
    Pipeline createPipeline(GameInfo game, TurnInput input);
}
