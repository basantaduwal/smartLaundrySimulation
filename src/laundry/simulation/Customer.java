package laundry.simulation;

import laundry.facility.LaundryFacility;
import laundry.resources.Dryer;
import laundry.resources.PaymentKiosk;
import laundry.resources.WashingMachine;

import java.util.Random;

/**
 * Customer — Active Thread Representing One Laundry Customer
 */
public class Customer extends Thread {

    private static final double FAILURE_PROBABILITY = 0.05; // 5% chance

    private final int             customerId;
    private final LaundryFacility facility;
    private final Random          random;
    private final boolean         bonusMode;

    public Customer(int customerId, LaundryFacility facility, boolean bonusMode) {
        super("Customer-" + customerId);
        this.customerId = customerId;
        this.facility   = facility;
        this.random     = new Random();
        this.bonusMode  = bonusMode;
    }

    @Override
    public void run() {
        long arrivalTime = System.currentTimeMillis();

        try {
            // Stage 1: Arrival
            log("ARRIVED at the laundry facility. (Entry Gate)");

            // Stage 2: Washing
            performWashing();

            // Stage 3: Drying
            performDrying();

            // Stage 4: Payment
            performPayment();

            // Stage 5: Exit
            long totalTime = System.currentTimeMillis() - arrivalTime;
            log(String.format("EXITED facility. Total time: %.2f seconds. (Exit Gate)",
                    totalTime / 1000.0));
            facility.recordCompletion(customerId, totalTime, bonusMode);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("Thread interrupted — customer left early.");
        }
    }

    private void performWashing() throws InterruptedException {
        boolean washComplete = false;

        while (!washComplete) {
            log("WAITING for a washing machine... (Washer queue: "
                    + facility.getWasherQueueLength() + " ahead)");

            WashingMachine machine = facility.acquireWasher(customerId);
            log("ACQUIRED Washing Machine #" + machine.getId() + ". Starting wash cycle.");

            int washTime = randomBetween(4000, 6000);  // 4–6 seconds
            log("WASHING on Machine #" + machine.getId()
                    + " (cycle: " + (washTime / 1000.0) + "s)...");
            sleep(washTime);

            // 5% Failure chance
            if (random.nextDouble() < FAILURE_PROBABILITY) {
                facility.incrementWasherFailures();
                machine.markFailed();
                log("WASHER #" + machine.getId() + " FAILED mid-cycle on Customer-" + customerId
                        + " - releasing machine and waiting 3 seconds before retry...");
                
                // Release faulty washer so resource returns to pool / can be serviced
                facility.releaseWasher(machine);
                sleep(3000);
                
                log("RETRYING wash - re-entering washer queue for Customer-" + customerId + "...");
                // Loop restarts -> re-acquires a washer from the pool
            } else {
                washComplete = true;
                log("WASH COMPLETE on Machine #" + machine.getId() + ".");
                facility.releaseWasher(machine);
                log("RELEASED Washing Machine #" + machine.getId() + ".");
            }
        }

        log("Customer-" + customerId + " finished washing, heading to dryers");
    }

    private void performDrying() throws InterruptedException {
        log("WAITING for a dryer... (Dryer queue: "
                + facility.getDryerQueueLength() + " ahead)");

        Dryer dryer = facility.acquireDryer(customerId);
        log("ACQUIRED Dryer #" + dryer.getId() + ". Starting dry cycle.");

        int dryTime = randomBetween(3000, 5000);  // 3–5 seconds
        log("DRYING on Dryer #" + dryer.getId()
                + " (cycle: " + (dryTime / 1000.0) + "s)...");
        sleep(dryTime);

        facility.releaseDryer(dryer);
        log("Customer-" + customerId + " finished drying, heading to payment");
    }

    private void performPayment() throws InterruptedException {
        boolean paymentComplete = false;

        while (!paymentComplete) {
            // Customer enters the physical payment line (accounted per attempt)
            facility.enterPaymentQueue();
            log("WAITING for a payment kiosk... (Payment queue: "
                    + facility.getPaymentQueueSize() + " waiting)");

            PaymentKiosk kiosk = null;
            try {
                while (kiosk == null) {
                    try {
                        kiosk = facility.acquireKiosk(customerId);
                    } catch (InterruptedException e) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw e;
                        }
                        log("Kiosk is DOWN (congestion) - Customer-" + customerId + " payment failed, still queued");
                        sleep(2000);
                    }
                }
            } finally {
                facility.leavePaymentQueue();
            }

            log("ACQUIRED Kiosk #" + kiosk.getId() + ". Processing payment.");

            int payTime = randomBetween(1000, 2000);  // 1–2 seconds
            log("PAYING at Kiosk #" + kiosk.getId()
                    + " (processing: " + (payTime / 1000.0) + "s)...");
            sleep(payTime);

            if (random.nextDouble() < FAILURE_PROBABILITY) {
                facility.incrementKioskFailures();
                kiosk.markFailed();
                log("KIOSK FAILURE at Kiosk #" + kiosk.getId()
                        + "! Releasing kiosk and waiting 2 seconds before retry...");
                
                // Release kiosk back to pool so other customers can use it
                facility.releaseKiosk(kiosk);
                sleep(2000);
                
                log("RETRYING payment - Customer-" + customerId + " rejoining payment queue...");
                // Loop restarts -> re-acquires a kiosk from pool
            } else {
                paymentComplete = true;
                log("PAYMENT COMPLETE at Kiosk #" + kiosk.getId() + ".");
                facility.releaseKiosk(kiosk);
                log("RELEASED Kiosk #" + kiosk.getId() + ".");
            }
        }
    }

    private int randomBetween(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private void log(String message) {
        String threadName = Thread.currentThread().getName();
        String formatted  = String.format("[%-12s] %s", threadName, message);
        facility.log(formatted);
    }
}
