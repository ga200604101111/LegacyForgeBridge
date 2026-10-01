package dev.yinghuang.legacyforgebridge.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

/**
 * Compact conversion-status presentation for the desktop helper.
 *
 * Layout (collapsed):
 *   狀態: ...
 *   模組 n / total                         轉換時長: x 秒
 *   [progress]
 *   詳細資訊
 *   [轉換診斷] [取消自動退出/重啟]
 */
public final class DesktopProgressUi {
    private static final Map<JFrame, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private DesktopProgressUi() {}

    public static void install(JFrame frame) {
        if (frame == null) return;
        Runnable task = () -> installEdt(frame);
        if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task);
    }

    private static void installEdt(JFrame frame) {
        if (STATES.containsKey(frame)) return;
        Container content = frame.getContentPane();
        if (!(content instanceof JPanel root)) return;

        JToggleButton details = findNamed(root, "details-toggle", JToggleButton.class);
        if (details == null) return;

        JLabel heading = null, source = null, elapsed = null;
        for (Component component : root.getComponents()) {
            if (component instanceof JLabel label) {
                if (heading == null) heading = label;
                else if (source == null) source = label;
                else if (elapsed == null) { elapsed = label; break; }
            }
        }
        if (heading == null || source == null || elapsed == null) return;

        JPanel expanded = null;
        JPanel buttons = null;
        for (Component component : root.getComponents()) {
            if (component instanceof JPanel panel) {
                if (panel == root) continue;
                if (isDescendant(panel, details)) continue;
                if (containsDirectButton(panel)) buttons = panel;
                else if (expanded == null) expanded = panel;
            }
        }
        if (expanded == null || buttons == null) return;

        details.setText("詳細資訊");
        details.setName("details-toggle");
        details.setFocusPainted(false);
        details.setBorderPainted(false);
        details.setContentAreaFilled(false);
        details.setOpaque(false);
        details.setMargin(new Insets(0, 0, 0, 0));
        details.setHorizontalAlignment(SwingConstants.LEFT);
        details.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton[] actions = directButtons(buttons);
        if (actions.length > 0) actions[0].setText("轉換診斷");
        if (actions.length > 1) actions[1].setText("取消自動退出/重啟");

        JPanel infoRow = new JPanel(new BorderLayout(12, 0));
        infoRow.setOpaque(false);
        infoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(24, source.getPreferredSize().height + 4)));
        source.setHorizontalAlignment(SwingConstants.LEFT);
        elapsed.setHorizontalAlignment(SwingConstants.RIGHT);
        infoRow.add(source, BorderLayout.WEST);
        infoRow.add(elapsed, BorderLayout.EAST);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setName("conversion-progress");
        bar.setValue(0);
        bar.setStringPainted(true);
        bar.setString("0%");
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        bar.setPreferredSize(new Dimension(594, 24));
        bar.setBorder(new EmptyBorder(0, 0, 0, 0));

        root.removeAll();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.add(heading);
        root.add(Box.createVerticalStrut(10));
        root.add(infoRow);
        root.add(Box.createVerticalStrut(8));
        root.add(bar);
        root.add(Box.createVerticalStrut(10));
        root.add(details);
        root.add(expanded);
        root.add(Box.createVerticalStrut(12));
        root.add(buttons);

        STATES.put(frame, new State(bar, heading, source, elapsed, details, expanded, buttons));
        root.revalidate();
        root.repaint();
        frame.setMinimumSize(new Dimension(760, 198));
        frame.pack();
    }

    public static void update(JFrame frame, Properties properties, String stateName) {
        if (frame == null || properties == null) return;
        Runnable task = () -> updateEdt(frame, properties, stateName);
        if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task);
    }

    private static void updateEdt(JFrame frame, Properties properties, String stateName) {
        installEdt(frame);
        State ui = STATES.get(frame);
        if (ui == null) return;

        String state = stateName == null ? "" : stateName.strip();
        boolean conversionFinished = Boolean.parseBoolean(properties.getProperty("conversionFinished", "false"));
        boolean problem = isProblem(state);
        int percent = clamp(parseInt(properties.getProperty("percent"), 0));
        if (conversionFinished && !problem) percent = 100;
        ui.bar.setValue(percent);
        ui.bar.setString(percent + "%");

        String pass = clean(properties.getProperty("pass", ""), 96);
        String phase = clean(properties.getProperty("phase", ""), 160);
        String passProgress = clean(properties.getProperty("passProgress", ""), 64);
        String tooltip = joinNonBlank(" — ", pass, phase, passProgress);
        ui.bar.setToolTipText(tooltip.isBlank() ? null : tooltip);

        Properties snapshot = new Properties();
        snapshot.putAll(properties);
        String stateSnapshot = state;
        SwingUtilities.invokeLater(() -> applyRequestedText(ui, snapshot, stateSnapshot));

        if (problem) {
            if (ui.closeTimer != null) { ui.closeTimer.stop(); ui.closeTimer = null; }
            if (ui.hidden) {
                frame.setVisible(true);
                frame.setState(Frame.NORMAL);
                frame.toFront();
                ui.hidden = false;
            }
            return;
        }

        if (conversionFinished && ui.closeTimer == null && !ui.hidden) {
            ui.closeTimer = new javax.swing.Timer(3000, event -> {
                frame.setVisible(false);
                ui.hidden = true;
                javax.swing.Timer timer = ui.closeTimer;
                ui.closeTimer = null;
                if (timer != null) timer.stop();
            });
            ui.closeTimer.setRepeats(false);
            ui.closeTimer.start();
        }
    }

    private static void applyRequestedText(State ui, Properties properties, String stateName) {
        String current = clean(ui.heading.getText(), 120);
        current = stripStatusPrefix(current);
        if (current.isBlank()) current = displayState(stateName);
        ui.heading.setText("狀態: " + current);

        int index = Math.max(0, parseInt(properties.getProperty("index"), 0));
        int total = Math.max(0, parseInt(properties.getProperty("total"), 0));
        ui.source.setText("模組 " + index + " / " + total);

        double seconds = parseDouble(properties.getProperty("elapsedSeconds"), -1D);
        if (seconds < 0D) seconds = elapsedFromEpoch(properties.getProperty("startedEpoch"));
        ui.elapsed.setText("轉換時長: " + formatSeconds(seconds) + " 秒");

        ui.details.setText("詳細資訊");
        JButton[] actions = directButtons(ui.buttons);
        if (actions.length > 0) actions[0].setText("轉換診斷");
        if (actions.length > 1) actions[1].setText("取消自動退出/重啟");
    }

    private static String displayState(String state) {
        return switch (state == null ? "" : state) {
            case "ERROR" -> "錯誤";
            case "WARN" -> "警告";
            case "MANUAL" -> "需要手動處理";
            case "DONE" -> "完成";
            case "VERIFYING" -> "驗證中";
            case "READY" -> "準備完成";
            case "COUNTDOWN" -> "等待自動退出/重啟";
            case "STOPPING" -> "正在退出";
            case "WAITING_CLIENT" -> "等待客戶端";
            case "WAITING_GAME_READY" -> "等待遊戲就緒";
            case "LAUNCH_SENT" -> "已送出重啟";
            case "LAUNCH_FAILED" -> "重啟失敗";
            default -> state == null || state.isBlank() ? "處理中" : state;
        };
    }

    private static boolean isProblem(String state) {
        return "ERROR".equals(state) || "WARN".equals(state) || "MANUAL".equals(state) || "LAUNCH_FAILED".equals(state);
    }

    private static String stripStatusPrefix(String text) {
        if (text == null) return "";
        String s = text.strip();
        if (s.startsWith("狀態:")) return s.substring(3).strip();
        if (s.startsWith("狀態：")) return s.substring(3).strip();
        return s;
    }

    private static double elapsedFromEpoch(String value) {
        double started = parseDouble(value, -1D);
        if (started <= 0D) return 0D;
        double nowMillis = System.currentTimeMillis();
        if (started < 10_000_000_000L) started *= 1000D;
        return Math.max(0D, (nowMillis - started) / 1000D);
    }

    private static String formatSeconds(double value) {
        if (!Double.isFinite(value) || value < 0D) return "0";
        if (value < 10D) return String.format(java.util.Locale.ROOT, "%.1f", value);
        return Long.toString(Math.round(value));
    }

    private static int parseInt(String text, int fallback) {
        try { return Integer.parseInt(text); } catch (RuntimeException ignored) { return fallback; }
    }
    private static double parseDouble(String text, double fallback) {
        try { return Double.parseDouble(text); } catch (RuntimeException ignored) { return fallback; }
    }
    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    private static String clean(String value, int max) {
        if (value == null) return "";
        value = value.replace('\r', ' ').replace('\n', ' ').strip();
        return value.length() <= max ? value : value.substring(0, max);
    }
    private static String joinNonBlank(String separator, String... values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) if (value != null && !value.isBlank()) {
            if (!out.isEmpty()) out.append(separator);
            out.append(value);
        }
        return out.toString();
    }

    private static boolean containsDirectButton(JPanel panel) {
        for (Component c : panel.getComponents()) if (c instanceof JButton) return true;
        return false;
    }
    private static JButton[] directButtons(JPanel panel) {
        java.util.List<JButton> out = new java.util.ArrayList<>();
        for (Component c : panel.getComponents()) if (c instanceof JButton button) out.add(button);
        return out.toArray(JButton[]::new);
    }
    private static boolean isDescendant(Container parent, Component child) {
        for (Component c = child; c != null; c = c.getParent()) if (c == parent) return true;
        return false;
    }
    private static <T extends Component> T findNamed(Container root, String name, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && name.equals(c.getName())) return type.cast(c);
            if (c instanceof Container nested) {
                T found = findNamed(nested, name, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class State {
        final JProgressBar bar;
        final JLabel heading;
        final JLabel source;
        final JLabel elapsed;
        final JToggleButton details;
        final JPanel expanded;
        final JPanel buttons;
        javax.swing.Timer closeTimer;
        boolean hidden;
        State(JProgressBar bar, JLabel heading, JLabel source, JLabel elapsed,
              JToggleButton details, JPanel expanded, JPanel buttons) {
            this.bar = bar;
            this.heading = heading;
            this.source = source;
            this.elapsed = elapsed;
            this.details = details;
            this.expanded = expanded;
            this.buttons = buttons;
        }
    }
}
