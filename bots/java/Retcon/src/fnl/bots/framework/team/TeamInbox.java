package fnl.bots.framework.team;

import fnl.bots.framework.sense.TurnInput;

/** Read-only view of the team messages received this turn. */
public final class TeamInbox {

    private final TurnInput input;

    public TeamInbox(TurnInput input) {
        this.input = input;
    }

    public int count() {
        return input.teamMessageCount;
    }

    public String message(int index) {
        return input.teamMessages[index];
    }

    public int sender(int index) {
        return input.teamMessageSenders[index];
    }
}
