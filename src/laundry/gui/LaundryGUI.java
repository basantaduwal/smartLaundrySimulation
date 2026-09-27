package laundry.gui;

import laundry.facility.LaundryFacility;
import laundry.model.MachineState;
import laundry.resources.Dryer;
import laundry.resources.PaymentKiosk;
import laundry.resources.WashingMachine;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * LaundryGUI — Modern Facility Command Center Layout
 *
 * Distinct Architecture:
 *  - Top Header: Gradient/Navy title banner with live facility status badge + action toolbar
 *  - 3-Column Pipeline Overview:
 *      [ Column 1: Wash Bay ]      [ Column 2: Drying Area ]   [ Column 3: Payment Hub ]
 *      - Queue pill counter        - Queue pill counter        - Queue pill counter
 *      - Vertical machine cards    - Vertical machine cards    - Vertical machine cards
 *        (shows status icon,         (shows status icon,         (shows status icon,
 *         machine label, customer)    machine label, customer)    machine label, customer)
 *  - Right-Hand Metrics Bar:
 *      - KPI metric cards (Served, Avg Turnaround, Capacity Peak, Fault counters)
 *      - Congestion threshold warning bar (shows 0-30 progress toward owner call)
 *  - Bottom Panel:
 *      - Filterable-style activity terminal with timestamp & event category
 */
public class LaundryGUI extends JFrame {

    // Distinct modern styling & color palette
    private static final Color THEME_PRIMARY    = new Color(30, 41, 59);     // Deep Slate
    private static final Color THEME_SECONDARY  = new Color(51, 65, 85);    // Slate Accent
    private static final Color THEME_CARD_BG    = new Color(255, 255, 255);  // Card White
    private static final Color THEME_CANVAS     = new Color(241, 245, 249);  // Slate tint background
    private static final Color THEME_TEXT_MAIN  = new Color(15, 23, 42);     // Near black
    private static final Color THEME_TEXT_MUTED = new Color(100, 116, 139);  // Muted slate

    // Machine state colors
    private static final Color COLOR_IDLE_CARD  = new Color(248, 250, 252);
    private static final Color COLOR_ACTIVE     = new Color(37, 99, 235);    // Vibrant Royal Blue
    private static final Color COLOR_FAULT      = new Color(220, 38, 38);    // Crimson
    private static final Color COLOR_RETRYING   = new Color(217, 119, 6);    // Amber

    private LaundryFacility facility;

    // Control components
    private JButton launchButton;
    private JCheckBox bonusCheckBox;
    private JLabel facilityStatusBadge;

    // Machine card panels
    private final MachineCard[] washerCards = new MachineCard[6];
    private final MachineCard[] dryerCards  = new MachineCard[4];
    private final MachineCard[] kioskCards  = new MachineCard[2];

    // Pipeline queue badges
    private JLabel washerQueueBadge;
    private JLabel dryerQueueBadge;
    private JLabel kioskQueueBadge;

    // KPI Metric displays
    private JLabel metricServed;
    private JLabel metricAvgTime;
    private JLabel metricWasherPeak;
    private JLabel metricDryerPeak;
    private JLabel metricWasherErrors;
    private JLabel metricKioskErrors;
    private JProgressBar congestionMeter;
    private JLabel congestionMeterLabel;

    // Terminal log
    private JTextArea logStream;

    // Simulation threading
    private ScheduledExecutorService poller;
    private Thread simExecutionThread;
    private Runnable normalSimulationRunner;
    private Runnable bonusSimulationRunner;

    public LaundryGUI(LaundryFacility facility) {
        this.facility = facility;
        buildInterface();
        startMetricsPoller();
    }

    private void buildInterface() {
        setTitle("AquaFlow Smart Laundromat - Live Simulation");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1120, 760);
        setMinimumSize(new Dimension(980, 680));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(THEME_CANVAS);

        root.add(createHeaderBar(), BorderLayout.NORTH);

        // Center: Stage Columns + Right KPI Panel
        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setBackground(THEME_CANVAS);
        body.setBorder(new EmptyBorder(12, 16, 12, 16));

        body.add(createPipelineStagePanel(), BorderLayout.CENTER);
        body.add(createKpiSidebar(), BorderLayout.EAST);

        root.add(body, BorderLayout.CENTER);
        root.add(createActivityConsole(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. TOP NAV / HEADER BAR
    // ─────────────────────────────────────────────────────────────────────────
    private JPanel createHeaderBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(THEME_PRIMARY);
        bar.setBorder(new EmptyBorder(14, 20, 14, 20));

        // Brand / Title
        JPanel brandBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brandBox.setOpaque(false);

        JLabel logo = new JLabel("⚡");
        logo.setFont(new Font("Segoe UI", Font.PLAIN, 22));
        logo.setForeground(new Color(56, 189, 248));

        JLabel title = new JLabel("AquaFlow");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("|  Smart Laundromat");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(new Color(148, 163, 184));

        brandBox.add(logo);
        brandBox.add(title);
        brandBox.add(subtitle);

        // Action Toolbar
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        actions.setOpaque(false);

        facilityStatusBadge = new JLabel("● SYSTEM READY");
        facilityStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        facilityStatusBadge.setForeground(new Color(52, 211, 153)); // Soft green

        // Clean, unambiguous checkbox with clear contrast and state feedback
        bonusCheckBox = new JCheckBox("Bonus: Force Kiosks Down (Congested Mode)");
        bonusCheckBox.setFont(new Font("Segoe UI", Font.BOLD, 12));
        bonusCheckBox.setFocusPainted(false);
        bonusCheckBox.setOpaque(false);
        bonusCheckBox.setForeground(new Color(226, 232, 240)); // High-contrast crisp light silver
        bonusCheckBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        bonusCheckBox.addActionListener(e -> {
            if (bonusCheckBox.isSelected()) {
                bonusCheckBox.setForeground(new Color(252, 211, 77)); // Warm amber highlight when checked
                facilityStatusBadge.setText("● BONUS MODE ARMED");
                facilityStatusBadge.setForeground(new Color(252, 211, 77));
            } else {
                bonusCheckBox.setForeground(new Color(226, 232, 240));
                facilityStatusBadge.setText("● SYSTEM READY");
                facilityStatusBadge.setForeground(new Color(52, 211, 153));
            }
        });

        launchButton = new JButton("Launch Simulation");
        launchButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        launchButton.setFocusPainted(false);
        launchButton.setBackground(new Color(14, 165, 233)); // Sky blue
        launchButton.setForeground(Color.WHITE);
        launchButton.setBorder(new EmptyBorder(8, 18, 8, 18));
        launchButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        launchButton.addActionListener(e -> onLaunchSimulation());

        actions.add(facilityStatusBadge);
        actions.add(bonusCheckBox);
        actions.add(launchButton);

        bar.add(brandBox, BorderLayout.WEST);
        bar.add(actions, BorderLayout.EAST);
        return bar;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. WORKFLOW STAGES (3 Columns: Washers, Dryers, Payment Kiosks)
    // ─────────────────────────────────────────────────────────────────────────
    private JPanel createPipelineStagePanel() {
        JPanel stages = new JPanel(new GridLayout(1, 3, 12, 0));
        stages.setOpaque(false);

        // Column 1: Wash Section
        washerQueueBadge = createQueuePill("Waiting: 0");
        stages.add(buildStageColumn("STAGE 1: WASH BAY (6)", washerQueueBadge, washerCards, 6, "Washer"));

        // Column 2: Dry Section
        dryerQueueBadge = createQueuePill("Waiting: 0");
        stages.add(buildStageColumn("STAGE 2: DRYING AREA (4)", dryerQueueBadge, dryerCards, 4, "Dryer"));

        // Column 3: Payment Section
        kioskQueueBadge = createQueuePill("Waiting: 0");
        stages.add(buildStageColumn("STAGE 3: PAYMENT HUB (2)", kioskQueueBadge, kioskCards, 2, "Kiosk"));

        return stages;
    }

    private JPanel buildStageColumn(String title, JLabel queueBadge, MachineCard[] cardArray, int count, String typeName) {
        JPanel col = new JPanel(new BorderLayout(0, 8));
        col.setBackground(THEME_CARD_BG);
        col.setBorder(new CompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1, true),
                new EmptyBorder(10, 10, 10, 10)
        ));

        // Column Header: Stage Name + Waiting Pill
        JPanel colHeader = new JPanel(new BorderLayout());
        colHeader.setOpaque(false);

        JLabel colTitle = new JLabel(title);
        colTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        colTitle.setForeground(THEME_SECONDARY);

        colHeader.add(colTitle, BorderLayout.WEST);
        colHeader.add(queueBadge, BorderLayout.EAST);
        col.add(colHeader, BorderLayout.NORTH);

        // Responsive grid: single column for 2 or 4 machines, 2 columns for 6 machines
        // Gives each card plenty of width so text never truncates to '...'
        int rows = (count == 6) ? 3 : count;
        int cols = (count == 6) ? 2 : 1;
        JPanel grid = new JPanel(new GridLayout(rows, cols, 8, 8));
        grid.setOpaque(false);

        for (int i = 0; i < count; i++) {
            // Compact name format like "Washer 1", "Dryer 1", "Kiosk 1"
            cardArray[i] = new MachineCard(typeName + " " + (i + 1));
            grid.add(cardArray[i]);
        }

        col.add(grid, BorderLayout.CENTER);
        return col;
    }

    private JLabel createQueuePill(String initialText) {
        JLabel pill = new JLabel(initialText, SwingConstants.CENTER);
        pill.setFont(new Font("Segoe UI", Font.BOLD, 11));
        pill.setForeground(new Color(15, 23, 42));
        pill.setBackground(new Color(226, 232, 240));
        pill.setOpaque(true);
        pill.setBorder(new EmptyBorder(3, 8, 3, 8));
        return pill;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. KPI SIDEBAR
    // ─────────────────────────────────────────────────────────────────────────
    private JPanel createKpiSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(260, 0));
        sidebar.setBackground(THEME_CARD_BG);
        sidebar.setBorder(new CompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1, true),
                new EmptyBorder(14, 14, 14, 14)
        ));

        JLabel sideTitle = new JLabel("OPERATIONAL METRICS");
        sideTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sideTitle.setForeground(THEME_TEXT_MUTED);
        sideTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(sideTitle);
        sidebar.add(Box.createVerticalStrut(10));

        // Metric Cards
        metricServed       = addMetricItem(sidebar, "Completed Visits", "0 / 50", new Color(16, 185, 129));
        metricAvgTime      = addMetricItem(sidebar, "Avg Customer Cycle", "0.00 s", new Color(14, 165, 233));
        metricWasherPeak   = addMetricItem(sidebar, "Peak Washer Concurrency", "0 / 6", THEME_TEXT_MAIN);
        metricDryerPeak    = addMetricItem(sidebar, "Peak Dryer Concurrency", "0 / 4", THEME_TEXT_MAIN);
        metricWasherErrors = addMetricItem(sidebar, "Washer Mid-Cycle Faults", "0", new Color(245, 158, 11));
        metricKioskErrors  = addMetricItem(sidebar, "Kiosk Payment Faults", "0", new Color(239, 68, 68));

        sidebar.add(Box.createVerticalStrut(10));

        // Congestion Tracker for bonus mode
        JLabel meterTitle = new JLabel("Kiosk Congestion Gauge");
        meterTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        meterTitle.setForeground(THEME_TEXT_MUTED);
        meterTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(meterTitle);
        sidebar.add(Box.createVerticalStrut(4));

        congestionMeter = new JProgressBar(0, 30);
        congestionMeter.setValue(0);
        congestionMeter.setStringPainted(false);
        congestionMeter.setForeground(new Color(239, 68, 68));
        congestionMeter.setBackground(new Color(241, 245, 249));
        congestionMeter.setMaximumSize(new Dimension(Short.MAX_VALUE, 12));
        congestionMeter.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(congestionMeter);

        congestionMeterLabel = new JLabel("Queue: 0 (Owner alert threshold: 30)");
        congestionMeterLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        congestionMeterLabel.setForeground(THEME_TEXT_MUTED);
        congestionMeterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(congestionMeterLabel);

        sidebar.add(Box.createVerticalGlue());
        return sidebar;
    }

    private JLabel addMetricItem(JPanel parent, String title, String val, Color highlight) {
        JPanel block = new JPanel(new BorderLayout());
        block.setOpaque(false);
        block.setMaximumSize(new Dimension(Short.MAX_VALUE, 36));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        titleLbl.setForeground(THEME_TEXT_MUTED);

        JLabel valLbl = new JLabel(val);
        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        valLbl.setForeground(highlight);

        block.add(titleLbl, BorderLayout.WEST);
        block.add(valLbl, BorderLayout.EAST);

        parent.add(block);
        parent.add(Box.createVerticalStrut(4));
        return valLbl;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. LOWER ACTIVITY CONSOLE
    // ─────────────────────────────────────────────────────────────────────────
    private JPanel createActivityConsole() {
        JPanel console = new JPanel(new BorderLayout());
        console.setBackground(THEME_CARD_BG);
        console.setBorder(new CompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1, false),
                new EmptyBorder(8, 16, 12, 16)
        ));
        console.setPreferredSize(new Dimension(0, 200));

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(0, 0, 6, 0));

        JLabel lbl = new JLabel("SYSTEM EVENT TRACE (AUDIT LOG)");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(THEME_TEXT_MUTED);

        JButton clearBtn = new JButton("Clear Trace");
        clearBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        clearBtn.setFocusPainted(false);
        clearBtn.setBackground(new Color(241, 245, 249));
        clearBtn.setBorder(new EmptyBorder(3, 8, 3, 8));
        clearBtn.addActionListener(e -> logStream.setText(""));

        bar.add(lbl, BorderLayout.WEST);
        bar.add(clearBtn, BorderLayout.EAST);
        console.add(bar, BorderLayout.NORTH);

        logStream = new JTextArea();
        logStream.setEditable(false);
        logStream.setFont(new Font("Consolas", Font.PLAIN, 12));
        logStream.setBackground(new Color(248, 250, 252));
        logStream.setForeground(new Color(30, 41, 59));
        logStream.setMargin(new Insets(6, 8, 6, 8));

        JScrollPane scroll = new JScrollPane(logStream);
        scroll.setBorder(new LineBorder(new Color(226, 232, 240), 1));
        console.add(scroll, BorderLayout.CENTER);

        facility.setLogListener(this::appendTrace);

        return console;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SIMULATION LAUNCH LOGIC
    // ─────────────────────────────────────────────────────────────────────────
    private void onLaunchSimulation() {
        if (simExecutionThread != null && simExecutionThread.isAlive()) {
            JOptionPane.showMessageDialog(this,
                    "Simulation is currently running. Please wait for completion.",
                    "Active Simulation", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        boolean bonusMode = bonusCheckBox.isSelected();

        // Fresh state
        this.facility = new LaundryFacility();
        facility.setLogListener(this::appendTrace);
        logStream.setText("");

        launchButton.setEnabled(false);
        bonusCheckBox.setEnabled(false);
        facilityStatusBadge.setText("● SIMULATION RUNNING (" + (bonusMode ? "BONUS" : "NORMAL") + ")");
        facilityStatusBadge.setForeground(new Color(14, 165, 233));

        Runnable runner = bonusMode ? bonusSimulationRunner : normalSimulationRunner;
        if (runner == null) return;

        simExecutionThread = new Thread(() -> {
            runner.run();
            SwingUtilities.invokeLater(() -> {
                launchButton.setEnabled(true);
                bonusCheckBox.setEnabled(true);
                facilityStatusBadge.setText("● COMPLETED");
                facilityStatusBadge.setForeground(new Color(16, 185, 129));
            });
        }, "SimOrchestrator");
        simExecutionThread.setDaemon(true);
        simExecutionThread.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REAL-TIME DASHBOARD REFRESHER
    // ─────────────────────────────────────────────────────────────────────────
    private void startMetricsPoller() {
        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DashboardPoller");
            t.setDaemon(true);
            return t;
        });
        poller.scheduleAtFixedRate(
                () -> SwingUtilities.invokeLater(this::renderCurrentState),
                100, 150, TimeUnit.MILLISECONDS
        );
    }

    private void renderCurrentState() {
        if (facility == null) return;

        // 1. Washers
        List<WashingMachine> ws = facility.getWashers();
        for (int i = 0; i < ws.size(); i++) {
            washerCards[i].updateCard(ws.get(i).getState(), ws.get(i).getCurrentCustomerId());
        }

        // 2. Dryers
        List<Dryer> ds = facility.getDryers();
        for (int i = 0; i < ds.size(); i++) {
            dryerCards[i].updateCard(ds.get(i).getState(), ds.get(i).getCurrentCustomerId());
        }

        // 3. Kiosks
        List<PaymentKiosk> ks = facility.getKiosks();
        for (int i = 0; i < ks.size(); i++) {
            kioskCards[i].updateCard(ks.get(i).getState(), ks.get(i).getCurrentCustomerId());
        }

        // 4. Queues
        washerQueueBadge.setText("Waiting: " + facility.getWasherQueueLength());
        dryerQueueBadge.setText("Waiting: " + facility.getDryerQueueLength());
        int kioskWait = facility.getPaymentQueueSize();
        kioskQueueBadge.setText("Waiting: " + kioskWait);

        // 5. KPIs
        metricServed.setText(facility.getTotalServed() + " / 50");
        metricAvgTime.setText(String.format("%.2f s", facility.getAverageTimeSeconds()));
        metricWasherPeak.setText(facility.getPeakWashersInUse() + " / 6");
        metricDryerPeak.setText(facility.getPeakDryersInUse() + " / 4");
        metricWasherErrors.setText(String.valueOf(facility.getWasherFailures()));
        metricKioskErrors.setText(String.valueOf(facility.getKioskFailures()));

        // 6. Congestion Gauge
        congestionMeter.setValue(Math.min(kioskWait, 30));
        congestionMeterLabel.setText("Queue: " + kioskWait + " (Owner alert at 30)");
    }

    private void appendTrace(String logLine) {
        SwingUtilities.invokeLater(() -> {
            logStream.append(logLine + "\n");
            Document doc = logStream.getDocument();
            if (doc.getLength() > 100_000) {
                try { doc.remove(0, 30_000); } catch (BadLocationException ignored) {}
            }
            logStream.setCaretPosition(doc.getLength());
        });
    }

    public void setNormalSimulationRunner(Runnable r) { this.normalSimulationRunner = r; }
    public void setBonusSimulationRunner(Runnable r)  { this.bonusSimulationRunner  = r; }
    public LaundryFacility getFacility()               { return facility; }

    // ─────────────────────────────────────────────────────────────────────────
    // CUSTOM MACHINE CARD COMPONENT
    // ─────────────────────────────────────────────────────────────────────────
    private static class MachineCard extends JPanel {
        private final JLabel nameLabel;
        private final JLabel statusLabel;
        private final JLabel occupantBadge;

        public MachineCard(String name) {
            setLayout(new BorderLayout(4, 2));
            setBackground(COLOR_IDLE_CARD);
            setBorder(new CompoundBorder(
                    new LineBorder(new Color(226, 232, 240), 1, true),
                    new EmptyBorder(6, 8, 6, 8)
            ));

            nameLabel = new JLabel(name);
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            nameLabel.setForeground(THEME_TEXT_MAIN);

            statusLabel = new JLabel("IDLE");
            statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            statusLabel.setForeground(new Color(100, 116, 139));

            occupantBadge = new JLabel("—", SwingConstants.CENTER);
            occupantBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
            occupantBadge.setOpaque(true);
            occupantBadge.setBackground(new Color(241, 245, 249));
            occupantBadge.setForeground(new Color(148, 163, 184));
            occupantBadge.setPreferredSize(new Dimension(42, 22));
            occupantBadge.setBorder(new LineBorder(new Color(226, 232, 240), 1, true));

            JPanel textCol = new JPanel();
            textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
            textCol.setOpaque(false);
            textCol.add(nameLabel);
            textCol.add(statusLabel);

            add(textCol, BorderLayout.CENTER);
            add(occupantBadge, BorderLayout.EAST);
        }

        public void updateCard(MachineState state, int customerId) {
            switch (state) {
                case IN_USE:
                    setBackground(new Color(239, 246, 255)); // Soft blue
                    setBorder(new CompoundBorder(
                            new LineBorder(COLOR_ACTIVE, 1, true),
                            new EmptyBorder(6, 8, 6, 8)
                    ));
                    statusLabel.setText("ACTIVE");
                    statusLabel.setForeground(COLOR_ACTIVE);
                    occupantBadge.setText("C" + customerId);
                    occupantBadge.setBackground(COLOR_ACTIVE);
                    occupantBadge.setForeground(Color.WHITE);
                    break;
                case FAILED:
                    setBackground(new Color(254, 242, 242)); // Soft red
                    setBorder(new CompoundBorder(
                            new LineBorder(COLOR_FAULT, 1, true),
                            new EmptyBorder(6, 8, 6, 8)
                    ));
                    statusLabel.setText("FAULT");
                    statusLabel.setForeground(COLOR_FAULT);
                    occupantBadge.setText(customerId > 0 ? "C" + customerId + "!" : "FAIL");
                    occupantBadge.setBackground(COLOR_FAULT);
                    occupantBadge.setForeground(Color.WHITE);
                    break;
                case RETRYING:
                    setBackground(new Color(255, 251, 235)); // Soft amber
                    setBorder(new CompoundBorder(
                            new LineBorder(COLOR_RETRYING, 1, true),
                            new EmptyBorder(6, 8, 6, 8)
                    ));
                    statusLabel.setText("RETRY");
                    statusLabel.setForeground(COLOR_RETRYING);
                    occupantBadge.setText("C" + customerId);
                    occupantBadge.setBackground(COLOR_RETRYING);
                    occupantBadge.setForeground(Color.WHITE);
                    break;
                default:
                    setBackground(COLOR_IDLE_CARD);
                    setBorder(new CompoundBorder(
                            new LineBorder(new Color(226, 232, 240), 1, true),
                            new EmptyBorder(6, 8, 6, 8)
                    ));
                    statusLabel.setText("IDLE");
                    statusLabel.setForeground(new Color(100, 116, 139));
                    occupantBadge.setText("—");
                    occupantBadge.setBackground(new Color(241, 245, 249));
                    occupantBadge.setForeground(new Color(148, 163, 184));
                    break;
            }
        }
    }
}
