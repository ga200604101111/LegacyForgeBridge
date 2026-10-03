package dev.yinghuang.legacyforgebridge.desktop;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Properties;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Desktop-only view. No converter, game, network, restart, or source-mod writes. */
public final class Rev247SupportWindow {
    private static final String DIALOG_KEY="lfb.rev247.supportDialog";
    private static final String SNAPSHOT_KEY="lfb.rev247.statusSnapshot";
    private static final String VERSION="0.2.0-alpha.27-corpus4-local.30-rev247-ui-cache.1";
    private Rev247SupportWindow() {}

    public static void open(JFrame owner) {
        if(!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(()->open(owner)); return; }
        Object old=owner.getRootPane().getClientProperty(DIALOG_KEY);
        if(old instanceof JDialog dialog && dialog.isDisplayable()) {
            dialog.setVisible(true);dialog.toFront();dialog.requestFocus();return;
        }
        JDialog dialog=new JDialog(owner,"LegacyForgeBridge | 支援模組 | by YingHunag09",false);
        dialog.setName("supported-mods-dialog");
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setIconImages(owner.getIconImages());
        owner.getRootPane().putClientProperty(DIALOG_KEY,dialog);
        Font font=readableFont(owner);
        Column body=new Column(12);
        body.setName("supported-mods-content");
        body.setBorder(new EmptyBorder(18,20,18,20));
        body.add(paragraph("目前支援／驗證中的模組",font.deriveFont(Font.BOLD,18),"support-heading"));
        body.add(paragraph("LegacyForgeBridge 採用通用、來源驅動的轉換規則，不是依模組名稱建立白名單。使用與下列模組相似的 Forge 1.7.10 API 或行為結構，其他模組也可能通過轉換並正常運作；仍以實際轉換結果和遊戲驗證為準。",font,"support-intro"));
        body.add(entry(font,"RPGTool1 1.1 · Minecraft 1.7.10","已有歷史實機驗證；目前仍在回歸測試",
                "物品、武器與資源已有先前測試基線。本次仍在調查跳躍同步；此標示不代表目前版本所有功能都已驗證或跳躍問題已修復。"));
        body.add(entry(font,"BambooMod 2.6.8.5","部分相容／持續驗證",
                "已有物品、方塊、配方、事件與部分呈現及互動路徑的來源分析和測試。GUI、處理器、實體等功能依各自的轉換證據判定，不宣稱整包完整支援。"));
        body.add(entry(font,"iYAMATO's Mod 1.7.10-1.6.8","部分相容／持續驗證",
                "已有方塊材質、創造分頁及部分武器、投射物和遠端實體呈現的來源與實機紀錄；未驗證的功能仍可能無法正常運作。"));
        body.add(paragraph("通用轉換說明",font.deriveFont(Font.BOLD),"generic-heading"));
        body.add(paragraph("相似的 GameRegistry、Item／Block、配方、NBT、事件、DataWatcher 或舊版渲染結構，也可能套用相同轉換規則。模組未列出不代表一定不支援；列出也不代表所有版本及功能保證相容。",font,"generic-description"));
        JScrollPane scroll=new JScrollPane(body,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("support-scroll");scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        JPanel shell=new JPanel(new BorderLayout());shell.add(scroll,BorderLayout.CENTER);
        JButton close=new JButton("關閉");close.setName("support-close");close.setFont(font);
        close.addActionListener(e->dialog.dispose());
        JPanel footer=new JPanel(new FlowLayout(FlowLayout.RIGHT,16,10));footer.add(close);shell.add(footer,BorderLayout.SOUTH);
        dialog.setContentPane(shell);
        dialog.getRootPane().setDefaultButton(close);
        dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        // Width can be resized; the CONTENT has no horizontal pan and follows the viewport.
        Rectangle screen=owner.getGraphicsConfiguration().getBounds();
        dialog.setMinimumSize(new Dimension(Math.min(480,screen.width),Math.min(340,screen.height)));
        dialog.setSize(Math.min(720,screen.width-40),Math.min(650,screen.height-60));
        dialog.setLocationRelativeTo(owner);dialog.setVisible(true);
    }

    public static boolean deferAutoHide(JFrame owner,ActionEvent event) {
        Object value=owner.getRootPane().getClientProperty(DIALOG_KEY);
        if(value instanceof JDialog d && d.isDisplayable() && d.isVisible()) {
            if(event.getSource() instanceof javax.swing.Timer t)t.restart();
            return true;
        }
        return false;
    }

    /** Called on the existing helper EDT update; copies only an explicit, non-secret key set. */
    public static void observe(JFrame frame,Properties properties,String state) {
        if(frame==null||properties==null)return;
        Properties snapshot=new Properties();snapshot.setProperty("uiArtifactVersion",VERSION);
        snapshot.setProperty("state",state==null?"":state);
        for(String key:new String[]{"phase","pass","passProgress","message","resultSummary","warningCount","errorCount","index","total","percent","conversionFinished","elapsedSeconds"}) {
            String value=properties.getProperty(key);
            if(value!=null)snapshot.setProperty(key,value.length()>8000?value.substring(0,8000)+" [truncated]":value);
        }
        frame.getRootPane().putClientProperty(SNAPSHOT_KEY,snapshot);
        JButton support=find(frame.getContentPane(),"supported-mods-button",JButton.class);
        if(support==null||find(frame.getContentPane(),"copy-diagnostics-button",JButton.class)!=null)return;
        JButton copy=new JButton("複製診斷摘要");copy.setName("copy-diagnostics-button");copy.setFont(support.getFont());
        copy.setAlignmentX(Component.LEFT_ALIGNMENT);copy.setToolTipText("只複製目前狀態、轉換階段及摘要，不上傳資料；完整例外仍以日誌為準。");
        copy.addActionListener(e->{
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(diagnosticText(frame)),null);
                copy.setToolTipText("已複製。完整錯誤請同時保留 logs/legacyforgebridge.log 與轉換診斷報告。");
            } catch(RuntimeException ex) { copy.setToolTipText("無法存取剪貼簿："+ex.getClass().getSimpleName()); }
        });
        Container area=support.getParent();area.add(Box.createVerticalStrut(6));area.add(copy);area.revalidate();
    }
    static String diagnosticText(JFrame frame) {
        Object value=frame.getRootPane().getClientProperty(SNAPSHOT_KEY);
        if(!(value instanceof Properties p))return "尚無轉換狀態";
        StringBuilder out=new StringBuilder("LegacyForgeBridge 診斷摘要（不是完整錯誤堆疊）\n");
        p.stringPropertyNames().stream().sorted().forEach(k->out.append(k).append('=').append(p.getProperty(k)).append('\n'));
        return out.toString();
    }
    public static String displayState(String state) {
        return switch(state==null?"":state) {
            case "ERROR"->"轉換錯誤";case "WARN"->"警告";case "MANUAL"->"需要手動處理";
            case "CONVERTING"->"轉換中";case "DONE"->"完成";case "VERIFYING"->"驗證中";
            case "READY"->"準備完成";case "COUNTDOWN"->"等待自動退出/重啟";case "STOPPING"->"正在退出";
            case "WAITING_CLIENT"->"等待客戶端";case "WAITING_GAME_READY"->"等待遊戲就緒";
            case "LAUNCH_SENT"->"已送出重啟";case "LAUNCH_FAILED"->"重啟失敗";
            default->state==null||state.isBlank()?"處理中":state;
        };
    }
    private static Font readableFont(JFrame owner) {
        java.util.List<String> available=java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for(String name:new String[]{"Microsoft JhengHei","Noto Sans CJK TC","Noto Sans CJK SC","WenQuanYi Micro Hei"})
            if(available.contains(name))return new Font(name,Font.PLAIN,14);
        Font value=UIManager.getFont("Label.font");return value==null?new Font("Dialog",Font.PLAIN,14):value.deriveFont(14F);
    }
    private static Column entry(Font font,String name,String status,String description) {
        Column box=new Column(7);box.setName("support-entry");
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(),new EmptyBorder(12,12,12,12)));
        box.add(paragraph(name,font.deriveFont(Font.BOLD,15),"mod-name"));
        box.add(paragraph(status,font.deriveFont(Font.BOLD),"mod-status"));
        box.add(paragraph(description,font,"mod-description"));return box;
    }
    private static JTextArea paragraph(String text,Font font,String name) {
        JTextArea area=new JTextArea(text);area.setName(name);area.setFont(font);
        area.setLineWrap(true);area.setWrapStyleWord(true);area.setEditable(false);
        area.setOpaque(false);area.setBorder(null);area.setMargin(new Insets(0,0,0,0));
        area.setMinimumSize(new Dimension(0,0));area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }
    private static <T extends Component>T find(Container root,String name,Class<T> type) {
        for(Component c:root.getComponents()) {
            if(type.isInstance(c)&&name.equals(c.getName()))return type.cast(c);
            if(c instanceof Container n){T value=find(n,name,type);if(value!=null)return value;}
        }return null;
    }
    /** A width-tracking column: preferred height is measured AFTER assigning wrap width. */
    static final class Column extends JPanel implements Scrollable {
        private final int gap;
        Column(int gap){super(null);this.gap=gap;setAlignmentX(Component.LEFT_ALIGNMENT);}
        private int heightAt(int width,boolean layout) {
            Insets in=getInsets();int childWidth=Math.max(1,width-in.left-in.right),y=in.top,count=0;
            for(Component c:getComponents())if(c.isVisible()) {
                if(count++>0)y+=gap;
                int h;
                if(c instanceof Column nested)h=nested.heightAt(childWidth,false);
                else {c.setSize(childWidth,Integer.MAX_VALUE/1024);h=c.getPreferredSize().height;}
                if(layout)c.setBounds(in.left,y,childWidth,h);
                y+=h;
            }
            return y+in.bottom;
        }
        @Override public void doLayout(){heightAt(getWidth(),true);}
        @Override public Dimension getPreferredSize(){int w=getWidth()>0?getWidth():640;return new Dimension(w,heightAt(w,false));}
        @Override public Dimension getMinimumSize(){return new Dimension(0,0);}
        @Override public Dimension getPreferredScrollableViewportSize(){return new Dimension(640,550);}
        @Override public boolean getScrollableTracksViewportWidth(){return true;}
        @Override public boolean getScrollableTracksViewportHeight(){return false;}
        @Override public int getScrollableUnitIncrement(Rectangle r,int o,int d){return 24;}
        @Override public int getScrollableBlockIncrement(Rectangle r,int o,int d){return Math.max(24,r.height-24);}
    }
}
