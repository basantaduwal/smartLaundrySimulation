package laundry.facility;

import laundry.model.CustomerRecord;
import laundry.resources.Dryer;
import laundry.resources.PaymentKiosk;
import laundry.resources.WashingMachine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * LaundryFacility — Central Resource Manager & Statistics Coordinator
 */
public class LaundryFacility {

    private final List<WashingMachine> washers;
    private final List<Dryer>          dryers;
    private final List<PaymentKiosk>   kiosks;

    // Semaphores with fairness (FIFO order)
    private final Semaphore washerSemaphore  = new Semaphore(6, true);
    private final Semaphore dryerSemaphore   = new Semaphore(4, true);
    private final Semaphore kioskSemaphore   = new Semaphore(2, true);

    // Atomic stats
    private final AtomicInteger totalServed        = new AtomicInteger(0);
    private final AtomicLong    totalTimeSum       = new AtomicLong(0);
    private final AtomicInteger peakWashersInUse   = new AtomicInteger(0);
    private final AtomicInteger peakDryersInUse    = new AtomicInteger(0);
    private final AtomicInteger currentWashersInUse = new AtomicInteger(0);
    private final AtomicInteger currentDryersInUse  = new AtomicInteger(0);
    private final AtomicInteger paymentQueueSize    = new AtomicInteger(0);
    private final AtomicInteger washerFailures     = new AtomicInteger(0);
    private final AtomicInteger kioskFailures      = new AtomicInteger(0);

    private final Queue<CustomerRecord> completedCustomers = new ConcurrentLinkedQueue<>();
    private volatile Consumer<String> logListener = null;

    public LaundryFacility() {
        List<WashingMachine> ws = new ArrayList<>();
        for (int i = 1; i <= 6; i++) ws.add(new WashingMachine(i));
        this.washers = Collections.unmodifiableList(ws);

        List<Dryer> ds = new ArrayList<>();
        for (int i = 1; i <= 4; i++) ds.add(new Dryer(i));
        this.dryers = Collections.unmodifiableList(ds);

        List<PaymentKiosk> ks = new ArrayList<>();
        for (int i = 1; i <= 2; i++) ks.add(new PaymentKiosk(i));
        this.kiosks = Collections.unmodifiableList(ks);
    }

    public WashingMachine acquireWasher(int customerId) throws InterruptedException {
        washerSemaphore.acquire();
        int current = currentWashersInUse.incrementAndGet();
        peakWashersInUse.accumulateAndGet(current, Math::max);

        synchronized (washers) {
            WashingMachine machine = findIdleWasher();
            machine.acquire(customerId);
            return machine;
        }
    }

    public void releaseWasher(WashingMachine machine) {
        synchronized (washers) {
            machine.release();
        }
        currentWashersInUse.decrementAndGet();
        washerSemaphore.release();
    }

    private WashingMachine findIdleWasher() {
        for (WashingMachine m : washers) {
            if (m.isIdle()) return m;
        }
        throw new IllegalStateException("No idle washer found");
    }

    public Dryer acquireDryer(int customerId) throws InterruptedException {
        dryerSemaphore.acquire();
        int current = currentDryersInUse.incrementAndGet();
        peakDryersInUse.accumulateAndGet(current, Math::max);

        synchronized (dryers) {
            Dryer dryer = findIdleDryer();
            dryer.acquire(customerId);
            return dryer;
        }
    }

    public void releaseDryer(Dryer dryer) {
        synchronized (dryers) {
            dryer.release();
        }
        currentDryersInUse.decrementAndGet();
        dryerSemaphore.release();
    }

    private Dryer findIdleDryer() {
        for (Dryer d : dryers) {
            if (d.isIdle()) return d;
        }
        throw new IllegalStateException("No idle dryer found");
    }

    public void enterPaymentQueue() {
        paymentQueueSize.incrementAndGet();
    }

    public void leavePaymentQueue() {
        paymentQueueSize.decrementAndGet();
    }

    public PaymentKiosk acquireKiosk(int customerId) throws InterruptedException {
        kioskSemaphore.acquire();

        synchronized (kiosks) {
            for (PaymentKiosk k : kiosks) {
                if (k.isIdle()) {
                    boolean acquired = k.acquire(customerId);
                    if (acquired) {
                        return k;
                    }
                }
            }
        }
        kioskSemaphore.release();
        throw new InterruptedException("Kiosk force-failed (congested mode)");
    }

    public void releaseKiosk(PaymentKiosk kiosk) {
        synchronized (kiosks) {
            kiosk.release();
        }
        kioskSemaphore.release();
    }

    public void recordCompletion(int customerId, long totalTimeMs, boolean bonusMode) {
        completedCustomers.add(new CustomerRecord(customerId, totalTimeMs, bonusMode));
        totalServed.incrementAndGet();
        totalTimeSum.addAndGet(totalTimeMs);
    }

    public double getAverageTimeSeconds() {
        int served = totalServed.get();
        if (served == 0) return 0.0;
        return (totalTimeSum.get() / (double) served) / 1000.0;
    }

    public void incrementWasherFailures() { washerFailures.incrementAndGet(); }
    public void incrementKioskFailures()  { kioskFailures.incrementAndGet(); }
    public int getWasherFailures()        { return washerFailures.get(); }
    public int getKioskFailures()         { return kioskFailures.get(); }

    public List<WashingMachine> getWashers()         { return washers; }
    public List<Dryer>          getDryers()           { return dryers; }
    public List<PaymentKiosk>   getKiosks()           { return kiosks; }
    public int getTotalServed()                        { return totalServed.get(); }
    public int getPeakWashersInUse()                   { return peakWashersInUse.get(); }
    public int getPeakDryersInUse()                    { return peakDryersInUse.get(); }
    public int getCurrentWashersInUse()                { return currentWashersInUse.get(); }
    public int getCurrentDryersInUse()                 { return currentDryersInUse.get(); }
    public int getPaymentQueueSize()                   { return paymentQueueSize.get(); }
    public int getWasherQueueLength()                  { return washerSemaphore.getQueueLength(); }
    public int getDryerQueueLength()                   { return dryerSemaphore.getQueueLength(); }
    public Queue<CustomerRecord> getCompletedCustomers() { return completedCustomers; }

    public void setLogListener(Consumer<String> listener) { this.logListener = listener; }

    public void log(String message) {
        System.out.println(message);
        Consumer<String> listener = this.logListener;
        if (listener != null) {
            listener.accept(message);
        }
    }

    public void forceFailAllKiosks() {
        for (PaymentKiosk k : kiosks) k.forceFail();
    }

    public void restoreAllKiosks() {
        for (PaymentKiosk k : kiosks) k.restore();
    }
}
