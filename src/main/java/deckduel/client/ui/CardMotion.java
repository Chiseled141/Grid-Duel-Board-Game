package deckduel.client.ui;

import java.awt.Rectangle;

/**
 * Natural card motion as closed-form functions of time, so it is identical at any frame rate.
 * Modelled on how Balatro-style cards move: the visible card follows its target like a
 * critically damped spring (quick to leave, soft to arrive), leans into its sideways speed with a
 * slight lag, rises off the table while carried (larger, shadow further away), and flips with a
 * lift and shading rather than a flat squash.
 */
final class CardMotion {
    /** Anticipation: the pick-up before a card travels, in animation milliseconds. */
    static final double PICK_UP = 90;
    // Time constants in a trip. A critically damped spring still has 1% of the way to go after 6.6,
    // so the remainder is faded out over the trip (see remaining) and the card arrives exactly.
    private static final double SETTLE = 6.6;
    private static final double LEAN = 0.00012; // Radians of lean per pixel per second of sideways speed.
    private static final double MAX_LEAN = 0.21;  // About 12°: enough to read as momentum, not as a tumble.
    private static final double LAG = 0.04;     // Seconds the lean trails the motion, giving a gentle swing.

    /**
     * Where a card is drawn: centre, lean, uniform lift scale, how far it is turned over about its
     * vertical axis (0 face up, π face down), height off the table (0–1), how much its turning
     * face is shaded, and the fraction of the trip covered.
     */
    record Pose(double x, double y, double angle, double scale, double turn, double height, double shade, double progress) {
        /** Whether the face, rather than the back, is toward the viewer. */
        boolean faceUp() { return Math.cos(turn) >= 0; }
    }

    private CardMotion() { }

    /**
     * A card carried from {@code from} to {@code to}: picked up, launched, eased down and set on
     * the table by {@code duration}. With {@code flip} it turns from its back to its face on the way.
     */
    static Pose travel(Rectangle from, Rectangle to, double t, double duration, boolean flip) {
        double fx = from.getCenterX(), fy = from.getCenterY(), tx = to.getCenterX(), ty = to.getCenterY();
        double direction = Math.signum(tx - fx);
        double lift = easeOut(Math.min(1, t / PICK_UP));
        // Picked up: it rises and draws back a touch against its coming direction.
        double ax = fx - direction * 7 * lift, ay = fy - 14 * lift;
        if (t < PICK_UP) {
            return new Pose(ax, ay, -direction * .035 * lift, 1 + .05 * lift, flip ? Math.PI : 0, lift, 0, 0);
        }
        double span = (duration - PICK_UP) / 1000, seconds = Math.min(span, (t - PICK_UP) / 1000), omega = SETTLE / span;
        double left = remaining(omega, seconds, span);
        double x = tx + (ax - tx) * left, y = ty + (ay - ty) * left, covered = 1 - left;
        double lean = clamp(springVelocity(ax, tx, omega, Math.max(0, seconds - LAG)) * LEAN, MAX_LEAN);
        // Carried high, then set down over the last fifth of the distance.
        double height = covered < .8 ? 1 : 1 - smooth((covered - .8) / .2);
        double scale = 1 + .05 * height;
        if (!flip) return new Pose(x, y, lean, scale, 0, height, 0, covered);
        // Turned over during the first half of the trip; edge-on it is lifted and shaded.
        double turn = Math.PI * (1 - smooth(Math.min(1, covered / .55)));
        double side = Math.sin(turn);
        return new Pose(x, y, lean, scale, turn, Math.max(height, side), .45 * side, covered);
    }

    /**
     * A card drawn or dealt into the hand: an unhurried glide rather than a throw. It eases in and
     * out (a quintic smootherstep, so speed rises and falls without a jolt), follows a shallow arc
     * upward, is lifted most at mid-flight, turns over across the middle of the trip and settles
     * at the hand's own tilt, {@code endAngle}, so nothing jumps when the real hand card appears.
     */
    static Pose glide(Rectangle from, Rectangle to, double t, double duration, boolean flip, double endAngle) {
        double p = Math.max(0, Math.min(1, t / duration)), e = smoother(p);
        double fx = from.getCenterX(), fy = from.getCenterY(), tx = to.getCenterX(), ty = to.getCenterY();
        double arc = Math.min(110, Math.hypot(tx - fx, ty - fy) * .22);
        double cx = (fx + tx) / 2, cy = (fy + ty) / 2 - arc;
        double x = bezier(fx, cx, tx, e), y = bezier(fy, cy, ty, e);
        // Sideways speed in pixels per second, for the same lean a carried card shows.
        double ahead = smoother(Math.min(1, p + .02)), vx = (bezier(fx, cx, tx, ahead) - x) / (.02 * duration / 1000);
        double lean = clamp(vx * LEAN * .7, MAX_LEAN) * (1 - e);
        double height = Math.sin(Math.PI * e), scale = 1 + .06 * height;
        double turn = flip ? Math.PI * (1 - smooth((e - .12) / .6)) : 0;
        return new Pose(x, y, lean + endAngle * e, scale, turn, height, .4 * Math.sin(turn), e);
    }

    /** A damped wobble after an impact: {@code amount} at first, fading within about {@code decay}·4 ms. */
    static double punch(double t, double amount, double decay, double period) {
        return amount * Math.exp(-t / decay) * Math.sin(t / period);
    }

    /**
     * The fraction of the distance still to go after {@code t} of a {@code span}-second trip: a
     * critically damped spring's (1 + ωt)e^(−ωt), less its small final remainder spread evenly
     * over the trip, so it is exactly 1 at the start and exactly 0 at the end.
     */
    private static double remaining(double omega, double t, double span) {
        double end = (1 + omega * span) * Math.exp(-omega * span);
        return ((1 + omega * t) * Math.exp(-omega * t) - end * t / span) / (1 - end);
    }

    private static double springVelocity(double start, double target, double omega, double t) {
        return -omega * omega * (start - target) * t * Math.exp(-omega * t);
    }

    private static double easeOut(double p) { return 1 - Math.pow(1 - p, 3); }
    private static double smoother(double p) { return p * p * p * (p * (p * 6 - 15) + 10); }
    private static double bezier(double a, double control, double b, double t) {
        return (1 - t) * (1 - t) * a + 2 * (1 - t) * t * control + t * t * b;
    }
    private static double smooth(double p) { p = Math.max(0, Math.min(1, p)); return p * p * (3 - 2 * p); }
    private static double clamp(double value, double limit) { return Math.max(-limit, Math.min(limit, value)); }
}
