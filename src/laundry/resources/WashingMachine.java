package laundry.resources;

import laundry.model.MachineState;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a single Washing Machine in the laundry facility.
 */
public class WashingMachine {

    private final int id;
    private final AtomicReference<MachineState> state;
    private volatile int currentCustomerId = -1;

    public WashingMachine(int id) {
        this.id    = id;
        this.state = new AtomicReference<>(MachineState.IDLE);
    }

    public void acquire(int customerId) {
        state.set(MachineState.IN_USE);
        currentCustomerId = customerId;
    }

    public void markFailed() {
        state.set(MachineState.FAILED);
    }

    public void markRetrying() {
        state.set(MachineState.RETRYING);
    }

    public void release() {
        currentCustomerId = -1;
        state.set(MachineState.IDLE);
    }

    public int getId()                 { return id; }
    public MachineState getState()     { return state.get(); }
    public int getCurrentCustomerId()  { return currentCustomerId; }
    public boolean isIdle()            { return state.get() == MachineState.IDLE; }
}
