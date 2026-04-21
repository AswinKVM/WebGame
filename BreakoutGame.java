import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.prefs.Preferences;

/**
 * BreakoutGame.java
 * A Java Swing port of the HTML5 Canvas Breakout game.
 * Equivalent to game.js — same mechanics, speed, levels, and scoring.
 *
 * Run:  javac BreakoutGame.java && java BreakoutGame
 */
public class BreakoutGame extends JFrame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BreakoutGame frame = new BreakoutGame();
            frame.setVisible(true);
        });
    }

    public BreakoutGame() {
        setTitle("Breakout");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        GamePanel panel = new GamePanel();
        add(panel);
        pack();
        setLocationRelativeTo(null);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GamePanel  ←  equivalent to the entire game.js logic + drawing
// ─────────────────────────────────────────────────────────────────────────────
class GamePanel extends JPanel implements ActionListener, KeyListener, MouseMotionListener, MouseListener {

    // ── Canvas dimensions ────────────────────────────────────────
    static final int W = 700, H = 500;

    // ── Brick Grid (mirrors game.js) ─────────────────────────────
    static final int COLS  = 10;
    static final int BPAD  = 6;       // padding between bricks
    static final int LEFT  = 20;
    static final int TOP   = 55;
    static final int BH    = 22;
    static final int BW    = (W - LEFT * 2 - BPAD * (COLS - 1)) / COLS;

    // Colors and point values (same order as JS)
    static final Color[] COLORS = {
        new Color(0xFF6B6B), new Color(0xFFD93D), new Color(0x6BCB77),
        new Color(0x4D96FF), new Color(0xC77DFF), new Color(0xAAAAAA)
    };
    static final int[] PTS = { 10, 20, 5, 15, 25, 8 };

    // ── State ────────────────────────────────────────────────────
    int  score, level, lives;
    int  hiScore;
    boolean wide, playing;
    ArrayList<Brick> bricks = new ArrayList<>();

    // ── Ball ─────────────────────────────────────────────────────
    double ballX, ballY, ballVx, ballVy;
    final int BALL_R = 8;
    boolean ballStuck = true;

    // ── Paddle ───────────────────────────────────────────────────
    double padX;
    final int PAD_Y = H - 36;
    final int PAD_W = 110;
    final int PAD_H = 14;

    // ── Input ────────────────────────────────────────────────────
    boolean leftDown, rightDown;
    double  mouseX = -1;

    // ── Overlay state ────────────────────────────────────────────
    enum Screen { MENU, PLAYING, PAUSED, GAMEOVER, WIN }
    Screen screen = Screen.MENU;
    String overlayTitle = "BREAKOUT";
    String overlayIcon  = "▶";
    String overlaySub   = "Click or press Space to start";
    String overlayBtn   = "START";

    // ── Timer (game loop, ~60 fps) ────────────────────────────────
    Timer timer;

    // ── Hi-score persistence (replaces localStorage) ──────────────
    Preferences prefs = Preferences.userNodeForPackage(GamePanel.class);

    // ─────────────────────────────────────────────────────────────
    GamePanel() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);

        hiScore = prefs.getInt("bx_hi", 0);

        timer = new Timer(1000 / 60, this);  // 60 FPS
        timer.start();
    }

    // ── Init ─────────────────────────────────────────────────────
    void startGame() {
        score = 0; level = 1; lives = 3; wide = false; playing = true;
        screen = Screen.PLAYING;
        buildBricks();
        resetBall();
        requestFocusInWindow();
    }

    void buildBricks() {
        bricks.clear();
        int rows = Math.min(5 + level, 10);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < COLS; c++) {
                int i = Math.min(r + (level / 2), COLORS.length - 1);
                int bx = LEFT + c * (BW + BPAD);
                int by = TOP  + r * (BH + BPAD);
                bricks.add(new Brick(bx, by, COLORS[i], PTS[i], (i == 5) ? 2 : 1));
            }
        }
    }

    void resetBall() {
        double pw = wide ? PAD_W * 1.7 : PAD_W;
        ballX  = padX + pw / 2;
        ballY  = PAD_Y - BALL_R - 1;
        ballVx = 3;
        ballVy = -5;
        ballStuck = true;
    }

    void launch() {
        if (ballStuck) ballStuck = false;
    }

    // ── Game Loop (ActionListener from Timer) ─────────────────────
    @Override
    public void actionPerformed(ActionEvent e) {
        if (playing) update();
        repaint();
    }

    // ── Update (mirrors update() in game.js) ──────────────────────
    void update() {
        double pw = wide ? PAD_W * 1.7 : PAD_W;

        // Move paddle
        if (leftDown)       padX -= 10;
        if (rightDown)      padX += 10;
        if (mouseX >= 0)    padX = mouseX - pw / 2;
        padX = Math.max(0, Math.min(W - pw, padX));

        // Stuck ball follows paddle
        if (ballStuck) {
            ballX = padX + pw / 2;
            return;
        }

        // Speed: 5 + (level-1) * 0.3  (mirrors line 98 in game.js)
        double spd = 5 + (level - 1) * 0.3;
        double mag = Math.hypot(ballVx, ballVy);
        if (mag == 0) mag = 1;
        ballX += (ballVx / mag) * spd;
        ballY += (ballVy / mag) * spd;

        // Wall bounces
        if (ballX - BALL_R < 0)  { ballX =  BALL_R;     ballVx =  Math.abs(ballVx); }
        if (ballX + BALL_R > W)  { ballX =  W - BALL_R; ballVx = -Math.abs(ballVx); }
        if (ballY - BALL_R < 0)  { ballY =  BALL_R;     ballVy =  Math.abs(ballVy); }

        // Paddle bounce
        if (ballVy > 0
                && ballY + BALL_R >= PAD_Y
                && ballY - BALL_R <= PAD_Y + PAD_H
                && ballX >= padX
                && ballX <= padX + pw) {
            ballY = PAD_Y - BALL_R;
            double hit = (ballX - (padX + pw / 2)) / (pw / 2);
            double sp  = Math.hypot(ballVx, ballVy);
            ballVx = sp * Math.sin(hit * Math.PI / 3);
            ballVy = -Math.abs(sp * Math.cos(hit * Math.PI / 3));
        }

        // Lost ball
        if (ballY - BALL_R > H) {
            lives--;
            if (lives <= 0) { endGame(false); return; }
            resetBall();
            return;
        }

        // Brick collision
        for (Brick b : bricks) {
            if (!b.alive) continue;
            if (ballX + BALL_R < b.x || ballX - BALL_R > b.x + BW
             || ballY + BALL_R < b.y || ballY - BALL_R > b.y + BH) continue;

            double oL = (ballX + BALL_R) - b.x;
            double oR = (b.x + BW)       - (ballX - BALL_R);
            double oT = (ballY + BALL_R) - b.y;
            double oB = (b.y + BH)       - (ballY - BALL_R);
            double m  = Math.min(Math.min(oL, oR), Math.min(oT, oB));

            if      (m == oL) { ballX -= oL; ballVx = -Math.abs(ballVx); }
            else if (m == oR) { ballX += oR; ballVx =  Math.abs(ballVx); }
            else if (m == oT) { ballY -= oT; ballVy = -Math.abs(ballVy); }
            else              { ballY += oB; ballVy =  Math.abs(ballVy); }

            b.hits--;
            if (b.hits <= 0) { b.alive = false; score += b.pts; }
            break;
        }

        // Win check
        boolean allDead = bricks.stream().noneMatch(b -> b.alive);
        if (allDead) {
            if (level >= 10) { endGame(true); return; }
            level++;
            wide = !wide;
            buildBricks();
            resetBall();
            showMsg("LEVEL " + level, String.valueOf(level),
                    "Things are warming up. Level " + level + ".", "CONTINUE");
        }
    }

    // ── Draw (mirrors draw() in game.js) ─────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        g2.setColor(new Color(0x05, 0x0B, 0x18));
        g2.fillRect(0, 0, W, H);

        // Scanlines
        g2.setColor(new Color(0, 0, 0, 18));
        for (int y = 0; y < H; y += 4) g2.fillRect(0, y, W, 2);

        // HUD bar (top)
        drawHUD(g2);

        if (screen == Screen.PLAYING || screen == Screen.PAUSED) {
            drawBricks(g2);
            drawPaddle(g2);
            drawBall(g2);
        } else {
            drawBricks(g2);
            drawPaddle(g2);
            drawOverlay(g2);
        }
    }

    void drawHUD(Graphics2D g2) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.setColor(new Color(180, 180, 255));
        g2.drawString("SCORE: " + score,        10,  22);
        g2.drawString("LEVEL: " + level,        180, 22);
        g2.drawString("LIVES: " + Math.max(lives, 0) + " / 3", 330, 22);
        g2.drawString("HI: " + hiScore,         530, 22);

        // Separator line
        g2.setColor(new Color(255, 255, 255, 30));
        g2.drawLine(0, 30, W, 30);
    }

    void drawBricks(Graphics2D g2) {
        long now = System.currentTimeMillis();
        for (Brick b : bricks) {
            if (!b.alive) continue;
            // Grey bricks pulse when on 1 hit
            float alpha = (b.hits == 1 && b.color.equals(COLORS[5]))
                    ? (float)(0.5 + 0.4 * Math.sin(now * 0.01)) : 1f;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(b.color);
            g2.fill(roundRect(b.x, b.y, BW, BH, 4));
            // Shine highlight
            g2.setColor(new Color(255, 255, 255, 38));
            g2.fill(roundRect(b.x + 2, b.y + 2, BW - 4, 5, 2));
            g2.setComposite(AlphaComposite.SrcOver);
        }
    }

    void drawPaddle(Graphics2D g2) {
        double pw = wide ? PAD_W * 1.7 : PAD_W;
        g2.setColor(wide ? new Color(0x00F5FF) : new Color(0x9090FF));
        g2.fill(roundRect((int)padX, PAD_Y, (int)pw, PAD_H, 7));
    }

    void drawBall(Graphics2D g2) {
        g2.setColor(Color.WHITE);
        g2.fillOval((int)(ballX - BALL_R), (int)(ballY - BALL_R), BALL_R * 2, BALL_R * 2);
    }

    void drawOverlay(Graphics2D g2) {
        // Dim background
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(0, 0, W, H);

        // Card background
        int cw = 340, ch = 200;
        int cx = (W - cw) / 2, cy = (H - ch) / 2;
        g2.setColor(new Color(20, 25, 50, 230));
        g2.fill(new RoundRectangle2D.Double(cx, cy, cw, ch, 20, 20));
        g2.setColor(new Color(100, 100, 200, 80));
        g2.draw(new RoundRectangle2D.Double(cx, cy, cw, ch, 20, 20));

        // Title
        g2.setFont(new Font("SansSerif", Font.BOLD, 28));
        g2.setColor(new Color(0xC77DFF));
        drawCenteredString(g2, overlayTitle, cy + 50);

        // Sub text
        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g2.setColor(new Color(200, 200, 220));
        drawCenteredString(g2, overlaySub, cy + 90);

        // Button
        int bw = 160, bh = 36;
        int bx = (W - bw) / 2, by = cy + ch - 60;
        g2.setColor(new Color(0x4D96FF));
        g2.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 10, 10));
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setColor(Color.WHITE);
        drawCenteredString(g2, overlayBtn, by + 24);
    }

    // ── Overlay helpers ───────────────────────────────────────────
    void showMsg(String title, String icon, String sub, String btn) {
        playing = false;
        screen  = Screen.PAUSED;
        overlayTitle = title;
        overlayIcon  = icon;
        overlaySub   = sub;
        overlayBtn   = btn;
    }

    void endGame(boolean won) {
        playing = false;
        if (score > hiScore) {
            hiScore = score;
            prefs.putInt("bx_hi", hiScore);  // Save hi-score (replaces localStorage)
        }
        screen = won ? Screen.WIN : Screen.GAMEOVER;
        if (won) {
            showMsg("YOU WIN", "GG",
                    score + " pts  •  Every wall came down.", "PLAY AGAIN");
        } else {
            showMsg("GAME OVER", "OUT",
                    score + " pts  •  Adjust. Come back.", "TRY AGAIN");
        }
    }

    // ── Utilities ─────────────────────────────────────────────────
    RoundRectangle2D roundRect(int x, int y, int w, int h, int arc) {
        return new RoundRectangle2D.Double(x, y, w, h, arc, arc);
    }

    void drawCenteredString(Graphics2D g2, String s, int y) {
        FontMetrics fm = g2.getFontMetrics();
        int x = (W - fm.stringWidth(s)) / 2;
        g2.drawString(s, x, y);
    }

    // ── Keyboard input (mirrors keydown/keyup listeners) ──────────
    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_LEFT)  leftDown  = true;
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) rightDown = true;
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (screen == Screen.PLAYING) launch();
            else if (screen == Screen.MENU || screen == Screen.GAMEOVER
                  || screen == Screen.WIN)  startGame();
            else if (screen == Screen.PAUSED) resumeGame();
        }
        if ((e.getKeyCode() == KeyEvent.VK_R) && screen != Screen.PLAYING) startGame();
    }
    @Override public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_LEFT)  leftDown  = false;
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) rightDown = false;
    }
    @Override public void keyTyped(KeyEvent e) {}

    // ── Mouse input (mirrors mousemove + click listeners) ─────────
    @Override public void mouseMoved(MouseEvent e)   { mouseX = e.getX(); }
    @Override public void mouseDragged(MouseEvent e) { mouseX = e.getX(); }
    @Override public void mouseClicked(MouseEvent e) {
        if (screen == Screen.PLAYING) { launch(); return; }
        if (screen == Screen.MENU || screen == Screen.GAMEOVER
         || screen == Screen.WIN)  { startGame(); return; }
        if (screen == Screen.PAUSED) resumeGame();
    }
    @Override public void mousePressed(MouseEvent e)  {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e)  {}
    @Override public void mouseExited(MouseEvent e)   { mouseX = -1; }

    void resumeGame() {
        playing = true;
        screen  = Screen.PLAYING;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Brick  ←  equivalent to the brick objects in buildBricks()
// ─────────────────────────────────────────────────────────────────────────────
class Brick {
    int   x, y, pts, hits;
    Color color;
    boolean alive = true;

    Brick(int x, int y, Color color, int pts, int hits) {
        this.x = x; this.y = y;
        this.color = color;
        this.pts   = pts;
        this.hits  = hits;
    }
}
