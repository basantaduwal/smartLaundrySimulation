package laundry.model;

/**
 * Enum representing all possible states a machine (washer, dryer, kiosk) can be in.
 */
public enum MachineState {
    IDLE,       // Machine is free and available for use
    IN_USE,     // Machine is actively running a cycle
    FAILED,     // Machine has experienced a mid-cycle failure
    RETRYING    // Machine or customer is retrying after a failure
}
