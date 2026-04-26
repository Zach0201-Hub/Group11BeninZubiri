import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Stadium oval. Window 1000 x 900.
 * Centre: (500, 450). Outer radii: 420h x 320v. Inner radii: 335h x 235v.
 * Cars drive CLOCKWISE: left-straight UP → top-bend → top-straight RIGHT →
 * right-bend → right-straight DOWN → bottom-bend → bottom-straight LEFT →
 * left-bend → finish (left straight, y≈430).
 *
 * Waypoints are dense (every ~18–22 px on straights, ~15° arcs on bends)
 * so the AI never skips one at full speed.  Lap detection uses waypoint
 * proximity instead of crossing a line, so it always registers.
 */
public class Track {
    public static final int TRACK_WIDTH = 85;

    private static final int CX = 500, CY = 450;
    private static final int ORX = 420, ORY = 320;
    private static final int IRX = 335, IRY = 235;
    private static final int MRX = (ORX + IRX) / 2; // 377
    private static final int MRY = (ORY + IRY) / 2; // 277

    private Polygon outerTrack;
    private Polygon innerTrack;
    private Shape   trackShape;

    private final List<Point> waypoints = new ArrayList<>();

    /** Start/finish area: just a reference point for the HUD line drawn on screen. */
    public int[]  startLine;     // [x1,y1,x2,y2] – cosmetic only
    public Point  startPosition; // where cars spawn

    public Track() { buildTrack(); }

    // ── Geometry helpers ─────────────────────────────────────────
    private static Point mid(double t) {
        return new Point((int)(CX + MRX * Math.cos(t)), (int)(CY + MRY * Math.sin(t)));
    }
    private static int[] outer(double t) {
        return new int[]{ (int)(CX + ORX * Math.cos(t)), (int)(CY + ORY * Math.sin(t)) };
    }
    private static int[] inner(double t) {
        return new int[]{ (int)(CX + IRX * Math.cos(t)), (int)(CY + IRY * Math.sin(t)) };
    }

    private void buildTrack() {
        // ── Polygons ─────────────────────────────────────────────
        int N = 64;
        int[] ox = new int[N], oy = new int[N];
        int[] ix = new int[N], iy = new int[N];
        for (int i = 0; i < N; i++) {
            double t = 2 * Math.PI * i / N;
            ox[i] = (int)(CX + ORX * Math.cos(t));
            oy[i] = (int)(CY + ORY * Math.sin(t));
            ix[i] = (int)(CX + IRX * Math.cos(t));
            iy[i] = (int)(CX + IRY * Math.sin(t));   // note: intentional CX base kept for symmetry
            iy[i] = (int)(CY + IRY * Math.sin(t));
        }
        outerTrack = new Polygon(ox, oy, N);
        innerTrack = new Polygon(ix, iy, N);
        Area a = new Area(outerTrack);
        a.subtract(new Area(innerTrack));
        trackShape = a;

        // ── Start / finish ────────────────────────────────────────
        // Left straight, y = 430 (cars travel upward past this point)
        int[] outerPt = outer(Math.PI); // (80, 450)
        int[] innerPt = inner(Math.PI); // (165, 450)
        startLine     = new int[]{ outerPt[0], 430, innerPt[0], 430 };
        startPosition = new Point((outerPt[0] + innerPt[0]) / 2, 470); // spawn just south of line

        // ── Waypoints (clockwise, starting at the finish area) ────
        // Waypoint[0] is just SOUTH of the start/finish line so that
        // passing it after crossing halfway = a completed lap.
        //
        // Order: left-straight UP → top-left bend → top-straight →
        //        top-right bend → right-straight DOWN → bottom-right bend →
        //        bottom-straight → bottom-left bend → back to WP[0]

        // 72 waypoints uniformly spaced (5 deg) on the track midline ellipse,
        // in clockwise driving order. Max consecutive gap ~33px (radius=50 -> never skipped).
        // WP[0]  = just past finish line (lap gate, upper-left going upward)
        // WP[36] = halfway point (right side)
        addWP(125, 416);  // [0]  LAP GATE
        addWP(131, 392);  // [1]
        addWP(139, 368);  // [2]
        addWP(150, 346);  // [3]
        addWP(164, 324);  // [4]
        addWP(180, 303);  // [5]
        addWP(198, 283);  // [6]
        addWP(219, 264);  // [7]
        addWP(242, 247);  // [8]
        addWP(267, 231);  // [9]
        addWP(294, 217);  // [10]
        addWP(323, 205);  // [11]
        addWP(352, 195);  // [12]
        addWP(383, 186);  // [13]
        addWP(415, 180);  // [14]
        addWP(447, 175);  // [15]
        addWP(480, 173);  // [16]
        addWP(513, 173);  // [17]
        addWP(545, 175);  // [18]
        addWP(578, 179);  // [19]
        addWP(610, 185);  // [20]
        addWP(641, 193);  // [21]
        addWP(671, 203);  // [22]
        addWP(699, 215);  // [23]
        addWP(726, 228);  // [24]
        addWP(752, 244);  // [25]
        addWP(775, 261);  // [26]
        addWP(797, 279);  // [27]
        addWP(816, 299);  // [28]
        addWP(832, 319);  // [29]
        addWP(847, 341);  // [30]
        addWP(858, 364);  // [31]
        addWP(867, 387);  // [32]
        addWP(873, 411);  // [33]
        addWP(876, 435);  // [34]
        addWP(876, 459);  // [35]
        addWP(874, 483);  // [36]  HALFWAY
        addWP(868, 507);  // [37]
        addWP(860, 531);  // [38]
        addWP(849, 553);  // [39]
        addWP(835, 575);  // [40]
        addWP(819, 596);  // [41]
        addWP(801, 616);  // [42]
        addWP(780, 635);  // [43]
        addWP(757, 652);  // [44]
        addWP(732, 668);  // [45]
        addWP(705, 682);  // [46]
        addWP(676, 694);  // [47]
        addWP(647, 704);  // [48]
        addWP(616, 713);  // [49]
        addWP(584, 719);  // [50]
        addWP(552, 724);  // [51]
        addWP(519, 726);  // [52]
        addWP(486, 726);  // [53]
        addWP(454, 724);  // [54]
        addWP(421, 720);  // [55]
        addWP(389, 714);  // [56]
        addWP(358, 706);  // [57]
        addWP(328, 696);  // [58]
        addWP(300, 684);  // [59]
        addWP(273, 671);  // [60]
        addWP(247, 655);  // [61]
        addWP(224, 638);  // [62]
        addWP(202, 620);  // [63]
        addWP(183, 600);  // [64]
        addWP(167, 580);  // [65]
        addWP(152, 558);  // [66]
        addWP(141, 535);  // [67]
        addWP(132, 512);  // [68]
        addWP(126, 488);  // [69]
        addWP(123, 464);  // [70]
        addWP(123, 440);  // [71]
    }

    private void addWP(int x, int y) { waypoints.add(new Point(x, y)); }

    public boolean isOnTrack(double x, double y) { return trackShape.contains(x, y); }

    public Shape       getTrackShape()  { return trackShape; }
    public Polygon     getOuterTrack()  { return outerTrack; }
    public Polygon     getInnerTrack()  { return innerTrack; }
    public List<Point> getWaypoints()   { return waypoints; }

    public void draw(Graphics2D g2d) {
        // Track surface
        g2d.setColor(new Color(50, 50, 55));
        g2d.fill(trackShape);

        // Inner grass
        g2d.setColor(new Color(28, 100, 28));
        g2d.fill(innerTrack);

        // Faint racing line (connects waypoints)
        g2d.setColor(new Color(255, 255, 255, 18));
        g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < waypoints.size(); i++) {
            Point a = waypoints.get(i);
            Point b = waypoints.get((i + 1) % waypoints.size());
            g2d.drawLine(a.x, a.y, b.x, b.y);
        }

        // Track borders
        g2d.setColor(new Color(220, 40, 40));
        g2d.setStroke(new BasicStroke(3.5f));
        g2d.draw(outerTrack);
        g2d.draw(innerTrack);

        // Kerbs at corners
        drawKerbs(g2d);

        // Start / finish line (cosmetic)
        int slx = startLine[0], sly = startLine[1];
        int trackW = startLine[2] - startLine[0];
        int sqW = Math.max(1, trackW / 8);
        for (int i = 0; i < 8; i++) {
            g2d.setColor(i % 2 == 0 ? Color.WHITE : Color.BLACK);
            g2d.fillRect(slx + i * sqW, sly - 4, sqW, 8);
        }
        g2d.setColor(new Color(255, 255, 100, 180));
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawLine(slx, sly, startLine[2], sly);
        g2d.setStroke(new BasicStroke(1f));
    }

    private void drawKerbs(Graphics2D g2d) {
        // Apex angles in screen coords: 3PI/2=top, 0=right, PI/2=bottom, PI=left
        double[] apexAngles = {
            3 * Math.PI / 2,   // top (between left and right straights)
            0,                  // right
            Math.PI / 2,        // bottom
            Math.PI             // left
        };
        for (double a : apexAngles) {
            for (int k = 0; k < 4; k++) {
                double ang = a + (k - 1.5) * 0.12;
                int[] o   = outer(ang);
                int[] inn = inner(ang);
                g2d.setColor(k % 2 == 0 ? new Color(210, 30, 30) : Color.WHITE);
                g2d.fillRect(o[0] - 8,   o[1] - 8,   16, 16);
                g2d.setColor(k % 2 == 0 ? Color.WHITE : new Color(210, 30, 30));
                g2d.fillRect(inn[0] - 8, inn[1] - 8, 16, 16);
            }
        }
    }
}
