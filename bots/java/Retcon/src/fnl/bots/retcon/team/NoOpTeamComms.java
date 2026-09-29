package fnl.bots.retcon.team;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.roles.TeamComms;
import fnl.bots.framework.team.TeamInbox;
import fnl.bots.framework.team.TeamOutbox;

/**
 * Sends and reads nothing. Team play comes after milestone 1.
 * <p>
 * Book: <a href="https://book.robocode.dev/team-strategies/team-basics.html">Team Basics</a>.
 */
public final class NoOpTeamComms implements TeamComms {

    @Override
    public void receive(Blackboard bb, TeamInbox inbox) {
    }

    @Override
    public void send(Blackboard bb, TeamOutbox outbox) {
    }
}
