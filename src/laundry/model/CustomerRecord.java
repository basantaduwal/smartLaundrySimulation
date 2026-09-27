package laundry.model;

/**
 * Immutable data record for a completed customer's visit.
 */
public class CustomerRecord {

    private final int customerId;
    private final long totalTimeMs;
    private final boolean usedBonusMode;

    public CustomerRecord(int customerId, long totalTimeMs, boolean usedBonusMode) {
        this.customerId    = customerId;
        this.totalTimeMs   = totalTimeMs;
        this.usedBonusMode = usedBonusMode;
    }

    public int getCustomerId()       { return customerId; }
    public long getTotalTimeMs()     { return totalTimeMs; }
    public boolean isUsedBonusMode() { return usedBonusMode; }
}
