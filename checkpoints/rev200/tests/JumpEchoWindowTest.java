import dev.yinghuang.legacyforgebridge.behavior.LegacyJumpEchoWindow;
import dev.yinghuang.legacyforgebridge.behavior.LegacyJumpEchoWindow.Velocity;

/** Pure Java model tests: simulated sequences are NOT Minecraft or real-server observations. */
public final class JumpEchoWindowTest {
    private static int checks;
    private static final Velocity BASE = new Velocity(0, .42, 0);
    private static final Velocity BOOST = new Velocity(0, .57, 0);
    private static Velocity v(double y) { return new Velocity(0, y, 0); }
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static LegacyJumpEchoWindow ready() {
        var w = new LegacyJumpEchoWindow(); check(w.begin(100, 64, BASE, BOOST), "arm source boost"); return w;
    }
    private static void pass(LegacyJumpEchoWindow w, long tick, double y, Velocity current,
                             Velocity incoming, boolean ground, boolean unsafe, String label) {
        var d = w.receive(tick, y, current, incoming, ground, unsafe);
        check(!d.candidate(), label); check(d.velocity() == incoming, "pass exact incoming instance " + label);
    }
    public static void main(String[] args) {
        var w = ready();
        w.sample(100, 64.57, v(.4802), false, false);
        var d = w.receive(101, 65.0502, v(.392996), BOOST, false, false);
        check(d.candidate(), "delayed launch echo"); check(d.velocity().y() == .392996, "retain pre-packet Y");
        check(!w.active(), "one shot"); pass(w, 101, 65.0502, v(.392996), BOOST, false, false, "second packet");
        w = ready(); w.sample(100, 64.57, v(.4802), false, false);
        d = w.receive(101, 65.0502, v(.392996), v(.480125), false, false);
        check(d.candidate(), "quantized post-first-tick velocity also has source history");
        w = ready();
        d = w.receive(100, 64.57, v(.4802), BOOST, false, false);
        check(d.candidate(), "same tick but observed upward progress");
        w = ready(); pass(w, 100, 64, BOOST, BOOST, true, false, "no takeoff, no rewind");
        w = ready(); pass(w, 100, 64, v(.4802), BOOST, false, false, "no position progress");
        w = ready(); pass(w, 101, 64.57, v(.4802), v(.48), false, false, "not an upward rewind");
        w = ready(); pass(w, 101, 64.57, v(.4802), v(.65), false, false, "different positive impulse");
        w = ready(); pass(w, 101, 64.57, v(.4802), new Velocity(.4, .57, 0), false, false, "horizontal knockback");
        w = ready(); pass(w, 101, 64.57, v(.4802), BOOST, false, true, "hurt / teleport / liquid guard");
        w = ready(); pass(w, 101, 64.57, v(0), BOOST, false, false, "apex");
        w = ready(); pass(w, 101, 64.57, v(-.1), BOOST, false, false, "descent");
        w = ready(); pass(w, 101, 63.9, v(.48), BOOST, false, false, "position discontinuity");
        w = ready(); pass(w, 101, 64.57, v(.8), BOOST, false, false, "unexplained local acceleration");
        w = ready(); pass(w, 99, 64.57, v(.48), BOOST, false, false, "backward clock");
        w = ready(); pass(w, 109, 64.57, v(.48), BOOST, false, false, "expired");
        w = ready(); w.sample(101, 64.57, v(.48), false, false);
        pass(w, 102, 64.6, v(.4), BOOST, true, false, "landed");
        w = ready(); w.clear(); pass(w, 101, 64.57, v(.48), BOOST, false, false, "context cleared");
        w = ready(); pass(w, 101, 64.57, v(.48), v(.7), false, false, "mismatch consumes");
        pass(w, 102, 65, v(.4), BOOST, false, false, "cannot match after mismatch");
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            w = ready(); pass(w, 101, 64.57, new Velocity(0, bad, 0), BOOST, false, false, "invalid current");
            w = ready(); pass(w, 101, 64.57, v(.48), new Velocity(0, bad, 0), false, false, "invalid packet");
            w = ready(); pass(w, 101, bad, v(.48), BOOST, false, false, "invalid position");
        }
        w = new LegacyJumpEchoWindow();
        check(!w.begin(100, 64, BASE, BASE), "no source boost");
        check(!w.begin(100, 64, BASE, v(.2)), "source lowers Y");
        check(!w.begin(100, 64, BASE, new Velocity(.1, .57, 0)), "source also modifies X");
        check(!w.begin(100, 64, v(0), BOOST), "not a positive ground-jump base");
        check(!w.begin(-1, 64, BASE, BOOST), "invalid launch clock");
        check(!w.begin(100, 64, null, BOOST), "missing launch");
        // No fixed 0.15 literal or three-block height. Exercise independent boosts/directions/delays.
        for (int b = 1; b <= 24; b++) for (int delay = 1; delay <= 8; delay++) {
            double baseY = .2 + b * .013, boostY = baseY + b * .017;
            Velocity before = new Velocity(b * .003, baseY, -b * .007);
            Velocity launch = new Velocity(before.x(), boostY, before.z());
            w = new LegacyJumpEchoWindow(); check(w.begin(30, 12, before, launch), "variable boost");
            double y = 12;
            Velocity current = launch;
            for (int t = 1; t <= delay; t++) {
                y += .1;
                current = new Velocity(before.x() * (1 - t * .02), boostY - t * .01, before.z() * (1 - t * .02));
                w.sample(30 + t, y, current, false, false);
            }
            // Legacy wire rounding, not a game-physics simulation.
            Velocity packet = new Velocity((int)(launch.x()*8000)/8000d, (int)(launch.y()*8000)/8000d, (int)(launch.z()*8000)/8000d);
            d = w.receive(30 + delay, y, current, packet, false, false);
            check(d.candidate(), "observed late echo with delay=" + delay);
            check(d.velocity().y() == current.y(), "no vertical reinjection");
            check(d.velocity().x() == packet.x() && d.velocity().z() == packet.z(), "server horizontal retained");
        }
        w = ready();
        for (int i = 1; i < 300; i++) w.sample(100, 64 + i * .0001, v(.57-i*.0001), false, false);
        check(w.sampleCount() <= 2, "same-tick observation capacity");
        // Document the indistinguishable case rather than claiming all knockback is distinguishable.
        w = ready(); d = w.receive(101, 64.57, v(.48), BOOST, false, false);
        check(d.candidate(), "same-vector unrelated force remains ambiguous; observe default is required");
        System.out.println("PASS core assertions=" + checks);
    }
}
