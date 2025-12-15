import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

class MindandReflex extends JPanel implements KeyListener {
    static final int W = 720, H = 480, HP_MAX = 5, E_MAX = 5;
    static final Random R = new Random();

    static class Obj {
        float x, y, vx, vy, r;
        boolean real, orb;
        Obj(float x, float y) { this.x = x; this.y = y; }
    }

    ArrayList<Obj> hazards = new ArrayList<>(), orbs = new ArrayList<>();
    boolean up, down, left, right, over;
    float px, py, pr = 10, ps = 210;

    int hp, energy, score, best, level, mood;
    long startMs, lastHazMs, lastOrbMs, shiftAtMs, revealUntilMs, slowUntilMs, msgUntilMs;
    float shiftDur = 6.5f, warnDur = 0.9f;
    String msg = "";

    long lastN = System.nanoTime();

    public MindandReflex() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        restart();
        new Timer(16, e -> { tick(); repaint(); }).start();
    }

    void restart() {
        hazards.clear(); orbs.clear();
        px = W / 2f; py = H / 2f;
        hp = HP_MAX; energy = 0; score = 0; level = 1; mood = 0;
        over = false;
        startMs = System.currentTimeMillis();
        lastHazMs = lastOrbMs = 0;
        revealUntilMs = slowUntilMs = msgUntilMs = 0;
        scheduleShift();
        flash("READY", 700);
    }

    void flash(String s, int ms) {
        msg = s;
        msgUntilMs = System.currentTimeMillis() + ms;
    }

    void scheduleShift() {
        shiftAtMs = System.currentTimeMillis() + (long)(shiftDur * 1000);
    }

    String moodName() {
        return switch (mood) {
            case 1 -> "RUSH";
            case 2 -> "SEEK";
            case 3 -> "HEAVY";
            case 4 -> "TRICK";
            default -> "CALM";
        };
    }

    void shiftMood() {
        int nm = mood;
        while (nm == mood) nm = R.nextInt(5);
        mood = nm;

        level = 1 + score / 25;
        shiftDur = Math.max(3.6f, 6.5f - 0.18f * (level - 1));
        warnDur  = Math.max(0.55f, 0.90f - 0.02f * (level - 1));

        scheduleShift();
        flash("MOODSHIFT → " + moodName(), 900);
    }

    void spawnHazard() {
        Obj e = new Obj(0, 0);
        int side = R.nextInt(4);
        if (side == 0) { e.x = -12; e.y = R.nextInt(H); }
        if (side == 1) { e.x = W + 12; e.y = R.nextInt(H); }
        if (side == 2) { e.x = R.nextInt(W); e.y = -12; }
        if (side == 3) { e.x = R.nextInt(W); e.y = H + 12; }

        float dx = px - e.x, dy = py - e.y;
        float d = (float)Math.hypot(dx, dy); if (d < 1) d = 1;

        float sp = 90 + level * 12;
        if (mood == 1) sp *= 1.45f;
        if (mood == 3) sp *= 0.75f;

        e.vx = dx / d * sp;
        e.vy = dy / d * sp;
        e.r = (mood == 3) ? 14 : 10;
        e.real = !(mood == 4 && R.nextInt(3) == 0); // TRICK: some fakes
        hazards.add(e);
    }

    void spawnOrb() {
        Obj o = new Obj(20 + R.nextInt(W - 40), 20 + R.nextInt(H - 40));
        o.orb = true;
        o.r = 7;
        orbs.add(o);
    }

    float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }

    void useCenter() {
        if (over || energy <= 0) return;
        energy--;
        long now = System.currentTimeMillis();
        revealUntilMs = now + 2200;
        slowUntilMs = now + 1200;
        flash("CENTER!", 600);

        float rad = 160f, rr = rad * rad;
        for (int i = 0; i < hazards.size(); i++) {
            Obj e = hazards.get(i);
            float dx = e.x - px, dy = e.y - py;
            if (dx * dx + dy * dy < rr) hazards.remove(i--);
        }
    }

    void hurt() {
        hp--;
        flash("HIT! (-HP)", 650);
        if (hp <= 0) {
            over = true;
            best = Math.max(best, score);
            flash("GAME OVER", 1200);
        }
    }

    void tick() {
        long nowN = System.nanoTime();
        float dt = (nowN - lastN) / 1_000_000_000f;
        lastN = nowN;

        long now = System.currentTimeMillis();
        if (!over) score = (int)((now - startMs) / 1000);

        float toShift = (shiftAtMs - now) / 1000f;
        if (!over && toShift <= 0) shiftMood();

        if (!over) {
            int hazMs = (int)Math.max(140, 520 - level * 22 - (mood == 1 ? 90 : 0));
            if (now - lastHazMs >= hazMs) { lastHazMs = now; spawnHazard(); }

            int orbMs = (int)Math.max(900, 2600 - level * 70);
            if (now - lastOrbMs >= orbMs) { lastOrbMs = now; spawnOrb(); }
        }

        int mx = (right ? 1 : 0) - (left ? 1 : 0);
        int my = (down ? 1 : 0) - (up ? 1 : 0);
        float len = (float)Math.hypot(mx, my);
        float nx = (len > 0) ? mx / len : 0;
        float ny = (len > 0) ? my / len : 0;

        px = clamp(px + nx * ps * dt, 12, W - 12);
        py = clamp(py + ny * ps * dt, 12, H - 12);

        float slow = (now < slowUntilMs) ? 0.55f : 1f;
        for (int i = 0; i < hazards.size(); i++) {
            Obj e = hazards.get(i);

            if (mood == 2 && e.real) {
                float dx = px - e.x, dy = py - e.y;
                float d = (float)Math.hypot(dx, dy); if (d < 1) d = 1;
                float turn = 5.5f * dt;
                e.vx += (dx / d * 120 - e.vx) * turn;
                e.vy += (dy / d * 120 - e.vy) * turn;
            }

            e.x += e.vx * dt * slow;
            e.y += e.vy * dt * slow;

            if (e.x < -60 || e.x > W + 60 || e.y < -60 || e.y > H + 60) hazards.remove(i--);
        }

        for (int i = 0; i < orbs.size(); i++) {
            Obj o = orbs.get(i);
            float dx = o.x - px, dy = o.y - py, rr = (pr + o.r) * (pr + o.r);
            if (dx * dx + dy * dy < rr) {
                orbs.remove(i--);
                energy = Math.min(E_MAX, energy + 1);
                flash("Mind Orb +1", 600);
            }
        }

        for (int i = 0; i < hazards.size() && !over; i++) {
            Obj e = hazards.get(i);
            if (!e.real) continue;
            float dx = e.x - px, dy = e.y - py, rr = (pr + e.r) * (pr + e.r);
            if (dx * dx + dy * dy < rr) { hazards.remove(i--); hurt(); }
        }
    }

    @Override protected void paintComponent(Graphics gg) {
        super.paintComponent(gg);
        Graphics2D g = (Graphics2D)gg;
        g.setColor(new Color(18,18,24));
        g.fillRect(0, 0, W, H);

        long now = System.currentTimeMillis();
        float toShift = (shiftAtMs - now) / 1000f;
        boolean warn = !over && toShift > 0 && toShift < warnDur;
        boolean reveal = now < revealUntilMs;

        g.setColor(warn ? new Color(250,220,90) : new Color(70,70,95));
        g.drawRect(8, 8, W - 16, H - 16);

        g.setColor(new Color(120,190,255));
        for (Obj o : orbs) g.fillOval((int)(o.x - o.r), (int)(o.y - o.r), (int)(o.r * 2), (int)(o.r * 2));

        for (Obj e : hazards) {
            if (e.real) g.setColor(new Color(230,80,80));
            else g.setColor(reveal ? new Color(180,180,200) : new Color(230,80,80));
            int r = (int)e.r;
            g.fillOval((int)(e.x - r), (int)(e.y - r), r * 2, r * 2);
        }

        g.setColor(new Color(90,220,140));
        g.fillOval((int)(px - pr), (int)(py - pr), (int)(pr * 2), (int)(pr * 2));
        if (reveal) {
            g.setColor(new Color(200,200,255,90));
            g.drawOval((int)px - 70, (int)py - 70, 140, 140);
        }

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString("MindandReflex", 12, 22);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.drawString("HP:"+hp+"  Score:"+score+"  Best:"+best+"  Level:"+level, 12, 42);
        g.drawString("Mood: "+moodName()+"  Shift in: "+String.format(Locale.US,"%.1f",Math.max(0,toShift))+"s", 12, 60);
        g.drawString("Mind Orbs: "+energy+"/"+E_MAX+"  |  CENTER (SPACE)  |  Move: Arrows  |  R: Restart", 12, 78);

        if (now < msgUntilMs && msg != null && !msg.isEmpty()) {
            g.setColor(new Color(0,0,0,170));
            g.fillRoundRect(180, 10, W - 360, 28, 12, 12);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            int sw = g.getFontMetrics().stringWidth(msg);
            g.drawString(msg, W / 2 - sw / 2, 30);
        }

        if (over) {
            g.setColor(new Color(0,0,0,190));
            g.fillRect(0, 0, W, H);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 30));
            g.drawString("GAME OVER", W / 2 - 95, H / 2 - 10);
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g.drawString("Press R to restart", W / 2 - 72, H / 2 + 20);
        }
    }

    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_UP) up = true;
        if (k == KeyEvent.VK_DOWN) down = true;
        if (k == KeyEvent.VK_LEFT) left = true;
        if (k == KeyEvent.VK_RIGHT) right = true;
        if (k == KeyEvent.VK_SPACE) useCenter();
        if (k == KeyEvent.VK_R) { best = Math.max(best, score); restart(); }
    }

    @Override public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_UP) up = false;
        if (k == KeyEvent.VK_DOWN) down = false;
        if (k == KeyEvent.VK_LEFT) left = false;
        if (k == KeyEvent.VK_RIGHT) right = false;
    }

    @Override public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Mind and Reflex ");
            MindandReflex p = new MindandReflex();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(p);
            f.pack(); f.setResizable(false);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            p.requestFocusInWindow();
        });
    }
}