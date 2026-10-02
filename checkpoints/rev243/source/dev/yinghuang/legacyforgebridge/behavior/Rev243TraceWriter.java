package dev.yinghuang.legacyforgebridge.behavior;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Dedicated bounded writer. Producers never perform file I/O or wait for free capacity. */
public final class Rev243TraceWriter {
    private final ArrayBlockingQueue<String> queue;
    private final Object admission=new Object();
    private final Path directory;
    private final String prefix, header;
    private final long partLimit, totalLimit;
    private final Consumer<String> notices;
    private final AtomicLong offered=new AtomicLong(), accepted=new AtomicLong(), dropped=new AtomicLong();
    private final AtomicLong truncated=new AtomicLong(), written=new AtomicLong(), discarded=new AtomicLong();
    private final AtomicInteger highWater=new AtomicInteger();
    private final CountDownLatch closed=new CountDownLatch(1);
    private volatile boolean accepting=true, closing;
    private volatile String endReason="disconnect";
    private volatile Path firstPath;
    private BufferedWriter out;
    private long partBytes, totalBytes, lastReport;
    private int part;
    private String runtimeMeta;
    private static final int MAX_CHARS=16384;

    public Rev243TraceWriter(Path directory,String prefix,String header,Consumer<String> notices) {
        this(directory,prefix,header,notices,8192,16L*1024*1024,128L*1024*1024);
    }
    /** Explicit small capacities are used only by local regression tests. */
    public Rev243TraceWriter(Path directory,String prefix,String header,Consumer<String> notices,
                             int capacity,long partLimit,long totalLimit) {
        if(capacity<1||partLimit<256||totalLimit<partLimit) throw new IllegalArgumentException("invalid limits");
        if(!prefix.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("invalid prefix");
        this.directory=directory; this.prefix=prefix; this.header=header;
        this.notices=notices; this.queue=new ArrayBlockingQueue<>(capacity);
        this.partLimit=partLimit; this.totalLimit=totalLimit;
        Thread worker=new Thread(this::run,"LFB-motion-log-writer");
        worker.setDaemon(true); worker.start();
    }
    public boolean accepting() { return accepting; }
    public boolean isClosed() { return closed.getCount()==0; }
    public Path firstPath() { return firstPath; }
    public long accepted() { return accepted.get(); }
    public long dropped() { return dropped.get(); }
    public long written() { return written.get(); }
    public long discarded() { return discarded.get(); }
    public String stats() {
        return "offered="+offered.get()+" accepted="+accepted.get()+" written="+written.get()
                +" queue="+queue.size()+" highWater="+highWater.get()+" dropped="+dropped.get()
                +" truncated="+truncated.get()+" discarded="+discarded.get();
    }
    public void offer(String line) {
        if(!accepting) return;
        offered.incrementAndGet();
        if(line.length()>MAX_CHARS) {
            truncated.incrementAndGet();
            line=line.substring(0,MAX_CHARS)+" DETAIL_TRUNCATED=true";
        }
        // A stop may race with an already admitted producer. Such a line is either
        // drained or counted in discarded at close; no game state is held here.
        synchronized(admission) {
            if(!accepting) { discarded.incrementAndGet(); return; }
            if(queue.offer(line)) {
                accepted.incrementAndGet(); highWater.accumulateAndGet(queue.size(),Math::max);
            } else dropped.incrementAndGet();
        }
    }
    public void requestClose(String why) { synchronized(admission) { accepting=false; endReason=why; closing=true; } }
    public boolean awaitClosed(long millis) throws InterruptedException { return closed.await(millis,TimeUnit.MILLISECONDS); }
    private void notice(String text) { try { notices.accept(text); } catch(RuntimeException ignored) {} }
    private void openPart() throws IOException {
        Files.createDirectories(directory);
        Path path=directory.resolve(prefix+"-part"+String.format(java.util.Locale.ROOT,"%03d",++part)+".log");
        out=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        partBytes=0;
        if(firstPath==null) firstPath=path;
        raw("=== LegacyForgeBridge rev243 observation-only motion trace ===");
        raw(header+" part="+part+" openedUtc="+Instant.now());
        if(runtimeMeta==null)runtimeMeta=Rev243RuntimeMeta.capture();
        raw(runtimeMeta);
        raw("policy=NO_VELOCITY_RECONCILIATION no_chat_or_auth_payloads=true localJumpLabelIsNotServerCause=true");
        if(part>1) raw("CONTINUATION previousPart="+(part-1));
        out.flush();
    }
    private void raw(String line) throws IOException {
        out.write(line);out.newLine();
        long bytes=line.getBytes(StandardCharsets.UTF_8).length+System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;
        partBytes+=bytes; totalBytes+=bytes;
    }
    private void summary(String stage) throws IOException {
        raw(stage+" writeUtc="+Instant.now()+" "+stats()+" incomplete="
                +(dropped.get()>0||truncated.get()>0||discarded.get()>0)+" endReason="+endReason);
    }
    private void run() {
        try {
            openPart(); notice("rev243 diagnostics writing "+firstPath);
            lastReport=System.nanoTime();
            while(!closing||!queue.isEmpty()) {
                String line=queue.poll(100,TimeUnit.MILLISECONDS);
                if(line!=null) {
                    if(totalBytes>=totalLimit) {
                        requestClose("DISK_BUDGET_STOPPED");
                        long n=1; while(queue.poll()!=null)n++; discarded.addAndGet(n);
                        summary("TRACE_LIMIT_REACHED");
                        notice("rev243 motion trace stopped at disk budget; use /lfbtrace start for a new capture");
                        break;
                    }
                    if(partBytes>=partLimit) {
                        summary("PART_END");out.close();openPart();
                    }
                    raw(line);written.incrementAndGet();
                }
                long now=System.nanoTime();
                if(now-lastReport>=500_000_000L) {
                    summary("TRACE_HEALTH");out.flush();lastReport=now;
                }
            }
            // Normal stop drains all records already admitted before closing.
            summary("TRACE_END");out.flush();
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();endReason="WRITER_INTERRUPTED";
            notice("rev243 diagnostic writer interrupted; log incomplete");
        } catch(IOException|RuntimeException e) {
            endReason="WRITER_FAILURE";notice("rev243 diagnostic writer failed: "+e.getClass().getSimpleName());
        } finally {
            synchronized(admission) { accepting=false;closing=true; }
            long n=0;while(queue.poll()!=null)n++;discarded.addAndGet(n);
            if(out!=null)try { out.close(); } catch(IOException ignored) {}
            closed.countDown();
        }
    }
}
