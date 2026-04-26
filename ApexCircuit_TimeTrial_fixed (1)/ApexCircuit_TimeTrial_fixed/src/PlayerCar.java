import java.awt.*;

public class PlayerCar extends Car {

    private boolean accel = false;
    private boolean brake = false;
    private boolean left  = false;
    private boolean right = false;

    private static final Color PLAYER_COLOR = new Color(60, 160, 255);

    public PlayerCar(double x, double y, Track track) {
        super(x, y, -Math.PI / 2, PLAYER_COLOR, "YOU", track);
        maxSpeed     = 220;
        acceleration = 180;
        deceleration = 110;
        turnSpeed    = 3.2;
    }

    public void setAccel(boolean v) { accel = v; }
    public void setBrake(boolean v) { brake = v; }
    public void setLeft(boolean v)  { left  = v; }
    public void setRight(boolean v) { right = v; }

    @Override
    public void update(double delta) {
        double steer = 0;
        if (left)  steer = -turnSpeed * delta;
        if (right) steer =  turnSpeed * delta;
        applyMovement(delta, accel, brake, steer);
    }

    @Override
    public void draw(Graphics2D g2d) {
        super.draw(g2d);
        java.awt.geom.AffineTransform old = g2d.getTransform();
        g2d.translate(x, y);
        g2d.rotate(angle + Math.PI / 2);
        g2d.setColor(new Color(255, 255, 255, 160));
        g2d.setStroke(new java.awt.BasicStroke(2f));
        g2d.drawRoundRect(-CAR_WIDTH/2 - 2, -CAR_HEIGHT/2 - 2, CAR_WIDTH + 4, CAR_HEIGHT + 4, 5, 5);
        g2d.setTransform(old);
        g2d.setStroke(new java.awt.BasicStroke(1f));
    }
}
