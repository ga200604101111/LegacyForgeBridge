package dev.yinghuang.legacyforgebridge.behavior;

import java.util.Arrays;

/**
 * rev242: retains the rev241 ABI and source-driven ascent matching. One completed,
 * stationary, observed jump may additionally identify a delayed descent during
 * the next immediate stationary ascent. This is a BOUNDED HEURISTIC, not a server
 * acknowledgement: the legacy velocity packet has no causal ID or timestamp.
 * No jump height, source boost or gravity is changed by this state machine.
 */
public final class Rev241JumpHistory {
    public static final int MAX_TICKS=40;
    private static final double DRAG=(double)0.98F, EPS=1E-8;
    private final double[] samples=new double[MAX_TICKS+2];
    private int count;
    private long startTick, lastStepTick;
    private boolean active, landingClip, stationary;
    private String reason="unarmed";
    private Completed previous;
    private record Completed(long landingTick, double[] samples) {}
    public record Decision(boolean reconcile, double replacementY, int matchedPhase,
                           int currentPhase, String reason) {}
    public void newJump() { resetCurrent("new-source-jump"); }
    public boolean arm(long tick,double beforeY,double afterY,boolean eligible) {
        resetCurrent("source-not-eligible");
        if (!eligible || !Double.isFinite(beforeY) || !Double.isFinite(afterY) || beforeY<=0
                || afterY<=beforeY+EPS || afterY>=3.9) { previous=null; return false; }
        if (previous!=null && (tick<previous.landingTick || tick-previous.landingTick>2
                || Math.abs(previous.samples[0]-afterY)>EPS)) previous=null;
        startTick=tick; lastStepTick=Long.MIN_VALUE; count=1; samples[0]=afterY;
        active=true; stationary=true; reason="source-history"; return true;
    }
    public void horizontal(boolean still) { if (active && !still) stationary=false; }
    /** Only a verified ground contact completes the archive; arbitrary clips do not. */
    public void land(long tick,double currentY,boolean eligible) {
        if (active && eligible && stationary && count>2 && currentY<0 && tick>=startTick
                && tick-startTick<=MAX_TICKS && Math.abs(currentY-expectedY())<=EPS) {
            previous=new Completed(tick,Arrays.copyOf(samples,count));
            resetCurrent("completed-landing");
        } else clear("unverified-landing");
    }
    public void clear(String why) { resetCurrent(why); previous=null; }
    private void resetCurrent(String why) { active=false; count=0; landingClip=false; reason=why; }
    public boolean active() { return active; }
    public String reason() { return reason; }
    public int phase() { return Math.max(0,count-1); }
    public double expectedY() { return count==0?Double.NaN:samples[count-1]; }
    private boolean context(long tick,double y,boolean eligible) {
        if (!active) return false;
        if (!eligible || tick<startTick || tick-startTick>MAX_TICKS || !Double.isFinite(y)
                || Math.abs(y-expectedY())>EPS) { clear("context-or-trajectory-changed"); return false; }
        return true;
    }
    public void observeVelocity(long tick,double currentY,double requestedY,boolean eligible) {
        if (!context(tick,currentY,eligible)) return;
        if (!Double.isFinite(requestedY)) { clear("non-finite-velocity"); return; }
        if (Math.abs(requestedY-currentY)<=EPS) return;
        if (landingClip || Math.abs(requestedY-gravity(currentY))>EPS || lastStepTick==tick || count==samples.length) {
            clear("non-gravity-velocity-write"); return;
        }
        samples[count++]=requestedY; lastStepTick=tick;
    }
    public void observePosition(double currentYVelocity,double deltaY) {
        if (!active) return;
        if (!Double.isFinite(deltaY) || !Double.isFinite(currentYVelocity)) { clear("invalid-position"); return; }
        if (Math.abs(deltaY)>1E-9 && Math.abs(deltaY-currentYVelocity)>1E-6) {
            if (currentYVelocity<0 && deltaY<0 && deltaY>currentYVelocity) landingClip=true;
            else clear("clipped-step-or-position-change");
        }
    }
    public void tick(long tick,double y,boolean eligible) {
        if (landingClip) clear("unconfirmed-landing-clip"); else context(tick,y,eligible);
        if (previous!=null && (tick<previous.landingTick || tick-previous.landingTick>8)) previous=null;
    }
    public Decision decide(long tick,double currentY,double x,double y,double z,boolean eligible) {
        if (!context(tick,currentY,eligible)) return pass(currentY,reason);
        if (landingClip) { clear("pending-contact"); return pass(currentY,reason); }
        if (!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)
                ||Math.max(Math.abs(x),Math.abs(z))>=3.9||Math.abs(y)>=3.9||y==0||count<2) {
            clear("authoritative-non-echo-packet"); return pass(currentY,reason);
        }
        double scale=Math.ceil(Math.max(Math.abs(x),Math.max(Math.abs(y),Math.abs(z))));
        if (y<0) {
            // Narrow cross-jump case from the report. Never extrapolate future phases.
            if (previous!=null && stationary && x==0 && z==0 && currentY>0 && phase()<=6
                    && tick>=previous.landingTick && tick-previous.landingTick<=8) {
                int match=uniqueMatch(previous.samples,previous.samples.length,y,scale,false);
                if (match>=0) {
                    previous=null; // at most one delayed descent per completed jump
                    return new Decision(true,currentY,match,phase(),"previous-stationary-descent");
                }
            }
            clear("authoritative-negative-packet"); return pass(currentY,reason);
        }
        if (y<=currentY+1E-5) { clear("authoritative-non-echo-packet"); return pass(currentY,reason); }
        int match=uniqueMatch(samples,count-1,y,scale,true);
        if (match<0) { clear("no-unique-source-history-match"); return pass(currentY,reason); }
        return new Decision(true,currentY,match,phase(),"matched-observed-ascent");
    }
    private static int uniqueMatch(double[] a,int size,double y,double scale,boolean positive) {
        int match=-1;
        for(int i=0;i<size;i++) if ((positive?a[i]>0:a[i]<0) && Math.abs(encodedY(a[i],scale)-y)<=1E-10) {
            if(match>=0) return -1; match=i;
        }
        return match;
    }
    private Decision pass(double y,String why) { return new Decision(false,y,-1,phase(),why); }
    public static double gravity(double y) { return (y-0.08D)*DRAG; }
    public static double encodedY(double y,double scale) {
        double legacy=(int)(Math.max(-3.9D,Math.min(3.9D,y))*8000D)/8000D;
        return (Math.round((legacy/scale*.5D+.5D)*32766D)*2D/32766D-1D)*scale;
    }
}
