import java.awt.*;
import java.awt.geom.*;

public abstract class Car {
    protected double x, y;
    protected double angle;
    protected double speed;
    protected double maxSpeed;
    protected double acceleration;
    protected double deceleration;
    protected double turnSpeed;
    protected Color  color;
    protected Color  bodyColor;
    protected String name;

    // ── Lap tracking via waypoint progress ───────────────────────
    // Instead of checkpoint lines (which cars miss), we track which
    // waypoint segment the car is on. A lap completes when the car
    // passes segment 0 having already passed the halfway segment.
    protected int  waypointProgress = 0;  // index of NEXT waypoint to reach
    protected boolean passedHalfway = false;
    protected int  lap              = 0;

    protected long lapStartTime  = 0;
    protected long bestLapTime   = Long.MAX_VALUE;
    protected long lastLapTime   = 0;
    protected boolean finished   = false;
    protected int     finishPosition = 0;

    // Cooldown so finish line can't trigger twice in quick succession
    private long lapCooldownUntil = 0;
    private static final long LAP_COOLDOWN_MS = 4000;

    protected boolean onTrack = true;
    protected static final double OFF_TRACK_FACTOR = 0.42;

    protected static final int CAR_WIDTH  = 14;
    protected static final int CAR_HEIGHT = 24;

    protected Track track;

    public Car(double x, double y, double angle, Color color, String name, Track track) {
        this.x     = x;
        this.y     = y;
        this.angle = angle;
        this.color = color;
        this.bodyColor = color;
        this.name  = name;
        this.track = track;
    }

    public abstract void update(double delta);

    /** Called every frame. Updates waypointProgress and returns true on lap completion. */
    public boolean updateLapProgress(long now) {
        java.util.List<Point> wps = track.getWaypoints();
        int total = wps.size();
        int half  = total / 2;

        // Advance progress while we're close enough to the current target waypoint
        // Use a generous radius so fast cars don't skip waypoints
        for (int guard = 0; guard < 8; guard++) {
            Point wp = wps.get(waypointProgress % total);
            double dist = Math.hypot(wp.x - x, wp.y - y);
            if (dist < 50) {
                waypointProgress++;
                // Mark halfway flag
                int idx = waypointProgress % total;
                if (idx == half) passedHalfway = true;
            } else {
                break;
            }
        }

        // Lap complete: wrapped around to segment 0 AND had passed halfway
        int currentSeg = waypointProgress % total;
        if (passedHalfway && currentSeg < 5 && now > lapCooldownUntil) {
            // Only count if we actually crossed segment 0 area
            Point wp0 = wps.get(0);
            if (Math.hypot(wp0.x - x, wp0.y - y) < 120) {
                lapCooldownUntil = now + LAP_COOLDOWN_MS;
                passedHalfway    = false;
                waypointProgress = 0;
                if (lapStartTime > 0) {
                    lastLapTime = now - lapStartTime;
                    if (lastLapTime < bestLapTime) bestLapTime = lastLapTime;
                }
                lapStartTime = now;
                lap++;
                return true;
            }
        }
        return false;
    }

    /** Returns a comparable progress value: higher = further along the race. */
    public int getRaceProgress() {
        return lap * track.getWaypoints().size() + waypointProgress % track.getWaypoints().size();
    }

    protected void applyMovement(double delta, boolean accelerating, boolean braking, double steerAngle) {
        onTrack = track.isOnTrack(x, y);
        double effectiveMax = onTrack ? maxSpeed : maxSpeed * OFF_TRACK_FACTOR;

        if (accelerating) {
            speed += acceleration * delta * (onTrack ? 1.0 : 0.45);
        } else if (braking) {
            speed -= deceleration * 1.6 * delta;
        } else {
            speed -= deceleration * 0.5 * delta;
        }
        speed = Math.max(0, Math.min(speed, effectiveMax));

        if (speed > 1.0) {
            double speedRatio = Math.min(speed / maxSpeed, 1.0);
            angle += steerAngle * (0.35 + 0.65 * speedRatio);
        }

        x += Math.cos(angle) * speed * delta;
        y += Math.sin(angle) * speed * delta;
        x = Math.max(10, Math.min(GameWindow.WIDTH  - 10, x));
        y = Math.max(10, Math.min(GameWindow.HEIGHT - 10, y));
    }

    public void draw(Graphics2D g2d) {
        AffineTransform old = g2d.getTransform();
        g2d.translate(x, y);
        g2d.rotate(angle + Math.PI / 2);

        g2d.setColor(new Color(0, 0, 0, 60));
        g2d.fillRoundRect(-CAR_WIDTH/2+2, -CAR_HEIGHT/2+2, CAR_WIDTH, CAR_HEIGHT, 4, 4);
        g2d.setColor(bodyColor);
        g2d.fillRoundRect(-CAR_WIDTH/2, -CAR_HEIGHT/2, CAR_WIDTH, CAR_HEIGHT, 4, 4);
        g2d.setColor(new Color(150, 220, 255, 180));
        g2d.fillRect(-CAR_WIDTH/2+2, -CAR_HEIGHT/2+3, CAR_WIDTH-4, 7);
        g2d.setColor(new Color(255, 50, 50));
        g2d.fillRect(-CAR_WIDTH/2,   CAR_HEIGHT/2-4, 4, 3);
        g2d.fillRect( CAR_WIDTH/2-4, CAR_HEIGHT/2-4, 4, 3);
        g2d.setColor(new Color(30, 30, 30));
        g2d.fillRect(-CAR_WIDTH/2-3, -CAR_HEIGHT/2+2, 3, 6);
        g2d.fillRect( CAR_WIDTH/2,   -CAR_HEIGHT/2+2, 3, 6);
        g2d.fillRect(-CAR_WIDTH/2-3,  CAR_HEIGHT/2-8, 3, 6);
        g2d.fillRect( CAR_WIDTH/2,    CAR_HEIGHT/2-8, 3, 6);

        g2d.setTransform(old);
    }

    // ── Getters ──────────────────────────────────────────────────
    public double  getX()            { return x; }
    public double  getY()            { return y; }
    public double  getAngle()        { return angle; }
    public int     getLap()          { return lap; }
    public long    getBestLapTime()  { return bestLapTime; }
    public long    getLastLapTime()  { return lastLapTime; }
    public String  getName()         { return name; }
    public boolean isFinished()      { return finished; }
    public int     getFinishPosition(){ return finishPosition; }
    public void    setFinishPosition(int p){ finishPosition = p; finished = true; }
    public double  getSpeed()        { return speed; }
    public Color   getColor()        { return color; }
    public long    getLapStartTime() { return lapStartTime; }
    public void    setLapStartTime(long t){ lapStartTime = t; }
    public int     getWaypointProgress(){ return waypointProgress; }
}
