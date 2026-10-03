package dev.yinghuang.legacyforgebridge.desktop;

import javax.swing.*;
import java.awt.*;
import java.util.Properties;

public final class DesktopProgressUiTest {
    private static int checks;
    private static void ok(boolean v, String m) { checks++; if (!v) throw new AssertionError(m); }
    private static <T extends Component> T find(Container root, String name, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && name.equals(c.getName())) return type.cast(c);
            if (c instanceof Container nested) {
                T value=find(nested,name,type); if(value!=null)return value;
            }
        }
        return null;
    }
    private static boolean textContains(Container root, String needle) {
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel l && l.getText()!=null && l.getText().contains(needle)) return true;
            if (c instanceof JTextArea a && a.getText()!=null && a.getText().contains(needle)) return true;
            if (c instanceof Container nested && textContains(nested,needle)) return true;
        }
        return false;
    }
    private static JFrame fixture() {
        JFrame frame=new JFrame("old title");
        JPanel root=new JPanel();root.setLayout(new BoxLayout(root,BoxLayout.Y_AXIS));
        root.add(new JLabel("正在轉換"));
        root.add(new JLabel("模組 0 / 0"));
        root.add(new JLabel("轉換時長: 0 秒"));
        JToggleButton details=new JToggleButton("詳細資訊");details.setName("details-toggle");
        JPanel expanded=new JPanel();expanded.setLayout(new BoxLayout(expanded,BoxLayout.Y_AXIS));expanded.setVisible(false);
        expanded.add(new JTextArea("conversion details",3,30));
        details.addActionListener(e->{expanded.setVisible(details.isSelected());frame.pack();});
        root.add(details);root.add(expanded);
        JPanel buttons=new JPanel(new FlowLayout());buttons.add(new JButton("diag"));buttons.add(new JButton("cancel"));root.add(buttons);
        frame.setContentPane(root);frame.pack();
        return frame;
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(()->{
            JFrame frame=fixture();
            DesktopProgressUi.install(frame);
            JToggleButton details=find(frame.getContentPane(),"details-toggle",JToggleButton.class);
            ok(details!=null,"details toggle exists");
            ok(details.isBorderPainted(),"details has visible border");
            ok(details.isContentAreaFilled(),"details has visible content area");
            ok(details.isOpaque(),"details is opaque");
            ok(details.getText().contains("詳細資訊"),"details label retained");
            ok(frame.getTitle().equals("LegacyForgeBridge | 處理中 | by YingHunag09"),"initial title");

            details.doClick();
            ok(details.isSelected(),"details selected");
            ok(details.getText().contains("收合詳細資訊"),"details text reflects expanded state");
            JButton support=find(frame.getContentPane(),"supported-mods-button",JButton.class);
            ok(support!=null,"support list button added inside details");
            ok(support.isVisible(),"support button visible when expanded");

            Properties p=new Properties();p.setProperty("percent","41");p.setProperty("index","2");p.setProperty("total","3");p.setProperty("elapsedSeconds","12.2");
            DesktopProgressUi.update(frame,p,"VERIFYING");
            ok(frame.getTitle().equals("LegacyForgeBridge | 驗證中 | by YingHunag09"),"dynamic title verifying");
            DesktopProgressUi.update(frame,p,"WARN");
            ok(frame.getTitle().equals("LegacyForgeBridge | 警告 | by YingHunag09"),"dynamic title warning");

            support.doClick();
            Window[] owned=frame.getOwnedWindows();
            JDialog dialog=null;for(Window w:owned)if(w instanceof JDialog d && d.isDisplayable())dialog=d;
            ok(dialog!=null,"support child dialog opened");
            ok(dialog.getTitle().equals("LegacyForgeBridge | 支援模組 | by YingHunag09"),"support child title");
            ok(textContains(dialog.getContentPane(),"RPGTool1 1.1"),"RPGTool listed");
            ok(textContains(dialog.getContentPane(),"BambooMod 2.6.8.5"),"Bamboo listed");
            ok(textContains(dialog.getContentPane(),"iYAMATO's Mod 1.7.10-1.6.8"),"iYAMATO listed");
            ok(textContains(dialog.getContentPane(),"不是依模組名稱建立白名單"),"generic-not-allowlist explanation");
            ok(textContains(dialog.getContentPane(),"其他模組，也可能可由通用轉換正常運作"),"similar-mod compatibility explanation");
            dialog.dispose();frame.dispose();
        });
        System.out.println("PASS DesktopProgressUiTest checks="+checks);
    }
}
