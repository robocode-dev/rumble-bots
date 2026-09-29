package fnl.bots.framework.roles;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.team.TeamInbox;
import fnl.bots.framework.team.TeamOutbox;

/** Exchanges messages with teammates. */
public interface TeamComms {

    void receive(Blackboard bb, TeamInbox inbox);

    void send(Blackboard bb, TeamOutbox outbox);
}
