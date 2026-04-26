import java.awt.*;
import java.util.List;

public class AICar extends Car {

    private int    targetWaypoint = 0;
    private double skillLevel;

    // Noise (makes AI imperfect)
    private long   lastNoiseUpdate = 0;
    private double noiseX = 0, noiseY = 0;

    // Stuck detection
    private boolean stuckRecovery = false;
    private long    stuckTimer    = 0;
    private double  lastX, lastY;
    private long    positionCheckTimer = Long.MIN_VALUE; // initialised on race start

    private int lookAhead;

    private static final Color[] AI_COLORS = {
        new Color(255,  80,  80),   // RAZOR  – red
        new Color( 80, 220,  80),   // VENOM  – green
        new Color(255, 200,   0),   // BLAZE  – yellow
        new Color(200,  80, 255),   // GHOST  – purple
        new Color(255, 140,   0),   // TITAN  – orange
    };
    private static final String[] AI_NAMES = {"RAZOR","VENOM","BLAZE","GHOST","TITAN"};

    public AICar(int index, double x, double y, Track track) {
        super(x, y, -Math.PI / 2,           // angle = pointing UP (correct for left straight)
              AI_COLORS[index % AI_COLORS.length],
              AI_NAMES [index % AI_NAMES.length],
              track);

        skillLevel   = 0.60 + index * 0.08;
        skillLevel   = Math.min(skillLevel, 0.95);

        maxSpeed     = 190 + (int)(skillLevel * 50);  // 190–240
        acceleration = 150 + (int)(skillLevel * 55);
        deceleration = 100;
        turnSpeed    = 2.8 + skillLevel * 0.5;
        lookAhead    = 3 + (int)(skillLevel * 3);     // 3–6 waypoints ahead

        lastX = x;
        lastY = y;

        // Start targeting the waypoint ahead of spawn (not behind)
        targetWaypoint = forwardWaypoint(track.getWaypoints());
    }

    /**
     * Called by GamePanel the moment RACING begins (after countdown).
     * Resets stuck-detection so the countdown delay doesn't look like "stuck".
     */
    public void initRaceStart(long now) {
        positionCheckTimer = now;
        lastX = x;
        lastY = y;
        stuckRecovery = false;
        targetWaypoint = forwardWaypoint(track.getWaypoints());
    }

    @Override
    public void update(double delta) {
        if (finished) return;
        List<java.awt.Point> wps = track.getWaypoints();
        int total = wps.size();
        long now  = System.currentTimeMillis();

        // Safety init (should not happen if initRaceStart is called, but just in case)
        if (positionCheckTimer == Long.MIN_VALUE) {
            positionCheckTimer = now;
            lastX = x; lastY = y;
        }

        // ── Stuck detection ──────────────────────────────────────
        if (now - positionCheckTimer > 1500) {
            double moved = Math.hypot(x - lastX, y - lastY);
            if (moved < 8 && !stuckRecovery) {
                stuckRecovery = true;
                stuckTimer    = now;
            }
            lastX = x;
            lastY = y;
            positionCheckTimer = now;
        }

        if (stuckRecovery) {
            if (now - stuckTimer < 900) {
                // Reverse and steer out
                speed = Math.max(speed - deceleration * 2.5 * delta, -80);
                angle += 0.08 * delta * 60;
                x += Math.cos(angle) * speed * delta;
                y += Math.sin(angle) * speed * delta;
                x = Math.max(10, Math.min(GameWindow.WIDTH  - 10, x));
                y = Math.max(10, Math.min(GameWindow.HEIGHT - 10, y));
                return;
            } else {
                stuckRecovery  = false;
                speed          = 10;
                targetWaypoint = forwardWaypoint(wps);
            }
        }

        // ── Noise ────────────────────────────────────────────────
        if (now - lastNoiseUpdate > 350) {
            double mag = (1.0 - skillLevel) * 18;
            noiseX = (Math.random() - 0.5) * mag;
            noiseY = (Math.random() - 0.5) * mag;
            lastNoiseUpdate = now;
        }

        // ── Advance waypoint when close ───────────────────────────
        // Allow advancing multiple waypoints per frame if moving fast
        for (int i = 0; i < 3; i++) {
            java.awt.Point tgt = wps.get(targetWaypoint % total);
            double dist = Math.hypot(tgt.x + noiseX - x, tgt.y + noiseY - y);
            if (dist < 40) {
                targetWaypoint++;
            } else {
                break;
            }
        }

        // ── Look-ahead smoothing ──────────────────────────────────
        double blendX = 0, blendY = 0, weight = 0;
        for (int i = 0; i <= lookAhead; i++) {
            java.awt.Point wp = wps.get((targetWaypoint + i) % total);
            double w = 1.0 / (i + 1.0);
            blendX += (wp.x + (i == 0 ? noiseX : 0)) * w;
            blendY += (wp.y + (i == 0 ? noiseY : 0)) * w;
            weight += w;
        }
        blendX /= weight;
        blendY /= weight;

        // ── Steer toward blended target ───────────────────────────
        double desiredAngle = Math.atan2(blendY - y, blendX - x);
        double angleDiff    = normalizeAngle(desiredAngle - angle);
        double maxSteer     = turnSpeed * delta;
        double steer        = Math.signum(angleDiff) * Math.min(Math.abs(angleDiff), maxSteer);

        // Accelerate when roughly aligned, brake on sharp turns
        boolean accel = Math.abs(angleDiff) < 0.55;
        boolean brake = Math.abs(angleDiff) > 1.10;

        applyMovement(delta, accel, brake, steer);
    }

    /**
     * Find the nearest waypoint and skip a few ahead to ensure the AI
     * drives FORWARD from spawn, not backward.
     */
    private int forwardWaypoint(List<java.awt.Point> wps) {
        int    best     = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < wps.size(); i++) {
            double d = Math.hypot(wps.get(i).x - x, wps.get(i).y - y);
            if (d < bestDist) { bestDist = d; best = i; }
        }
        // Skip 4 ahead to guarantee forward direction
        return (best + 4) % wps.size();
    }

    private double normalizeAngle(double a) {
        while (a >  Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    @Override
    public void draw(Graphics2D g2d) {
        super.draw(g2d);
        g2d.setColor(new Color(255, 255, 255, 200));
        g2d.setFont(new Font("Monospaced", Font.BOLD, 9));
        g2d.drawString(name, (int)x - 12, (int)y - 20);
    }
}
