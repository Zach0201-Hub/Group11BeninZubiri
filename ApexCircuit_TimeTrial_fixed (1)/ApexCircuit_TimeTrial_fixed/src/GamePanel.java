import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * ApexCircuit – TIME TRIAL MODE
 *
 * Changes from v7:
 *  - AI cars removed entirely; solo time-attack only.
 *  - Lap count selectable from menu: 3 / 5 / 10 laps.
 *  - Sector timing: 4 sectors. HUD shows current-sector delta vs best lap
 *    (green = faster, red = slower, purple = no reference yet).
 *  - Ghost car: records every frame of the player's best lap and replays it
 *    as a translucent cyan silhouette on subsequent laps.
 *  - Full results screen: per-lap times, best lap highlighted, delta, avg.
 *  - Arcade timer removed; race ends when chosen laps are completed.
 */
public class GamePanel extends JPanel implements ActionListener, KeyListener {

    private static final int FPS         = 60;
    private static final int NUM_SECTORS = 4;

    private int[] sectorStarts;

    private Timer      gameTimer;
    private long       lastTime;
    private Track      track;
    private PlayerCar  player;

    private int  totalLaps    = 3;
    private long raceStartTime;
    private long raceElapsed;

    private final List<Long> lapTimes       = new ArrayList<>();
    private long[][]         lapSectorTimes = null;
    private long[]           bestLapSectors = null;
    private int              bestLapIndex   = -1;

    private int    currentSector      = 0;
    private long   sectorStartTime    = 0;
    private long[] currentSectorSnap  = new long[NUM_SECTORS];

    // Ghost car
    private static class GhostFrame {
        double x, y, angle;
        GhostFrame(double x, double y, double a) { this.x=x; this.y=y; this.angle=a; }
    }
    private final List<GhostFrame> ghostRecording  = new ArrayList<>();
    private       List<GhostFrame> ghostPlayback   = null;
    private int                    ghostPlaybackIdx = 0;
    private boolean                recordingThisLap = false;

    private static final long NOTIF_DURATION = 2800;
    private final List<Notif> notifications  = new ArrayList<>();

    private int  countdownSeconds = 3;
    private long countdownStart;

    private Font bigFont, menuFont, hudFont, smallFont, tinyFont;

    private enum GameState { MAIN_MENU, LAP_SELECT, COUNTDOWN, RACING, PAUSED, RESULTS }
    private GameState state = GameState.MAIN_MENU;

    private int menuSelected    = 0;
    private int lapSelectChoice = 0;
    private int pauseSelected   = 0;
    private int resultsSelected = 0;

    private static final int[] LAP_OPTIONS = { 3, 5, 10 };

    private static class Notif {
        String text; Color color; long expiry;
        Notif(String t, Color c, long e) { text=t; color=c; expiry=e; }
    }

    public GamePanel() {
        setPreferredSize(new Dimension(GameWindow.WIDTH, GameWindow.HEIGHT));
        setBackground(new Color(34, 100, 34));
        setFocusable(true);
        addKeyListener(this);
        bigFont   = new Font("Courier New", Font.BOLD, 72);
        menuFont  = new Font("Courier New", Font.BOLD, 22);
        hudFont   = new Font("Courier New", Font.BOLD, 13);
        smallFont = new Font("Courier New", Font.BOLD, 11);
        tinyFont  = new Font("Courier New", Font.BOLD, 10);
    }

    private void initGame() {
        track = new Track();
        List<Point> wps = track.getWaypoints();
        int total = wps.size();
        sectorStarts = new int[]{ 0, total/4, total/2, 3*total/4 };

        player = new PlayerCar(track.startPosition.x, track.startPosition.y, track);

        lapTimes.clear();
        lapSectorTimes  = new long[totalLaps][NUM_SECTORS];
        bestLapSectors  = null;
        bestLapIndex    = -1;
        ghostRecording.clear();
        ghostPlayback   = null;
        ghostPlaybackIdx = 0;
        recordingThisLap = true;
        notifications.clear();

        currentSector = 0;
        raceElapsed   = 0;
        Arrays.fill(currentSectorSnap, 0L);

        state          = GameState.COUNTDOWN;
        countdownStart = System.currentTimeMillis();
    }

    public void startGame() {
        lastTime  = System.currentTimeMillis();
        gameTimer = new Timer(1000 / FPS, this);
        gameTimer.start();
        requestFocusInWindow();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        long   now   = System.currentTimeMillis();
        double delta = Math.min((now - lastTime) / 1000.0, 0.05);
        lastTime = now;
        update(delta, now);
        repaint();
    }

    private void update(double delta, long now) {
        switch (state) {
            case MAIN_MENU: case LAP_SELECT: case PAUSED: case RESULTS: return;
            case COUNTDOWN:
                countdownSeconds = 3 - (int)((now - countdownStart) / 1000);
                if (countdownSeconds <= 0) {
                    state           = GameState.RACING;
                    raceStartTime   = now;
                    sectorStartTime = now;
                    player.setLapStartTime(now);
                }
                return;
            case RACING: break;
        }

        raceElapsed = now - raceStartTime;
        player.update(delta);

        if (recordingThisLap) {
            ghostRecording.add(new GhostFrame(player.getX(), player.getY(), player.getAngle()));
        }
        if (ghostPlayback != null && ghostPlaybackIdx < ghostPlayback.size() - 1) {
            ghostPlaybackIdx++;
        }

        updateSectors(now);

        boolean lapDone = player.updateLapProgress(now);
        if (lapDone) onLapComplete(now);
    }

    private void updateSectors(long now) {
        List<Point> wps = track.getWaypoints();
        int wp = player.getWaypointProgress() % wps.size();
        int nextSector = (currentSector + 1) % NUM_SECTORS;
        if (nextSector == 0) return; // sector 3→0 handled by lap completion

        int boundary = sectorStarts[nextSector];
        boolean crossed = (wp >= boundary && wp < boundary + 8
                        && currentSector == nextSector - 1);
        if (!crossed) return;

        long sectorMs = now - sectorStartTime;
        currentSectorSnap[currentSector] = sectorMs;

        if (bestLapSectors != null && bestLapSectors[currentSector] > 0) {
            long diff = sectorMs - bestLapSectors[currentSector];
            String sign = diff < 0 ? "-" : "+";
            Color  c    = diff < 0 ? new Color(0, 220, 110) : new Color(255, 80, 80);
            pushNotif("S"+(currentSector+1)+"  "+sign+formatShort(Math.abs(diff)), c, now);
        } else {
            pushNotif("S"+(currentSector+1)+"  "+formatShort(sectorMs), new Color(180, 100, 255), now);
        }
        currentSector   = nextSector;
        sectorStartTime = now;
    }

    private void onLapComplete(long now) {
        int lapIndex = player.getLap() - 1;
        if (lapIndex < 0 || lapIndex >= totalLaps) return;

        long lapMs = player.getLastLapTime();
        if (lapMs <= 0) return;
        lapTimes.add(lapMs);

        // Finalize last sector
        long finalSec = now - sectorStartTime;
        currentSectorSnap[currentSector] = finalSec;
        long[] thisSectors = Arrays.copyOf(currentSectorSnap, NUM_SECTORS);
        lapSectorTimes[lapIndex] = thisSectors;

        boolean isBest = (bestLapIndex == -1 || lapMs < lapTimes.get(bestLapIndex));
        if (isBest) {
            bestLapIndex   = lapTimes.size() - 1;
            bestLapSectors = Arrays.copyOf(thisSectors, NUM_SECTORS);
            ghostPlayback  = new ArrayList<>(ghostRecording);
            pushNotif("BEST LAP!  " + formatTime(lapMs), new Color(180, 100, 255), now);
        } else {
            long diff = lapMs - lapTimes.get(bestLapIndex);
            pushNotif("LAP "+player.getLap()+"  +"+formatShort(diff), new Color(255, 100, 100), now);
        }

        ghostRecording.clear();
        recordingThisLap = true;
        ghostPlaybackIdx = 0;
        currentSector    = 0;
        sectorStartTime  = now;
        Arrays.fill(currentSectorSnap, 0L);

        if (player.getLap() >= totalLaps) {
            javax.swing.Timer t = new javax.swing.Timer(1800, ev -> {
                state = GameState.RESULTS;
                resultsSelected = 0;
            });
            t.setRepeats(false);
            t.start();
        }
    }

    private void pushNotif(String text, Color color, long startAt) {
        notifications.add(new Notif(text, color, startAt + NOTIF_DURATION));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (state == GameState.MAIN_MENU)  { drawMainMenu(g2);  return; }
        if (state == GameState.LAP_SELECT) { drawLapSelect(g2); return; }

        g2.setColor(new Color(28, 90, 28));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(new Color(24, 78, 24));
        for (int i = 0; i < getWidth(); i += 20)
            for (int j = 0; j < getHeight(); j += 20)
                g2.fillOval(i, j, 3, 3);

        track.draw(g2);
        drawGhost(g2);
        player.draw(g2);

        if (state == GameState.COUNTDOWN) { drawCountdown(g2); return; }
        drawHUD(g2);
        if (state == GameState.PAUSED)  drawPauseMenu(g2);
        if (state == GameState.RESULTS) drawResults(g2);
    }

    private void drawGhost(Graphics2D g2d) {
        if (ghostPlayback == null) return;
        int idx = Math.min(ghostPlaybackIdx, ghostPlayback.size() - 1);
        GhostFrame gf = ghostPlayback.get(idx);

        AffineTransform old = g2d.getTransform();
        g2d.translate(gf.x, gf.y);
        g2d.rotate(gf.angle + Math.PI / 2);

        Composite comp = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.38f));
        g2d.setColor(new Color(0, 220, 255));
        g2d.fillRoundRect(-7, -12, 14, 24, 4, 4);
        g2d.setColor(new Color(255, 255, 255, 200));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(-7, -12, 14, 24, 4, 4);
        g2d.setStroke(new BasicStroke(1f));
        g2d.setComposite(comp);
        g2d.setTransform(old);
    }

    private void drawMainMenu(Graphics2D g) {
        g.setColor(new Color(8, 8, 20)); g.fillRect(0,0,getWidth(),getHeight());
        g.setColor(new Color(0,80,160,40));
        for (int x=0;x<getWidth();x+=40) g.drawLine(x,0,x,getHeight());
        for (int y=0;y<getHeight();y+=40) g.drawLine(0,y,getWidth(),y);

        int cx = getWidth()/2;
        g.setFont(bigFont); FontMetrics fm = g.getFontMetrics();
        String t1="APEX", t2="CIRCUIT";
        for(int i=12;i>0;i--){g.setColor(new Color(0,140,255,7));g.drawString(t1,cx-fm.stringWidth(t1)/2-i/2,200+i/2);}
        g.setColor(new Color(0,200,255)); g.drawString(t1,cx-fm.stringWidth(t1)/2,200);
        for(int i=12;i>0;i--){g.setColor(new Color(255,100,0,6));g.drawString(t2,cx-fm.stringWidth(t2)/2-i/2,278+i/2);}
        g.setColor(new Color(255,160,0)); g.drawString(t2,cx-fm.stringWidth(t2)/2,278);

        g.setFont(new Font("Courier New",Font.BOLD,15)); g.setColor(new Color(180,100,255));
        String sub="TIME TRIAL  ·  BEAT YOUR BEST LAP"; fm=g.getFontMetrics();
        g.drawString(sub,cx-fm.stringWidth(sub)/2,312);
        g.setColor(new Color(0,200,255,80)); g.fillRect(cx-170,328,340,2);

        String[] items={" START TIME TRIAL "," EXIT GAME "};
        Color[] active={new Color(0,220,255),new Color(255,80,80)};
        Color[] dim={new Color(80,140,180),new Color(150,70,70)};
        for(int i=0;i<items.length;i++){
            boolean sel=menuSelected==i; int by=378+i*76;
            g.setColor(sel?new Color(0,60,100,200):new Color(20,20,35,200));
            g.fillRoundRect(cx-160,by-30,320,48,10,10);
            g.setColor(sel?active[i]:dim[i]);
            g.setStroke(new BasicStroke(sel?2.5f:1.2f));
            g.drawRoundRect(cx-160,by-30,320,48,10,10); g.setStroke(new BasicStroke(1f));
            g.setFont(menuFont); fm=g.getFontMetrics(); g.setColor(sel?active[i]:dim[i]);
            g.drawString(items[i],cx-fm.stringWidth(items[i])/2,by+6);
        }

        // Feature icons
        String[][] feats={
            {"◈","Ghost Car"},{"◈","Sector Deltas"},{"◈","Lap Breakdown"}
        };
        int fx=cx-240;
        for(String[] f:feats){
            g.setFont(new Font("Courier New",Font.BOLD,12)); g.setColor(new Color(180,100,255));
            g.drawString(f[0],fx,getHeight()-44);
            g.setColor(new Color(180,180,200)); g.drawString(" "+f[1],fx+14,getHeight()-44);
            fx+=160;
        }
        g.setFont(smallFont); g.setColor(new Color(120,120,150));
        String hint="↑↓ SELECT   ENTER CONFIRM"; fm=g.getFontMetrics();
        g.drawString(hint,cx-fm.stringWidth(hint)/2,getHeight()-18);
    }

    private void drawLapSelect(Graphics2D g) {
        g.setColor(new Color(8, 8, 20)); g.fillRect(0,0,getWidth(),getHeight());
        g.setColor(new Color(0,80,160,40));
        for(int x=0;x<getWidth();x+=40) g.drawLine(x,0,x,getHeight());
        for(int y=0;y<getHeight();y+=40) g.drawLine(0,y,getWidth(),y);

        int cx=getWidth()/2;
        g.setFont(new Font("Courier New",Font.BOLD,36)); g.setColor(new Color(255,200,0));
        String t="SELECT LAPS"; FontMetrics fm=g.getFontMetrics();
        g.drawString(t,cx-fm.stringWidth(t)/2,220);
        g.setFont(new Font("Courier New",Font.BOLD,14)); g.setColor(new Color(180,180,200));
        String sub="More laps = longer session + more ghost data"; fm=g.getFontMetrics();
        g.drawString(sub,cx-fm.stringWidth(sub)/2,256);

        for(int i=0;i<LAP_OPTIONS.length;i++){
            boolean sel=lapSelectChoice==i; int by=310+i*92;
            g.setColor(sel?new Color(0,60,100,220):new Color(20,20,35,200));
            g.fillRoundRect(cx-165,by-34,330,62,10,10);
            Color c=sel?new Color(0,220,255):new Color(60,120,160);
            g.setColor(c); g.setStroke(new BasicStroke(sel?2.5f:1.2f));
            g.drawRoundRect(cx-165,by-34,330,62,10,10); g.setStroke(new BasicStroke(1f));
            g.setFont(new Font("Courier New",Font.BOLD,28)); fm=g.getFontMetrics();
            g.setColor(sel?Color.WHITE:new Color(140,160,180));
            String lbl=LAP_OPTIONS[i]+" LAPS";
            g.drawString(lbl,cx-fm.stringWidth(lbl)/2,by+6);
        }
        g.setFont(smallFont); g.setColor(new Color(120,120,150));
        String hint="↑↓ SELECT   ENTER START   ESC BACK"; FontMetrics fm2=g.getFontMetrics();
        g.drawString(hint,cx-fm2.stringWidth(hint)/2,getHeight()-18);
    }

    private void drawHUD(Graphics2D g) {
        long now = System.currentTimeMillis();

        // Top centre: lap counter
        g.setColor(new Color(0,0,0,175)); g.fillRoundRect(getWidth()/2-125,8,250,66,10,10);
        g.setColor(new Color(0,200,255)); g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(getWidth()/2-125,8,250,66,10,10); g.setStroke(new BasicStroke(1f));
        g.setFont(tinyFont); g.setColor(new Color(180,180,180));
        String lapLabel="LAP"; FontMetrics fm=g.getFontMetrics();
        g.drawString(lapLabel,getWidth()/2-fm.stringWidth(lapLabel)/2,26);
        int displayLap=Math.min(player.getLap()+1,totalLaps);
        g.setFont(new Font("Courier New",Font.BOLD,32)); fm=g.getFontMetrics();
        g.setColor(Color.WHITE);
        String lapStr=displayLap+"/"+totalLaps;
        g.drawString(lapStr,getWidth()/2-fm.stringWidth(lapStr)/2,62);

        // Left panel
        g.setColor(new Color(0,0,0,175)); g.fillRoundRect(10,10,228,200,10,10);
        long curLapMs=(player.getLapStartTime()>0)?now-player.getLapStartTime():0;
        g.setFont(hudFont); g.setColor(new Color(255,220,60)); g.drawString("CURRENT LAP",22,34);
        g.setFont(new Font("Courier New",Font.BOLD,19)); g.setColor(Color.WHITE);
        g.drawString(formatTime(curLapMs),22,54);

        g.setFont(hudFont); g.setColor(new Color(180,100,255)); g.drawString("BEST LAP",22,78);
        g.setFont(new Font("Courier New",Font.BOLD,19));
        if (bestLapIndex>=0){ g.setColor(new Color(200,140,255)); g.drawString(formatTime(lapTimes.get(bestLapIndex)),22,98); }
        else { g.setColor(new Color(130,130,150)); g.drawString("--:--.---",22,98); }

        g.setFont(hudFont); g.setColor(new Color(180,180,180)); g.drawString("RACE TIME",22,122);
        g.setFont(hudFont); g.setColor(Color.WHITE); g.drawString(formatTime(raceElapsed),22,138);

        // Sector bar
        drawSectorBar(g, 22, 156, 196, 16, now);

        // Speed top right
        g.setColor(new Color(0,0,0,170)); g.fillRoundRect(getWidth()-190,10,180,66,10,10);
        g.setFont(hudFont); g.setColor(new Color(180,180,180)); g.drawString("SPEED",getWidth()-178,30);
        int kmh=(int)(player.getSpeed()*1.2);
        g.setFont(new Font("Courier New",Font.BOLD,30));
        g.setColor(kmh>200?new Color(255,110,50):Color.WHITE);
        g.drawString(kmh+" km/h",getWidth()-178,62);

        // Lap list right side
        drawLapList(g, now);

        // Ghost label
        if (ghostPlayback!=null){
            g.setFont(tinyFont); g.setColor(new Color(0,200,255,160));
            g.drawString("◈ GHOST",getWidth()-182,90);
        }

        drawNotifications(g, now);

        g.setFont(new Font("Courier New",Font.PLAIN,11)); g.setColor(new Color(255,255,255,70));
        String ctrl="↑ACCEL  ↓BRAKE  ←→STEER  ESC PAUSE"; fm=g.getFontMetrics();
        g.drawString(ctrl,getWidth()/2-fm.stringWidth(ctrl)/2,getHeight()-8);
    }

    private void drawSectorBar(Graphics2D g, int x, int y, int w, int h, long now) {
        g.setFont(tinyFont); g.setColor(new Color(180,180,180)); g.drawString("SECTORS",x,y-3);
        int sw=(w-3)/NUM_SECTORS;
        for(int s=0;s<NUM_SECTORS;s++){
            int sx=x+s*(sw+1);
            boolean done=s<currentSector, active=s==currentSector;
            Color col;
            if(done){
                if(bestLapSectors!=null&&currentSectorSnap[s]>0)
                    col=currentSectorSnap[s]<bestLapSectors[s]?new Color(0,200,100):new Color(220,60,60);
                else col=new Color(180,100,255);
            } else if(active){
                int pulse=(int)(100+100*Math.abs(Math.sin(now/200.0)));
                col=new Color(pulse,180,50);
            } else col=new Color(50,50,60);
            g.setColor(col); g.fillRoundRect(sx,y,sw,h,3,3);
            g.setColor(new Color(0,0,0,80)); g.drawRoundRect(sx,y,sw,h,3,3);
            g.setColor(Color.WHITE); g.setFont(tinyFont);
            g.drawString("S"+(s+1),sx+sw/2-6,y+h-2);
        }
    }

    private void drawLapList(Graphics2D g, long now) {
        int bx=getWidth()-190, by=104;
        int bh=20+(lapTimes.size()+1)*18;
        g.setColor(new Color(0,0,0,175)); g.fillRoundRect(bx,by,180,bh,8,8);
        g.setFont(tinyFont); g.setColor(new Color(0,200,255)); g.drawString("LAP TIMES",bx+10,by+14);
        for(int i=0;i<lapTimes.size();i++){
            boolean best=(i==bestLapIndex);
            g.setColor(best?new Color(200,140,255):new Color(200,200,200));
            g.drawString((i+1)+". "+formatTime(lapTimes.get(i))+(best?" ◆":""),bx+8,by+14+(i+1)*18);
        }
    }

    private void drawNotifications(Graphics2D g, long now) {
        notifications.removeIf(n -> now > n.expiry);
        if(notifications.isEmpty()) return;
        int cx=getWidth()/2, baseY=getHeight()-75;
        for(int i=0;i<notifications.size();i++){
            Notif n=notifications.get(notifications.size()-1-i);
            if(now<n.expiry-NOTIF_DURATION) continue;
            long remaining=n.expiry-now;
            float alpha=remaining<400?(float)remaining/400f:1f;
            g.setFont(new Font("Courier New",Font.BOLD,20));
            FontMetrics fm=g.getFontMetrics();
            int w=fm.stringWidth(n.text)+32, ny=baseY-i*44;
            g.setColor(new Color(0,0,0,(int)(170*alpha)));
            g.fillRoundRect(cx-w/2,ny-24,w,34,10,10);
            Color bc=new Color(n.color.getRed(),n.color.getGreen(),n.color.getBlue(),(int)(200*alpha));
            g.setColor(bc); g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(cx-w/2,ny-24,w,34,10,10); g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(n.color.getRed(),n.color.getGreen(),n.color.getBlue(),(int)(255*alpha)));
            g.drawString(n.text,cx-fm.stringWidth(n.text)/2,ny);
        }
    }

    private void drawCountdown(Graphics2D g) {
        g.setColor(new Color(0,0,0,115)); g.fillRect(0,0,getWidth(),getHeight());
        String txt=countdownSeconds>0?String.valueOf(countdownSeconds):"GO!";
        Color tc=countdownSeconds>0?new Color(255,200,0):new Color(0,255,100);
        g.setFont(bigFont); FontMetrics fm=g.getFontMetrics(); int tw=fm.stringWidth(txt);
        for(int i=12;i>0;i--){g.setColor(new Color(tc.getRed(),tc.getGreen(),tc.getBlue(),8));g.drawString(txt,getWidth()/2-tw/2-i/2,getHeight()/2+20+i/2);}
        g.setColor(tc); g.drawString(txt,getWidth()/2-tw/2,getHeight()/2+20);
        g.setFont(new Font("Courier New",Font.BOLD,20)); g.setColor(Color.WHITE);
        String sub="APEX CIRCUIT  ·  TIME TRIAL  ·  "+totalLaps+" LAPS"; fm=g.getFontMetrics();
        g.drawString(sub,getWidth()/2-fm.stringWidth(sub)/2,getHeight()/2-65);
        g.setFont(new Font("Courier New",Font.BOLD,14)); g.setColor(new Color(180,100,255));
        String tip="Ghost replays your best lap  ◈  Beat each sector!"; fm=g.getFontMetrics();
        g.drawString(tip,getWidth()/2-fm.stringWidth(tip)/2,getHeight()/2+82);
    }

    private void drawPauseMenu(Graphics2D g) {
        g.setColor(new Color(0,0,0,165)); g.fillRect(0,0,getWidth(),getHeight());
        int cx=getWidth()/2, cy=getHeight()/2;
        g.setColor(new Color(10,10,25,230)); g.fillRoundRect(cx-185,cy-165,370,310,16,16);
        g.setColor(new Color(0,200,255,120)); g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(cx-185,cy-165,370,310,16,16); g.setStroke(new BasicStroke(1f));
        g.setFont(new Font("Courier New",Font.BOLD,30)); g.setColor(new Color(255,200,0));
        String t="PAUSED"; FontMetrics fm=g.getFontMetrics();
        g.drawString(t,cx-fm.stringWidth(t)/2,cy-110);
        String[] items={"  RESUME  ","  RESTART  ","  MAIN MENU  "};
        Color[] colors={new Color(0,220,100),new Color(255,180,0),new Color(255,80,80)};
        for(int i=0;i<items.length;i++){
            boolean sel=pauseSelected==i; int by=cy-50+i*64;
            g.setColor(sel?new Color(0,40,60,200):new Color(20,20,35,180));
            g.fillRoundRect(cx-150,by-22,300,44,8,8);
            Color c=sel?colors[i]:new Color(colors[i].getRed()/2,colors[i].getGreen()/2,colors[i].getBlue()/2);
            g.setColor(c); g.setStroke(new BasicStroke(sel?2f:1f));
            g.drawRoundRect(cx-150,by-22,300,44,8,8); g.setStroke(new BasicStroke(1f));
            g.setFont(menuFont); fm=g.getFontMetrics(); g.setColor(c);
            g.drawString(items[i],cx-fm.stringWidth(items[i])/2,by+6);
        }
        g.setFont(smallFont); g.setColor(new Color(120,120,150));
        String hint="↑↓ SELECT   ENTER CONFIRM   ESC RESUME"; fm=g.getFontMetrics();
        g.drawString(hint,cx-fm.stringWidth(hint)/2,cy+148);
    }

    private void drawResults(Graphics2D g) {
        g.setColor(new Color(0,0,0,215)); g.fillRect(0,0,getWidth(),getHeight());

        int cx=getWidth()/2;
        int pw=580;
        int headerH=60, rowH=24, statsH=90, btnH=3*58+20, padV=30;
        int tableRows=lapTimes.size();
        int ph=headerH+16+tableRows*rowH+statsH+btnH+padV;
        ph=Math.min(ph,getHeight()-40);
        int px=cx-pw/2, py=(getHeight()-ph)/2;

        g.setColor(new Color(10,10,28,240)); g.fillRoundRect(px,py,pw,ph,16,16);
        g.setColor(new Color(180,100,255,140)); g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(px,py,pw,ph,16,16); g.setStroke(new BasicStroke(1f));

        // Title
        g.setFont(new Font("Courier New",Font.BOLD,32)); g.setColor(new Color(180,100,255));
        String title="TIME TRIAL COMPLETE"; FontMetrics fm=g.getFontMetrics();
        g.drawString(title,cx-fm.stringWidth(title)/2,py+42);
        g.setColor(new Color(180,100,255,70)); g.fillRect(px+20,py+52,pw-40,2);

        int ty=py+76;

        // Column headers
        g.setFont(hudFont); g.setColor(new Color(160,160,160));
        g.drawString("LAP",px+28,ty);
        g.drawString("TIME",px+105,ty);
        g.drawString("DELTA",px+260,ty);
        g.drawString("SECTORS",px+370,ty);
        g.setColor(new Color(80,80,100)); g.fillRect(px+20,ty+4,pw-40,1);
        ty+=18;

        long best=bestLapIndex>=0?lapTimes.get(bestLapIndex):Long.MAX_VALUE;
        for(int i=0;i<lapTimes.size();i++){
            long lt=lapTimes.get(i);
            boolean isBest=(i==bestLapIndex);
            if(isBest){ g.setColor(new Color(80,0,160,80)); g.fillRoundRect(px+16,ty-14,pw-32,20,4,4); }
            g.setFont(new Font("Courier New",Font.BOLD,13));
            g.setColor(isBest?new Color(200,140,255):new Color(200,200,200));
            g.drawString((i+1)+(isBest?" ◆":""),px+28,ty);
            g.drawString(formatTime(lt),px+105,ty);
            if(isBest){ g.setColor(new Color(100,210,100)); g.drawString("BEST",px+260,ty); }
            else if(best!=Long.MAX_VALUE){
                long diff=lt-best;
                g.setColor(diff<0?new Color(0,200,100):new Color(220,80,80));
                g.drawString("+"+formatShort(diff),px+260,ty);
            }
            if(lapSectorTimes!=null&&i<lapSectorTimes.length&&lapSectorTimes[i]!=null){
                for(int s=0;s<NUM_SECTORS;s++){
                    boolean faster=bestLapSectors!=null&&lapSectorTimes[i][s]>0&&lapSectorTimes[i][s]<bestLapSectors[s];
                    g.setColor(isBest?new Color(180,100,255):(faster?new Color(0,200,100):new Color(180,60,60)));
                    g.fillRoundRect(px+370+s*32,ty-12,26,16,4,4);
                    g.setFont(tinyFont); g.setColor(Color.WHITE);
                    g.drawString("S"+(s+1),px+375+s*32,ty);
                    g.setFont(new Font("Courier New",Font.BOLD,13));
                }
            }
            ty+=rowH;
        }

        g.setColor(new Color(80,80,100)); g.fillRect(px+20,ty+2,pw-40,1); ty+=16;
        g.setFont(hudFont);
        if(!lapTimes.isEmpty()){
            long avg=lapTimes.stream().mapToLong(v->v).sum()/lapTimes.size();
            g.setColor(new Color(180,180,200)); g.drawString("AVERAGE LAP:   "+formatTime(avg),px+28,ty); ty+=22;
        }
        g.setColor(new Color(200,140,255));
        g.drawString("BEST LAP:      "+(bestLapIndex>=0?formatTime(lapTimes.get(bestLapIndex)):"--:--.---"),px+28,ty); ty+=22;
        g.setColor(new Color(180,180,200));
        g.drawString("TOTAL TIME:    "+formatTime(raceElapsed),px+28,ty); ty+=22;
        g.drawString("LAPS:          "+lapTimes.size()+"/"+totalLaps,px+28,ty); ty+=28;

        String[] btns={"  RACE AGAIN  ","  CHANGE LAPS  ","  MAIN MENU  "};
        Color[] bcols={new Color(0,200,100),new Color(255,180,0),new Color(255,80,80)};
        for(int i=0;i<btns.length;i++){
            boolean sel=resultsSelected==i; int bby=ty+i*56;
            g.setColor(sel?new Color(0,40,60,200):new Color(20,20,35,180));
            g.fillRoundRect(cx-150,bby-22,300,44,8,8);
            Color c=sel?bcols[i]:new Color(bcols[i].getRed()/2,bcols[i].getGreen()/2,bcols[i].getBlue()/2);
            g.setColor(c); g.setStroke(new BasicStroke(sel?2f:1f));
            g.drawRoundRect(cx-150,bby-22,300,44,8,8); g.setStroke(new BasicStroke(1f));
            g.setFont(menuFont); fm=g.getFontMetrics(); g.setColor(c);
            g.drawString(btns[i],cx-fm.stringWidth(btns[i])/2,bby+6);
        }
        g.setFont(smallFont); g.setColor(new Color(120,120,150));
        String hint="↑↓ SELECT   ENTER CONFIRM"; fm=g.getFontMetrics();
        g.drawString(hint,cx-fm.stringWidth(hint)/2,py+ph-12);
    }

    // helpers
    private String formatTime(long ms) {
        if(ms==Long.MAX_VALUE||ms<0) return "--:--.---";
        return String.format("%02d:%02d.%03d",ms/60000,(ms%60000)/1000,ms%1000);
    }
    private String formatShort(long ms) {
        return String.format("%d.%03d",ms/1000,ms%1000);
    }

    @Override public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        int k=e.getKeyCode();
        switch(state){
            case MAIN_MENU:
                if(k==KeyEvent.VK_UP||k==KeyEvent.VK_W)   menuSelected=Math.max(0,menuSelected-1);
                if(k==KeyEvent.VK_DOWN||k==KeyEvent.VK_S) menuSelected=Math.min(1,menuSelected+1);
                if(k==KeyEvent.VK_ENTER||k==KeyEvent.VK_SPACE){ if(menuSelected==0) state=GameState.LAP_SELECT; else System.exit(0); }
                break;
            case LAP_SELECT:
                if(k==KeyEvent.VK_UP||k==KeyEvent.VK_W)   lapSelectChoice=Math.max(0,lapSelectChoice-1);
                if(k==KeyEvent.VK_DOWN||k==KeyEvent.VK_S) lapSelectChoice=Math.min(LAP_OPTIONS.length-1,lapSelectChoice+1);
                if(k==KeyEvent.VK_ESCAPE) state=GameState.MAIN_MENU;
                if(k==KeyEvent.VK_ENTER||k==KeyEvent.VK_SPACE){ totalLaps=LAP_OPTIONS[lapSelectChoice]; initGame(); }
                break;
            case RACING:
                if(k==KeyEvent.VK_UP||k==KeyEvent.VK_W)    player.setAccel(true);
                if(k==KeyEvent.VK_DOWN||k==KeyEvent.VK_S)  player.setBrake(true);
                if(k==KeyEvent.VK_LEFT||k==KeyEvent.VK_A)  player.setLeft(true);
                if(k==KeyEvent.VK_RIGHT||k==KeyEvent.VK_D) player.setRight(true);
                if(k==KeyEvent.VK_ESCAPE){ state=GameState.PAUSED; pauseSelected=0; }
                break;
            case PAUSED:
                if(k==KeyEvent.VK_UP||k==KeyEvent.VK_W)   pauseSelected=Math.max(0,pauseSelected-1);
                if(k==KeyEvent.VK_DOWN||k==KeyEvent.VK_S) pauseSelected=Math.min(2,pauseSelected+1);
                if(k==KeyEvent.VK_ESCAPE) state=GameState.RACING;
                if(k==KeyEvent.VK_ENTER||k==KeyEvent.VK_SPACE){
                    if(pauseSelected==0) state=GameState.RACING;
                    else if(pauseSelected==1) initGame();
                    else { state=GameState.MAIN_MENU; menuSelected=0; }
                }
                break;
            case RESULTS:
                if(k==KeyEvent.VK_UP||k==KeyEvent.VK_W)   resultsSelected=Math.max(0,resultsSelected-1);
                if(k==KeyEvent.VK_DOWN||k==KeyEvent.VK_S) resultsSelected=Math.min(2,resultsSelected+1);
                if(k==KeyEvent.VK_ENTER||k==KeyEvent.VK_SPACE){
                    if(resultsSelected==0){ totalLaps=LAP_OPTIONS[lapSelectChoice]; initGame(); }
                    else if(resultsSelected==1) state=GameState.LAP_SELECT;
                    else { state=GameState.MAIN_MENU; menuSelected=0; }
                }
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k=e.getKeyCode();
        if(state!=GameState.RACING) return;
        if(k==KeyEvent.VK_UP||k==KeyEvent.VK_W)    player.setAccel(false);
        if(k==KeyEvent.VK_DOWN||k==KeyEvent.VK_S)  player.setBrake(false);
        if(k==KeyEvent.VK_LEFT||k==KeyEvent.VK_A)  player.setLeft(false);
        if(k==KeyEvent.VK_RIGHT||k==KeyEvent.VK_D) player.setRight(false);
    }
}
