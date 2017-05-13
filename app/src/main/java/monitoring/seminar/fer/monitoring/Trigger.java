package monitoring.seminar.fer.monitoring;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by roko on 4/29/17.
 */

public abstract class Trigger {
    private List<Action> actions;

    public synchronized Trigger setActions(List<? extends Action> actions) {
        this.actions = new ArrayList<>(actions);

        return this;
    }

    protected synchronized void triggerActions() {
        if (actions == null) {
            throw new IllegalStateException("Actions are not set");
        }

        for (Action action : actions) {
            action.performAction();
        }
    }
}
