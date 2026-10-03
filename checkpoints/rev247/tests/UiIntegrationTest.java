package dev.yinghuang.legacyforgebridge.desktop;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Uses the ACTUAL embedded DesktopHelper + real Swing under a virtual display. No Minecraft. */
public final class UiIntegrationTest {
    static int checks;static JFrame frame;static JDialog dialog;static Object helper;static Method show;
    static java.util.List<Throwable> errors=Collections.synchronizedList(new ArrayList<>());
    static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static <T extends Component>T find(Container root,String name,Class<T> type){for(Component c:root.getComponents()){
        if(type.isInstance(c)&&name.equals(c.getName()))return type.cast(c);
        if(c instanceof Container n){T r=find(n,name,type);if(r!=null)return r;}}
        return null;}
    static java.util.List<Component> all(Container c){java.util.List<Component> a=new ArrayList<>();for(Component x:c.getComponents()){a.add(x);if(x instanceof Container n)a.addAll(all(n));}return a;}
    static void pump()throws Exception{for(int i=0;i<4;i++)SwingUtilities.invokeAndWait(()->{});}
    static void snapshot(Path p,Window w)throws Exception{BufferedImage b=new BufferedImage(w.getWidth(),w.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=b.createGraphics();w.paint(g);g.dispose();ImageIO.write(b,"png",p.toFile());}
    static void layout(Container c){c.doLayout();for(Component x:c.getComponents())if(x instanceof Container n)layout(n);}
    public static void main(String[] args)throws Exception{
        boolean baseline=args[0].equals("baseline");Path out=Path.of(args[1]);Files.createDirectories(out);
        Thread.setDefaultUncaughtExceptionHandler((t,e)->errors.add(e));
        try{
            Path game=Files.createTempDirectory("lfb-ui-test-").toRealPath();String id=UUID.randomUUID().toString();
            Path session=game.resolve("legacy-cache/desktop/sessions").resolve(id);Files.createDirectories(session);
            Properties p=new Properties();p.setProperty("session",id);p.setProperty("game",game.toString());
            p.setProperty("parent.pid",Long.toString(ProcessHandle.current().pid()));p.setProperty("parent.start",ProcessHandle.current().info().startInstant().orElseThrow().toString());
            p.setProperty("state","CONVERTING");p.setProperty("percent","41");p.setProperty("index","2");p.setProperty("total","3");p.setProperty("elapsedSeconds","12");
            p.setProperty("message","UI 測試狀態，不是實際遊戲錯誤");p.setProperty("pass","fixture:test-only");
            p.setProperty("accessToken","TEST_SECRET_MUST_NOT_COPY");
            try(var s=Files.newOutputStream(session.resolve("status.properties"))){p.store(s,"test");}
            Class<?> h=Class.forName("dev.yinghuang.legacyforgebridge.desktop.DesktopHelper");
            Constructor<?> ctor=h.getDeclaredConstructor(Path.class);ctor.setAccessible(true);helper=ctor.newInstance(session);
            Method create=h.getDeclaredMethod("createWindow");create.setAccessible(true);show=h.getDeclaredMethod("show",Properties.class);show.setAccessible(true);
            SwingUtilities.invokeAndWait(()->{try{create.invoke(helper);Field f=h.getDeclaredField("frame");f.setAccessible(true);frame=(JFrame)f.get(helper);}catch(Exception e){throw new RuntimeException(e);}});
            pump();
            SwingUtilities.invokeAndWait(()->{try{show.invoke(helper,p);}catch(Exception e){throw new RuntimeException(e);}});pump();
            SwingUtilities.invokeAndWait(()->{
                JToggleButton b=find(frame.getContentPane(),"details-toggle",JToggleButton.class);ok(b!=null,"real helper toggle");
                ok(b.isBorderPainted()&&b.isContentAreaFilled(),"button style");b.doClick();
                JButton support=find(frame.getContentPane(),"supported-mods-button",JButton.class);ok(support!=null&&support.isShowing(),"support inside real expanded panel");support.doClick();
                for(Window w:frame.getOwnedWindows())if(w instanceof JDialog d&&d.isVisible())dialog=d;
                ok(dialog!=null,"real child dialog");
            });pump();
            if(baseline){
                SwingUtilities.invokeAndWait(()->{
                    JScrollPane sp=all(dialog).stream().filter(x->x instanceof JScrollPane).map(x->(JScrollPane)x).findFirst().orElseThrow();
                    System.out.println("BASELINE visibleHorizontal="+sp.getHorizontalScrollBar().isVisible()+" viewWidth="+sp.getViewport().getViewSize().width+" extentWidth="+sp.getViewport().getExtentSize().width);
                    try{snapshot(out.resolve("baseline-support.png"),dialog);}catch(Exception e){throw new RuntimeException(e);}
                });return;
            }
            SwingUtilities.invokeAndWait(()->{
                ok(frame.getTitle().equals("LegacyForgeBridge | 轉換中 | by YingHunag09"),"CONVERTING localized");
                JButton copy=find(frame.getContentPane(),"copy-diagnostics-button",JButton.class);ok(copy!=null,"diagnostic copy button");
                String text=Rev247SupportWindow.diagnosticText(frame);ok(text.contains("fixture:test-only"),"actual pass copied");ok(!text.contains("TEST_SECRET")&&!text.contains("accessToken"),"secret key excluded");
                JButton support=find(frame.getContentPane(),"supported-mods-button",JButton.class);support.doClick();
                ok(Arrays.stream(frame.getOwnedWindows()).filter(w->w instanceof JDialog&&w.isVisible()).count()==1,"single child on repeated clicks");
            });
            for(int width:new int[]{480,650,720,1000}){
                SwingUtilities.invokeAndWait(()->{dialog.setSize(width,540);dialog.validate();});pump();
                SwingUtilities.invokeAndWait(()->{
                    JScrollPane s=find(dialog,"support-scroll",JScrollPane.class);layout(dialog);
                    ok(s.getHorizontalScrollBarPolicy()==JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,"no horizontal policy "+width);
                    ok(!s.getHorizontalScrollBar().isVisible(),"no horizontal bar "+width);
                    ok(s.getViewport().getViewSize().width==s.getViewport().getExtentSize().width,"viewport width matches "+width);
                    ok(s.getViewport().getViewPosition().x==0,"no horizontal offset "+width);
                    for(Component c:all(s.getViewport()))if(c instanceof JTextArea a){
                        ok(a.getWidth()>0,"text has width");
                        try {var last=a.modelToView2D(a.getDocument().getLength());ok(last!=null&&last.getMaxY()<=a.getHeight()+2,"text not clipped "+a.getName()+" width="+width+" last="+last+" height="+a.getHeight());}
                        catch(Exception e){throw new RuntimeException(e);}
                    }
                    if(width==720)try{s.getVerticalScrollBar().setValue(0);snapshot(out.resolve("support-window.png"),dialog);}catch(Exception e){throw new RuntimeException(e);}
                    s.getVerticalScrollBar().setValue(s.getVerticalScrollBar().getMaximum());
                    JTextArea bottom=find(dialog,"generic-description",JTextArea.class);
                    ok(bottom!=null,"generic explanation exists");
                    ok(s.getViewport().getViewPosition().x==0,"vertical scroll never changes x");
                });pump();
            }
            SwingUtilities.invokeAndWait(()->{
                javax.swing.Timer timer=new javax.swing.Timer(3000,e->{});timer.setRepeats(false);
                ok(Rev247SupportWindow.deferAutoHide(frame,new ActionEvent(timer,0,"test")),"reading child defers hide");ok(timer.isRunning(),"hide timer restarted");timer.stop();
                p.setProperty("state","ERROR");p.setProperty("message","測試用轉換錯誤：ExampleFailure，不是真實來源模組錯誤");
                try{show.invoke(helper,p);}catch(Exception e){throw new RuntimeException(e);}
            });pump();
            SwingUtilities.invokeAndWait(()->{
                ok(frame.isVisible(),"ERROR window stays visible");ok(frame.getTitle().contains("轉換錯誤"),"ERROR not rewritten success");
                ok(Rev247SupportWindow.diagnosticText(frame).contains("ExampleFailure"),"error copy retains cause text");
                dialog.dispose();ok(!Rev247SupportWindow.deferAutoHide(frame,new ActionEvent(new javax.swing.Timer(1,e->{}),0,"test")),"closed child no hide deferral");
                JToggleButton b=find(frame.getContentPane(),"details-toggle",JToggleButton.class);b.doClick();ok(!b.isSelected(),"collapse works");
                try{snapshot(out.resolve("conversion-window.png"),frame);}catch(Exception e){throw new RuntimeException(e);}
            });pump();ok(errors.isEmpty(),"no asynchronous Swing exceptions "+errors);
            System.out.println("PASS actual-helper-ui checks="+checks);
        }finally{SwingUtilities.invokeAndWait(()->{for(Window w:Window.getWindows())w.dispose();});}
    }
}
