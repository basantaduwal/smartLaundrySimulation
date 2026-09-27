package laundry;

import laundry.facility.LaundryFacility;
import laundry.gui.LaundryGUI;
import laundry.simulation.CongestionManager;
import laundry.simulation.Customer;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Main — Entry Point for Smart Laundry Simulation
 */
public class Main {

    private static final int TOTAL_CUSTOMERS     = 50;
    private static final int MAX_ARRIVAL_DELAY_MS = 3000;

    public static void main(String[] args) throws Exception {
        boolean noGui    = hasArg(args, "--no-gui");
        boolean bonusArg = hasArg(args, "--bonus");

        LaundryFacility facility = new LaundryFacility();

        if (noGui) {
            System.out.println("Running in console-only mode" + (bonusArg ? " [BONUS]" : "") + "...");
            runSimulation(facility, bonusArg);
        } else {
            SwingUtilities.invokeLater(() -> {
                LaundryGUI gui = new LaundryGUI(facility);

                gui.setNormalSimulationRunner(() -> {
                    runSimulation(gui.getFacility(), false);
                });

                gui.setBonusSimulationRunner(() -> {
                    runSimulation(gui.getFacility(), true);
                });

                gui.setVisible(true);
            });
        }
    }

    public static void runSimulation(LaundryFacility facility, boolean bonusMode) {
        Random random = new Random();
        CongestionManager congestionManager = null;

        facility.log("\n======================================================\n" +
            "         SMART LAUNDRY SIMULATION — STARTING          \n" +
            "  Customers: 50  |  Washers: 6  |  Dryers: 4  |  Kiosks: 2  \n" +
            "  Mode: " + (bonusMode ? "CONGESTED BONUS             " : "NORMAL                      ") + "\n" +
            "======================================================");

        if (bonusMode) {
            congestionManager = new CongestionManager(facility);
            congestionManager.start();
        }

        List<Customer> customers = new ArrayList<>();
        for (int i = 1; i <= TOTAL_CUSTOMERS; i++) {
            customers.add(new Customer(i, facility, bonusMode));
        }

        long simStart = System.currentTimeMillis();
        for (Customer customer : customers) {
            customer.start();
            int interArrivalDelay = random.nextInt(MAX_ARRIVAL_DELAY_MS / TOTAL_CUSTOMERS * 2 + 1);
            try {
                Thread.sleep(interArrivalDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        for (Customer customer : customers) {
            try {
                customer.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                facility.log("[Main] Simulation interrupted!");
                break;
            }
        }

        long totalSimTime = System.currentTimeMillis() - simStart;

        if (congestionManager != null) {
            congestionManager.shutdown();
        }

        printStats(facility, totalSimTime, bonusMode);
    }

    private static void printStats(LaundryFacility facility, long totalSimTimeMs, boolean bonusMode) {
        facility.log("\n======================================================\n" +
            "                  SIMULATION COMPLETE — RESULTS       \n" +
            "------------------------------------------------------\n" +
            "  Mode              : " + (bonusMode ? "BONUS (Congested)" : "NORMAL") + "\n" +
            "  Total Time        : " + String.format("%.2f seconds", totalSimTimeMs / 1000.0) + "\n" +
            "  Customers Served  : " + facility.getTotalServed() + "\n" +
            "  Avg Time/Customer : " + String.format("%.2f seconds", facility.getAverageTimeSeconds()) + "\n" +
            "  Peak Washers In Use: " + facility.getPeakWashersInUse() + " / 6\n" +
            "  Peak Dryers In Use : " + facility.getPeakDryersInUse()  + " / 4\n" +
            "======================================================\n");
    }

    private static boolean hasArg(String[] args, String arg) {
        for (String a : args) {
            if (a.equalsIgnoreCase(arg)) return true;
        }
        return false;
    }
}
