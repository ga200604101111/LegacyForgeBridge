package dev.yinghuang.legacyforgebridge.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

/** Compact conversion progress UI layered onto the existing desktop helper window. */
public final class DesktopProgressUi {
    private static final Map<JFrame, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private DesktopProgressUi() {}

    public static void install(JFrame frame) {
        if (frame == null) return;
        Runnable task = () -> {
            if (STATES.containsKey(frame)) return;
            JProgressBar bar = new JProgressBar(0, 100);
            bar.setName("conversion-progress");
            bar.setValue(0);
            bar.setStringPainted(true);
            bar.setString("0%");
            bar.setAlignmentX(Component.LEFT_ALIGNMENT);
            bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            bar.setPreferredSize(new Dimension(580, 24));
            bar.setBorder(new EmptyBorder(0, 0, 10, 0));

            Container content = frame.getContentPane();
            int index = content.getComponentCount();
            for (int i = 0; i < content.getComponentCount(); i++) {
                Component c = content.getComponent(i);
                if ("details-toggle".equals(c.getName())) { index = i; break; }
            }
            content.add(bar, index);
            STATES.put(frame, new State(bar));
            content.revalidate();
            content.repaint();
            frame.pack();
        };
        if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task);
    }

    public static void update(JFrame frame, Properties properties, String stateName) {
        if (frame == null || properties == null) return;
        Runnable task = () -> {
            install(frame);
            State ui = STATES.get(frame);
            if (ui == null) return;

            String state = stateName == null ? "" : stateName;
            boolean conversionFinished = Boolean.parseBoolean(properties.getProperty("conversionFinished", "false"));
            boolean problem = "ERROR".equals(state) || "WARN".equals(state) || "MANUAL".equals(state);
            int percent = clamp(parseInt(properties.getProperty("percent"), 0));
            if (conversionFinished && !problem) percent = 100;
            ui.bar.setValue(percent);

            String passProgress = clean(properties.getProperty("passProgress", ""), 48);
            String pass = clean(properties.getProperty("pass", ""), 72);
            String phase = clean(properties.getProperty("phase", ""), 160);
            if (conversionFinished && !problem) {
                ui.bar.setString("100%  ·  轉換完成，3 秒後自動關閉");
            } else if (!passProgress.isBlank()) {
                ui.bar.setString(percent + "%  ·  " + passProgress);
            } else {
                ui.bar.setString(percent + "%");
            }
            if (!pass.isBlank() || !phase.isBlank()) {
                ui.bar.setToolTipText((pass.isBlank() ? "" : pass) + (pass.isBlank() || phase.isBlank() ? "" : " — ") + phase);
            }

            if (problem) {
                if (ui.closeTimer != null) { ui.closeTimer.stop(); ui.closeTimer = null; }
                if (ui.hidden) { frame.setVisible(true); frame.setState(Frame.NORMAL); frame.toFront(); ui.hidden = false; }
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
        };
        if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task);
    }

    private static int parseInt(String text, int fallback) {
        try { return Integer.parseInt(text); } catch (RuntimeException ignored) { return fallback; }
    }
    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    private static String clean(String value, int max) {
        if (value == null) return "";
        value = value.replace('\r', ' ').replace('\n', ' ').strip();
        return value.length() <= max ? value : value.substring(0, max);
    }
    private static final class State {
        final JProgressBar bar;
        javax.swing.Timer closeTimer;
        boolean hidden;
        State(JProgressBar bar) { this.bar = bar; }
    }
}
