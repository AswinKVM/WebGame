import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.prefs.Preferences;

public class BreakoutGame extends JFrame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            // ===== LOGIN SYSTEM =====
            JTextField usernameField = new JTextField();
            JPasswordField passwordField = new JPasswordField();

            Object[] message = {
                    "Username:", usernameField,
                    "Password:", passwordField
            };

            int option = JOptionPane.showConfirmDialog(
                    null,
                    message,
                    "Login",
                    JOptionPane.OK_CANCEL_OPTION
            );

            if (option == JOptionPane.OK_OPTION) {
                String username = usernameField.getText();
                String password = new String(passwordField.getPassword());

                if (username.equals("Krupa") && password.equals("Krupa@144")) {
                    BreakoutGame frame = new BreakoutGame();
                    frame.setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(null, "Invalid Credentials!");
                    System.exit(0);
                }
            } else {
                System.exit(0);
            }
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

// ================= GAME PANEL =================
class GamePanel extends JPanel implements ActionListener, KeyListener, MouseMotionListener, MouseListener {

    static final int W = 700, H = 500;

    static final int COLS = 10, BPAD = 6, LEFT = 20, TOP = 55;
    static final int BH = 22;
    static final int BW = (W - LEFT * 2 - BPAD * (COLS - 1)) / COLS;

    static final Color[] COLORS = {
            new Color(0xFF6B6B), new Color(0xFFD93D),
            new Color(0x6BCB77), new Color(0x4D96FF),
            new Color(0xC77DFF), new Color(0xAAAAAA)
    };

    static final int[] PTS = {10, 20, 5, 15, 25, 8};

    int score, level, lives, hiScore;
    boolean wide, playing;

    ArrayList<Brick> bricks = new ArrayList<>();

    double ballX, ballY, ballVx, ballVy;
    final int BALL_R = 8;
    boolean ballStuck = true;

    double padX;
    final int PAD_Y = H - 36;
    final int PAD_W = 110;
    final int PAD_H = 14;

    boolean leftDown, rightDown;
    double mouseX = -1;

    Timer timer;
    Preferences prefs = Preferences.userNodeForPackage(GamePanel.class);

    GamePanel() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);

        addKeyListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);

        hiScore = prefs.getInt("bx_hi", 0);

        timer = new Timer(1000 / 60, this);
        timer.start();
    }

    void startGame() {
        score = 0;
        level = 1;
        lives = 3;
        wide = false;
        playing = true;

        padX = W / 2 - PAD_W / 2;

        buildBricks();
        resetBall();
    }

    void buildBricks() {
        bricks.clear();
        int rows = Math.min(5 + level, 10);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < COLS; c++) {
                int i = Math.min(r + (level / 2), COLORS.length - 1);
                int bx = LEFT + c * (BW + BPAD);
                int by = TOP + r * (BH + BPAD);
                bricks.add(new Brick(bx, by, COLORS[i], PTS[i], (i == 5) ? 2 : 1));
            }
        }
    }

    void resetBall() {
        double pw = wide ? PAD_W * 1.7 : PAD_W;
        ballX = padX + pw / 2;
        ballY = PAD_Y - BALL_R - 1;
        ballVx = 3;
        ballVy = -5;
        ballStuck = true;
    }

    void launch() {
        ballStuck = false;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (playing) update();
        repaint();
    }

    void update() {
        double pw = wide ? PAD_W * 1.7 : PAD_W;

        if (leftDown) padX -= 10;
        if (rightDown) padX += 10;
        if (mouseX >= 0) padX = mouseX - pw / 2;

        padX = Math.max(0, Math.min(W - pw, padX));

        if (ballStuck) {
            ballX = padX + pw / 2;
            return;
        }

        double spd = 5 + (level - 1) * 0.3;
        double mag = Math.hypot(ballVx, ballVy);

        ballX += (ballVx / mag) * spd;
        ballY += (ballVy / mag) * spd;

        if (ballX < BALL_R || ballX > W - BALL_R) ballVx *= -1;
        if (ballY < BALL_R) ballVy *= -1;

        if (ballVy > 0 &&
                ballY + BALL_R >= PAD_Y &&
                ballX >= padX && ballX <= padX + pw) {

            double hit = (ballX - (padX + pw / 2)) / (pw / 2);
            ballVx = 5 * Math.sin(hit);
            ballVy = -Math.abs(5 * Math.cos(hit));
        }

        if (ballY > H) {
            lives--;
            if (lives <= 0) {
                playing = false;
                JOptionPane.showMessageDialog(this, "GAME OVER!");
                return;
            }
            resetBall();
        }

        for (Brick b : bricks) {
            if (!b.alive) continue;

            if (ballX > b.x && ballX < b.x + BW &&
                    ballY > b.y && ballY < b.y + BH) {

                ballVy *= -1;
                b.hits--;

                if (b.hits <= 0) {
                    b.alive = false;
                    score += b.pts;
                }
                break;
            }
        }

        if (bricks.stream().noneMatch(b -> b.alive)) {
            level++;
            buildBricks();
            resetBall();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, H);

        g.setColor(Color.WHITE);
        g.drawString("Score: " + score, 20, 20);
        g.drawString("Lives: " + lives, 120, 20);
        g.drawString("Level: " + level, 220, 20);

        for (Brick b : bricks) {
            if (!b.alive) continue;
            g.setColor(b.color);
            g.fillRect(b.x, b.y, BW, BH);
        }

        g.setColor(Color.BLUE);
        g.fillRect((int) padX, PAD_Y, PAD_W, PAD_H);

        g.setColor(Color.WHITE);
        g.fillOval((int) ballX, (int) ballY, BALL_R * 2, BALL_R * 2);
    }

    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_LEFT) leftDown = true;
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) rightDown = true;

        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (!playing) startGame();
            else launch();
        }
    }

    @Override public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_LEFT) leftDown = false;
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) rightDown = false;
    }

    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mouseMoved(MouseEvent e) { mouseX = e.getX(); }
    @Override public void mouseDragged(MouseEvent e) { mouseX = e.getX(); }
    @Override public void mouseClicked(MouseEvent e) { if (!playing) startGame(); }
    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
}

// ================= BRICK =================
class Brick {
    int x, y, pts, hits;
    Color color;
    boolean alive = true;

    Brick(int x, int y, Color color, int pts, int hits) {
        this.x = x;
        this.y = y;
        this.color = color;
        this.pts = pts;
        this.hits = hits;
    }
}