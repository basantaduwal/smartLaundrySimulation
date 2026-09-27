package laundry.resources;

import laundry.model.MachineState;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a single Payment Kiosk in the laundry facility.
 */
public class PaymentKiosk {

    private final int id;
    private final AtomicReference<MachineState> state;
    private volatile int currentCustomerId = -1;
    private volatile boolean forceFailed = false;

    public PaymentKiosk(int id) {
        this.id    = id;
        this.state = new AtomicReference<>(MachineState.IDLE);
    }

    public boolean acquire(int customerId) {
        if (forceFailed) {
            state.set(MachineState.FAILED);
            return false;
        }
        state.set(MachineState.IN_USE);
        currentCustomerId = customerId;
        return true;
    }

    public void markFailed() {
        state.set(MachineState.FAILED);
    }

    public void markRetrying() {
        state.set(MachineState.RETRYING);
    }

    public void release() {
        currentCustomerId = -1;
        if (!forceFailed) {
            state.set(MachineState.IDLE);
        }
    }

    public void forceFail() {
        this.forceFailed = true;
        state.set(MachineState.FAILED);
    }

    public void restore() {
        this.forceFailed = false;
        state.set(MachineState.IDLE);
    }

    public int getId()                 { return id; }
    public MachineState getState()     { return state.get(); }
    public int getCurrentCustomerId()  { return currentCustomerId; }
    public boolean isForceFailed()     { return forceFailed; }
    public boolean isIdle()            { return state.get() == MachineState.IDLE; }
}
