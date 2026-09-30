package laundry.simulation;

import laundry.facility.LaundryFacility;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * CongestionManager — Bonus Scenario: Total Payment Kiosk Failure
 */
public class CongestionManager {

    private static final int ALERT_THRESHOLD = 30;
    private static final int OWNER_ARRIVAL_DELAY_MS = 5000;

    private final LaundryFacility facility;
    private final ScheduledExecutorService monitor;
    private volatile boolean alertFired = false;

    public CongestionManager(LaundryFacility facility) {
        this.facility = facility;
        this.monitor  = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CongestionMonitor");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        facility.log("\n======================================================\n" +
                "  BONUS MODE: PAYMENT KIOSK TOTAL FAILURE SIMULATED  \n" +
                "       Both kiosks are OUT OF SERVICE from the start.     \n" +
                "======================================================\n");

        facility.forceFailAllKiosks();
        monitor.scheduleAtFixedRate(this::checkQueue, 1, 1, TimeUnit.SECONDS);
    }

    private void checkQueue() {
        if (alertFired) return;

        int queueSize = facility.getPaymentQueueSize();
        if (queueSize >= ALERT_THRESHOLD) {
            alertFired = true;
            monitor.shutdown();
            triggerOwnerIntervention(queueSize);
        }
    }

    private void triggerOwnerIntervention(int queueSize) {
        facility.log("\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
                "    *** EMERGENCY ALERT ***\n" +
                "    Payment queue has reached " + queueSize + " customers!\n" +
                "    Both kiosks are down. Facility owner has been contacted.\n" +
                "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n");

        facility.log("[CongestionMonitor] OWNER CALLED. ETA: 5 seconds...");

        try {
            Thread.sleep(OWNER_ARRIVAL_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        facility.log("\n******************************************************\n" +
                "    OWNER ARRIVED. Both kiosks are being restored!\n" +
                "    All waiting customers may now proceed to payment.\n" +
                "******************************************************\n");

        facility.restoreAllKiosks();
    }

    public void shutdown() {
        monitor.shutdownNow();
    }
}




