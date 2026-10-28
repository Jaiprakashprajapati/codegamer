package com.example.snipe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.core.content.res.ResourcesCompat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 60 FPS Canvas-based First-Person Sniper Game View.
 * Renders the 3D-perspective sniper rooftop arena, enemy sniper,
 * first-person rifle model, sniper optic scope, bullet tracers, and particle FX.
 */
public class SniperGameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    public interface GameEventListener {
        void onPlayerFired(float impactX, float impactY, boolean headshot, boolean bodyHit);
        void onPlayerStanceChanged(BotSniper.Stance stance);
        void onPlayerScopeChanged(boolean isScoping);
        void onPlayerDied();
        void onScoreUpdated(int p1Kills, int p2Kills);
        void onMatchEnded(boolean victory);
    }

    private GameEventListener listener;
    private SurfaceHolder holder;
    private Thread renderThread;
    private volatile boolean isRunning = false;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng = new Random();
    private Typeface pixelFont;

    // Viewport & Dimensions
    private int screenW = 1920;
    private int screenH = 1080;

    // Player Camera & Controls
    private float cameraYaw = 0f;    // Horizontal pan (-400 to +400)
    private float cameraPitch = 0f;  // Vertical pan (-200 to +200)
    private float recoilPitch = 0f;
    private float screenShake = 0f;
    private float lastTouchX, lastTouchY;
    private boolean isDragging = false;

    // Scope & Zoom
    private boolean isScoping = false;
    private float currentZoom = 1.0f;
    private float targetZoom = 1.0f;
    private float swayTime = 0f;
    private boolean isSteady = false;
    private float steadyBreath = 1.0f; // 1.0 down to 0.0

    // Weapon State
    private long boltCooldownMs = 0;
    private static final long BOLT_CYCLE_DURATION = 1200; // 1.2s bolt action
    private boolean rifleReady = true;

    // Player Stance
    private BotSniper.Stance playerStance = BotSniper.Stance.STAND;
    private boolean isPlayerAlive = true;
    private long respawnTimerMs = 0;
    private static final long RESPAWN_DURATION = 2500;

    // Opponent State
    private int opponentCoverSlot = 1; // 0: Left, 1: Center, 2: Right
    private BotSniper.Stance opponentStance = BotSniper.Stance.STAND;
    private boolean opponentScoping = false;
    private boolean opponentAlive = true;
    private long opponentRespawnMs = 0;
    private float opponentAimOffset = 0f;
    private long opponentFlashTimer = 0;

    // Score & Match
    private int myScore = 0;
    private int opponentScore = 0;
    private int targetKills = 5;
    private boolean matchFinished = false;
    private boolean wonMatch = false;

    // Effects
    private float damageFlash = 0f;
    private final List<BulletTracer> tracers = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private final List<ShellCasing> shells = new ArrayList<>();

    // Opponent Positions in world coordinates
    private final float[] coverWorldX = new float[3];
    private float opponentWorldY = 0f;

    // Starfield for atmospheric cyberpunk night sky
    private final float[] starX = new float[70];
    private final float[] starY = new float[70];
    private final float[] starSize = new float[70];

    public SniperGameView(Context context) {
        super(context);
        init();
    }

    public SniperGameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);

        try {
            pixelFont = ResourcesCompat.getFont(getContext(), R.font.press_start_2p);
        } catch (Exception ignored) {}

        for (int i = 0; i < starX.length; i++) {
            starX[i] = rng.nextFloat();
            starY[i] = rng.nextFloat() * 0.6f;
            starSize[i] = 1.5f + rng.nextFloat() * 3.5f;
        }
    }

    public void setGameEventListener(GameEventListener l) {
        this.listener = l;
    }

    public void setTargetKills(int kills) {
        this.targetKills = kills;
    }

    public void resetMatch() {
        myScore = 0;
        opponentScore = 0;
        matchFinished = false;
        wonMatch = false;
        isPlayerAlive = true;
        opponentAlive = true;
        playerStance = BotSniper.Stance.STAND;
        isScoping = false;
        targetZoom = 1.0f;
        currentZoom = 1.0f;
        if (listener != null) {
            listener.onScoreUpdated(myScore, opponentScore);
        }
    }

    // ---------------- Player Actions ----------------

    public void toggleScope() {
        if (!isPlayerAlive || matchFinished) return;
        isScoping = !isScoping;
        targetZoom = isScoping ? 2.5f : 1.0f;
        SoundEffects.playScope();
        if (listener != null) {
            listener.onPlayerScopeChanged(isScoping);
        }
    }

    public void setSteady(boolean steady) {
        if (!isScoping) return;
        this.isSteady = steady;
    }

    public void setStance(BotSniper.Stance stance) {
        if (!isPlayerAlive || matchFinished) return;
        this.playerStance = stance;
        if (stance == BotSniper.Stance.DUCK && isScoping) {
            toggleScope(); // Un-scope when ducking
        }
        if (listener != null) {
            listener.onPlayerStanceChanged(stance);
        }
    }

    public BotSniper.Stance getPlayerStance() {
        return playerStance;
    }

    public boolean canFire() {
        return isPlayerAlive && !matchFinished && boltCooldownMs <= 0;
    }

    public void fireWeapon() {
        if (!canFire()) return;

        boltCooldownMs = BOLT_CYCLE_DURATION;
        recoilPitch += isScoping ? 25f : 40f;
        screenShake = 16f;

        SoundEffects.playSniperShot();
        postDelayed(SoundEffects::playBoltAction, 500);

        // Calculate aim crosshair coordinates in world space
        float crosshairX = screenW * 0.5f;
        float crosshairY = screenH * 0.5f;

        // Firing tracer from barrel tip to crosshairs
        float barrelX = screenW * 0.72f;
        float barrelY = screenH * 0.82f;
        if (isScoping) {
            barrelX = screenW * 0.5f;
            barrelY = screenH * 0.95f;
        }
        tracers.add(new BulletTracer(barrelX, barrelY, crosshairX, crosshairY, Color.parseColor("#FFF475")));

        // Eject shell casing
        shells.add(new ShellCasing(barrelX - 40, barrelY + 20));

        // Check Hit on Opponent
        boolean headshot = false;
        boolean bodyHit = false;

        if (opponentAlive && opponentStance != BotSniper.Stance.DUCK) {
            float oppScreenX = getOpponentScreenX();
            float oppScreenY = getOpponentScreenY();

            float headRadius = 24f * currentZoom;
            float bodyW = 54f * currentZoom;
            float bodyH = 80f * currentZoom;

            float headDistSq = (crosshairX - oppScreenX) * (crosshairX - oppScreenX)
                    + (crosshairY - (oppScreenY - bodyH * 0.5f)) * (crosshairY - (oppScreenY - bodyH * 0.5f));

            if (headDistSq <= headRadius * headRadius) {
                headshot = true;
            } else if (Math.abs(crosshairX - oppScreenX) <= bodyW * 0.5f
                    && crosshairY >= (oppScreenY - bodyH * 0.5f) && crosshairY <= (oppScreenY + bodyH * 0.5f)) {
                bodyHit = true;
            }
        }

        if (headshot) {
            SoundEffects.playHeadshot();
            spawnBloodParticles(crosshairX, crosshairY);
            floatingTexts.add(new FloatingText("HEADSHOT!", crosshairX, crosshairY - 30, Color.parseColor("#FFE14D"), 22));
            handleOpponentKilled(true);
        } else if (bodyHit) {
            SoundEffects.playHit();
            spawnBloodParticles(crosshairX, crosshairY);
            floatingTexts.add(new FloatingText("HIT!", crosshairX, crosshairY - 30, Color.parseColor("#FF4D6D"), 18));
            handleOpponentKilled(false);
        } else {
            // Missed / hit wall sparks
            spawnSparkParticles(crosshairX, crosshairY);
        }

        if (listener != null) {
            listener.onPlayerFired(crosshairX, crosshairY, headshot, bodyHit);
        }
    }

    private void handleOpponentKilled(boolean headshot) {
        opponentAlive = false;
        opponentRespawnMs = RESPAWN_DURATION;
        myScore++;

        if (listener != null) {
            listener.onScoreUpdated(myScore, opponentScore);
        }

        if (myScore >= targetKills) {
            matchFinished = true;
            wonMatch = true;
            SoundEffects.playVictory();
            if (listener != null) {
                listener.onMatchEnded(true);
            }
        }
    }

    public void onPlayerHitByOpponent(boolean headshot) {
        if (!isPlayerAlive || matchFinished) return;

        damageFlash = 1.0f;
        screenShake = 22f;
        SoundEffects.playHit();

        isPlayerAlive = false;
        respawnTimerMs = RESPAWN_DURATION;
        opponentScore++;

        floatingTexts.add(new FloatingText("ELIMINATED!", screenW * 0.5f, screenH * 0.45f, Color.parseColor("#FF1744"), 24));

        if (listener != null) {
            listener.onPlayerDied();
            listener.onScoreUpdated(myScore, opponentScore);
        }

        if (opponentScore >= targetKills) {
            matchFinished = true;
            wonMatch = false;
            SoundEffects.playDefeat();
            if (listener != null) {
                listener.onMatchEnded(false);
            }
        }
    }

    // ---------------- Network / Bot Remote Updates ----------------

    public void updateOpponentState(int coverSlot, BotSniper.Stance stance, boolean scoping) {
        this.opponentCoverSlot = coverSlot;
        this.opponentStance = stance;
        this.opponentScoping = scoping;
    }

    public void triggerOpponentShot(boolean hitPlayer, boolean headshot) {
        opponentFlashTimer = 180; // flash duration ms
        SoundEffects.playSniperShot();

        float oppX = getOpponentScreenX();
        float oppY = getOpponentScreenY();
        float targetX = screenW * 0.5f + (rng.nextFloat() - 0.5f) * 100f;
        float targetY = screenH * 0.8f;
        tracers.add(new BulletTracer(oppX, oppY, targetX, targetY, Color.parseColor("#FF5252")));

        if (hitPlayer) {
            onPlayerHitByOpponent(headshot);
        } else {
            // Bullet ricocheted on our parapet
            spawnSparkParticles(targetX, targetY);
        }
    }

    public void respawnOpponent() {
        opponentAlive = true;
        opponentCoverSlot = rng.nextInt(3);
        opponentStance = BotSniper.Stance.STAND;
    }

    // ---------------- Coordinates & Screen Math ----------------

    private float getOpponentScreenX() {
        float worldX = coverWorldX[Math.min(2, Math.max(0, opponentCoverSlot))];
        float offsetX = (worldX - (screenW * 0.5f) + cameraYaw) * currentZoom;
        return screenW * 0.5f + offsetX;
    }

    private float getOpponentScreenY() {
        float stanceShift = 0;
        if (opponentStance == BotSniper.Stance.DUCK) stanceShift = 50f;
        float offsetY = (opponentWorldY - (screenH * 0.5f) + cameraPitch + recoilPitch + stanceShift) * currentZoom;
        return screenH * 0.5f + offsetY;
    }

    // ---------------- Touch Look Handling ----------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isPlayerAlive || matchFinished) return true;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Only allow look dragging if not pressing action buttons on right/bottom edges
                if (x < screenW * 0.75f) {
                    isDragging = true;
                    lastTouchX = x;
                    lastTouchY = y;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;
                    float sensitivity = isScoping ? 0.35f : 0.75f;

                    cameraYaw += dx * sensitivity;
                    cameraPitch += dy * sensitivity;

                    // Clamp camera angles
                    cameraYaw = Math.max(-500f, Math.min(500f, cameraYaw));
                    cameraPitch = Math.max(-250f, Math.min(250f, cameraPitch));

                    lastTouchX = x;
                    lastTouchY = y;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                break;
        }
        return true;
    }

    // ---------------- Game Loop ----------------

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isRunning = true;
        renderThread = new Thread(this, "SniperGameRenderer");
        renderThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        this.screenW = width;
        this.screenH = height;

        coverWorldX[0] = screenW * 0.28f;
        coverWorldX[1] = screenW * 0.50f;
        coverWorldX[2] = screenW * 0.72f;
        opponentWorldY = screenH * 0.42f;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isRunning = false;
        try {
            if (renderThread != null) {
                renderThread.join();
            }
        } catch (InterruptedException ignored) {}
    }

    @Override
    public void run() {
        long lastTime = System.currentTimeMillis();

        while (isRunning) {
            long now = System.currentTimeMillis();
            long dt = Math.min(now - lastTime, 50); // clamp dt to avoid huge steps
            lastTime = now;

            update(dt);
            drawFrame();

            // Cap at ~60fps
            long elapsed = System.currentTimeMillis() - now;
            if (elapsed < 16) {
                try {
                    Thread.sleep(16 - elapsed);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private void update(long dt) {
        // Smooth zoom interpolation
        currentZoom += (targetZoom - currentZoom) * 0.18f;

        // Recoil decay
        if (recoilPitch > 0) {
            recoilPitch -= dt * 0.08f;
            if (recoilPitch < 0) recoilPitch = 0;
        }

        // Screen shake decay
        if (screenShake > 0) {
            screenShake -= dt * 0.05f;
            if (screenShake < 0) screenShake = 0;
        }

        // Bolt cooldown
        if (boltCooldownMs > 0) {
            boltCooldownMs -= dt;
            if (boltCooldownMs <= 0) {
                boltCooldownMs = 0;
                rifleReady = true;
            }
        }

        // Scope sway
        swayTime += dt * 0.002f;
        if (isSteady) {
            steadyBreath -= dt * 0.0003f;
            if (steadyBreath <= 0) {
                steadyBreath = 0;
                isSteady = false;
            }
        } else {
            if (steadyBreath < 1.0f) {
                steadyBreath += dt * 0.00015f;
                if (steadyBreath > 1.0f) steadyBreath = 1.0f;
            }
        }

        // Damage flash decay
        if (damageFlash > 0) {
            damageFlash -= dt * 0.0025f;
            if (damageFlash < 0) damageFlash = 0;
        }

        if (opponentFlashTimer > 0) {
            opponentFlashTimer -= dt;
        }

        // Player respawn countdown
        if (!isPlayerAlive && !matchFinished) {
            respawnTimerMs -= dt;
            if (respawnTimerMs <= 0) {
                isPlayerAlive = true;
                playerStance = BotSniper.Stance.STAND;
                floatingTexts.add(new FloatingText("READY!", screenW * 0.5f, screenH * 0.45f, Color.parseColor("#3DDC6E"), 22));
            }
        }

        // Opponent respawn countdown
        if (!opponentAlive && !matchFinished) {
            opponentRespawnMs -= dt;
            if (opponentRespawnMs <= 0) {
                respawnOpponent();
            }
        }

        // Update Tracers
        Iterator<BulletTracer> itTracer = tracers.iterator();
        while (itTracer.hasNext()) {
            BulletTracer t = itTracer.next();
            t.life -= dt * 0.005f;
            if (t.life <= 0) itTracer.remove();
        }

        // Update Particles
        Iterator<Particle> itPart = particles.iterator();
        while (itPart.hasNext()) {
            Particle p = itPart.next();
            p.x += p.vx * (dt * 0.06f);
            p.y += p.vy * (dt * 0.06f);
            p.vy += 0.5f; // gravity
            p.life -= dt * 0.0025f;
            if (p.life <= 0) itPart.remove();
        }

        // Update Shells
        Iterator<ShellCasing> itShell = shells.iterator();
        while (itShell.hasNext()) {
            ShellCasing s = itShell.next();
            s.x += s.vx * (dt * 0.06f);
            s.y += s.vy * (dt * 0.06f);
            s.vy += 0.8f;
            s.rot += s.vRot * (dt * 0.06f);
            s.life -= dt * 0.0015f;
            if (s.life <= 0) itShell.remove();
        }

        // Update Floating Texts
        Iterator<FloatingText> itText = floatingTexts.iterator();
        while (itText.hasNext()) {
            FloatingText ft = itText.next();
            ft.y -= dt * 0.04f;
            ft.life -= dt * 0.0015f;
            if (ft.life <= 0) itText.remove();
        }
    }

    // ---------------- Rendering ----------------

    private void drawFrame() {
        if (!holder.getSurface().isValid()) return;
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) return;

        try {
            canvas.save();

            // Screen shake translation
            if (screenShake > 0) {
                float shakeX = (rng.nextFloat() * 2f - 1f) * screenShake;
                float shakeY = (rng.nextFloat() * 2f - 1f) * screenShake;
                canvas.translate(shakeX, shakeY);
            }

            // Stance translation for player duck/peek
            float stanceY = 0f;
            float stanceX = 0f;
            if (playerStance == BotSniper.Stance.DUCK) {
                stanceY = 120f;
            } else if (playerStance == BotSniper.Stance.PEEK_LEFT) {
                stanceX = -80f;
            } else if (playerStance == BotSniper.Stance.PEEK_RIGHT) {
                stanceX = 80f;
            }
            canvas.translate(stanceX, stanceY);

            // 1. Cyber Sky & Starfield
            drawSkyAndStars(canvas);

            // 2. Distant City Skyline (Parallax)
            drawSkyline(canvas);

            // 3. Opponent Sniper Building & Arena
            drawOpponentBuilding(canvas);

            // 4. Opponent Sniper Model
            drawOpponent(canvas);

            // 5. Particles & Bullet Tracers
            drawParticlesAndTracers(canvas);

            // 6. Player Parapet / Sandbag Cover (Foreground)
            drawPlayerParapet(canvas);

            // 7. First-Person Sniper Rifle Model
            if (!isScoping) {
                drawFirstPersonRifle(canvas);
            }

            // 8. Sniper Optic Scope View (if scoped)
            if (currentZoom > 1.05f) {
                drawSniperScope(canvas);
            }

            // 9. Floating Combat Text
            drawFloatingTexts(canvas);

            // 10. Damage Flash Screen Vignette
            if (damageFlash > 0) {
                paint.setColor(Color.argb((int) (damageFlash * 180), 255, 0, 40));
                canvas.drawRect(0, 0, screenW, screenH, paint);
            }

            // 11. Death Screen Overlay
            if (!isPlayerAlive && !matchFinished) {
                drawDeathScreen(canvas);
            }

            // 12. Victory / Defeat Screen
            if (matchFinished) {
                drawMatchOverScreen(canvas);
            }

            canvas.restore();
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawSkyAndStars(Canvas canvas) {
        // Deep purple to neon twilight gradient
        LinearGradient skyGrad = new LinearGradient(
                0, 0, 0, screenH * 0.75f,
                Color.parseColor("#08051E"), Color.parseColor("#341259"),
                Shader.TileMode.CLAMP);
        paint.setShader(skyGrad);
        canvas.drawRect(0, 0, screenW, screenH, paint);
        paint.setShader(null);

        // Twinkling stars with subtle parallax
        paint.setColor(Color.WHITE);
        for (int i = 0; i < starX.length; i++) {
            float sx = (starX[i] * screenW + cameraYaw * 0.1f) % screenW;
            if (sx < 0) sx += screenW;
            float sy = starY[i] * screenH + cameraPitch * 0.05f;
            paint.setAlpha(160 + (int) (Math.sin(swayTime * 2 + i) * 80));
            canvas.drawRect(sx, sy, sx + starSize[i], sy + starSize[i], paint);
        }
        paint.setAlpha(255);
    }

    private void drawSkyline(Canvas canvas) {
        float skylineY = screenH * 0.38f + cameraPitch * 0.15f;
        float parallaxX = cameraYaw * 0.25f;

        paint.setColor(Color.parseColor("#1B1238"));
        // Distant highrises silhouettes
        for (int i = -2; i < 16; i++) {
            float bx = i * 160f + (parallaxX % 160f);
            float bw = 120f;
            float bh = 140f + (float) Math.abs(Math.sin(i * 1.7)) * 120f;
            canvas.drawRect(bx, skylineY - bh, bx + bw, skylineY + 200, paint);

            // Pixelated neon building windows
            paint.setColor(Color.parseColor("#FFE670"));
            paint.setAlpha(110);
            for (float wy = skylineY - bh + 20; wy < skylineY; wy += 28) {
                canvas.drawRect(bx + 20, wy, bx + 36, wy + 16, paint);
                canvas.drawRect(bx + 60, wy, bx + 76, wy + 16, paint);
            }
            paint.setAlpha(255);
            paint.setColor(Color.parseColor("#1B1238"));
        }
    }

    private void drawOpponentBuilding(Canvas canvas) {
        float camY = cameraPitch * 0.5f + recoilPitch * 0.5f;
        float camX = cameraYaw * 0.5f;

        // Opponent rooftop base
        float bldgTopY = screenH * 0.44f + camY;
        paint.setColor(Color.parseColor("#25203A"));
        canvas.drawRect(-200, bldgTopY, screenW + 200, screenH, paint);

        // Brick/block texture line accents
        paint.setColor(Color.parseColor("#171328"));
        paint.setStrokeWidth(3f);
        for (float y = bldgTopY; y < screenH; y += 40) {
            canvas.drawLine(-200, y, screenW + 200, y, paint);
        }

        // Cover Nest 0 (Left Bunker/Window)
        float slot0X = coverWorldX[0] + camX;
        drawCoverStructure(canvas, slot0X, bldgTopY, "NEST A");

        // Cover Nest 1 (Center Sandbags & Parapet)
        float slot1X = coverWorldX[1] + camX;
        drawCoverStructure(canvas, slot1X, bldgTopY, "NEST B");

        // Cover Nest 2 (Right Water Tower / Outpost)
        float slot2X = coverWorldX[2] + camX;
        drawCoverStructure(canvas, slot2X, bldgTopY, "NEST C");
    }

    private void drawCoverStructure(Canvas canvas, float x, float topY, String label) {
        // Sandbags / reinforced barricade
        paint.setColor(Color.parseColor("#443E5C"));
        canvas.drawRect(x - 90, topY - 35, x + 90, topY + 40, paint);

        // Top cap
        paint.setColor(Color.parseColor("#69608A"));
        canvas.drawRect(x - 96, topY - 45, x + 96, topY - 35, paint);

        // Label
        paint.setColor(Color.parseColor("#8E82B8"));
        paint.setTextSize(12f);
        if (pixelFont != null) paint.setTypeface(pixelFont);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, x, topY + 20, paint);
    }

    private void drawOpponent(Canvas canvas) {
        if (!opponentAlive) return;

        float oppX = getOpponentScreenX();
        float oppY = getOpponentScreenY();

        canvas.save();
        canvas.translate(oppX, oppY);
        canvas.scale(currentZoom, currentZoom);

        // If scoping, draw intense scope glint lens flare!
        if (opponentScoping && opponentStance != BotSniper.Stance.DUCK) {
            drawScopeGlint(canvas, 10, -28);
        }

        // Opponent body
        if (opponentStance != BotSniper.Stance.DUCK) {
            // Torso (dark camo tactical vest)
            paint.setColor(Color.parseColor("#283B42"));
            canvas.drawRect(-18, -15, 18, 30, paint);

            // Red harness / shoulder straps
            paint.setColor(Color.parseColor("#E63946"));
            canvas.drawRect(-16, -15, -10, 25, paint);
            canvas.drawRect(10, -15, 16, 25, paint);

            // Head & Ballistic Mask
            paint.setColor(Color.parseColor("#1D2D44"));
            canvas.drawCircle(0, -32, 14, paint);

            // Sniper Visor / Goggles (Glowing cyan)
            paint.setColor(Color.parseColor("#00F5D4"));
            canvas.drawRect(-8, -35, 8, -29, paint);

            // Opponent Sniper Rifle Barrel aiming at screen
            paint.setColor(Color.parseColor("#0F141C"));
            canvas.drawRect(4, -22, 38, -14, paint);
            paint.setColor(Color.parseColor("#2A323D"));
            canvas.drawRect(10, -28, 26, -20, paint); // Rifle scope
        } else {
            // Ducking: only top of tactical helmet peeking slightly
            paint.setColor(Color.parseColor("#1D2D44"));
            canvas.drawArc(new RectF(-14, -14, 14, 14), 180, 180, true, paint);
        }

        // Muzzle Flash if opponent just fired
        if (opponentFlashTimer > 0) {
            paint.setColor(Color.parseColor("#FFF275"));
            canvas.drawCircle(38, -18, 22, paint);
            paint.setColor(Color.parseColor("#FF6B35"));
            canvas.drawCircle(38, -18, 12, paint);
        }

        canvas.restore();
    }

    private void drawScopeGlint(Canvas canvas, float gx, float gy) {
        float pulse = (float) (Math.sin(swayTime * 8) * 0.2 + 0.8);

        // Core bright star
        paint.setColor(Color.parseColor("#FFFFFF"));
        canvas.drawCircle(gx, gy, 8 * pulse, paint);

        // Horizontal and vertical glint beams
        paint.setColor(Color.parseColor("#70E000"));
        paint.setStrokeWidth(3f);
        canvas.drawLine(gx - 35 * pulse, gy, gx + 35 * pulse, gy, paint);
        canvas.drawLine(gx, gy - 25 * pulse, gx, gy + 25 * pulse, paint);

        // Cyan aura
        paint.setColor(Color.parseColor("#00F5D4"));
        paint.setAlpha(120);
        canvas.drawCircle(gx, gy, 20 * pulse, paint);
        paint.setAlpha(255);
    }

    private void drawParticlesAndTracers(Canvas canvas) {
        // Tracers
        paint.setStyle(Paint.Style.STROKE);
        for (BulletTracer t : tracers) {
            paint.setColor(t.color);
            paint.setStrokeWidth(4f * t.life);
            paint.setAlpha((int) (t.life * 255));
            canvas.drawLine(t.x1, t.y1, t.x2, t.y2, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);

        // Particles
        for (Particle p : particles) {
            paint.setColor(p.color);
            paint.setAlpha((int) (p.life * 255));
            canvas.drawRect(p.x - p.size, p.y - p.size, p.x + p.size, p.y + p.size, paint);
        }
        paint.setAlpha(255);

        // Brass Shell casings
        for (ShellCasing s : shells) {
            canvas.save();
            canvas.translate(s.x, s.y);
            canvas.rotate(s.rot);
            paint.setColor(Color.parseColor("#F4A261"));
            canvas.drawRect(-8, -3, 8, 3, paint);
            paint.setColor(Color.parseColor("#E76F51"));
            canvas.drawRect(-8, -3, -6, 3, paint);
            canvas.restore();
        }
    }

    private void drawPlayerParapet(Canvas canvas) {
        float parapetY = screenH * 0.76f;
        // Concrete wall in front of player
        paint.setColor(Color.parseColor("#1F1C2E"));
        canvas.drawRect(0, parapetY, screenW, screenH, paint);

        // Metal reinforcing trim / sandbags
        paint.setColor(Color.parseColor("#38334D"));
        canvas.drawRect(0, parapetY - 14, screenW, parapetY, paint);

        // Hazard stripes on ledge
        paint.setColor(Color.parseColor("#FFE14D"));
        paint.setAlpha(80);
        for (float x = 0; x < screenW; x += 60) {
            Path p = new Path();
            p.moveTo(x, parapetY);
            p.lineTo(x + 25, parapetY);
            p.lineTo(x + 10, parapetY - 14);
            p.lineTo(x - 15, parapetY - 14);
            p.close();
            canvas.drawPath(p, paint);
        }
        paint.setAlpha(255);
    }

    private void drawFirstPersonRifle(Canvas canvas) {
        float rifleX = screenW * 0.72f;
        float rifleY = screenH * 0.82f - recoilPitch * 2.5f;

        canvas.save();
        canvas.translate(rifleX, rifleY);
        canvas.rotate(-5f - recoilPitch * 0.4f);

        // High-caliber sniper barrel extending forward
        paint.setColor(Color.parseColor("#1A1D24"));
        canvas.drawRect(-260, -18, 40, -4, paint);

        // Heavy muzzle brake
        paint.setColor(Color.parseColor("#0C0E12"));
        canvas.drawRect(-280, -22, -260, 0, paint);

        // Sniper Receiver body
        paint.setColor(Color.parseColor("#2B3340"));
        canvas.drawRect(0, -32, 180, 20, paint);

        // Tactical Scope Mount & Tube
        paint.setColor(Color.parseColor("#15181F"));
        canvas.drawRect(-40, -62, 140, -38, paint);
        // Scope Lenses
        paint.setColor(Color.parseColor("#2EC4B6"));
        canvas.drawRect(-44, -65, -40, -35, paint);
        canvas.drawRect(140, -65, 144, -35, paint);

        // Bolt handle
        paint.setColor(Color.parseColor("#8D99AE"));
        float boltOffset = (boltCooldownMs > BOLT_CYCLE_DURATION * 0.5f) ? 25f : 0f;
        canvas.drawRect(80 + boltOffset, -36, 94 + boltOffset, -20, paint);
        canvas.drawCircle(87 + boltOffset, -16, 7, paint);

        canvas.restore();
    }

    private void drawSniperScope(Canvas canvas) {
        float cx = screenW * 0.5f;
        float cy = screenH * 0.5f;

        // Apply realistic breathing sway to crosshair if not holding breath
        float swayX = 0f;
        float swayY = 0f;
        if (!isSteady) {
            swayX = (float) (Math.sin(swayTime * 2.5f) * 14f);
            swayY = (float) (Math.cos(swayTime * 4.0f) * 9f);
        }
        float reticleX = cx + swayX;
        float reticleY = cy + swayY;

        float scopeRadius = screenH * 0.48f;

        // Black mask covering outside circle
        Path maskPath = new Path();
        maskPath.addRect(0, 0, screenW, screenH, Path.Direction.CW);
        maskPath.addCircle(cx, cy, scopeRadius, Path.Direction.CCW);
        paint.setColor(Color.BLACK);
        canvas.drawPath(maskPath, paint);

        // Outer scope ring border
        paint.setColor(Color.parseColor("#1A1D24"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(16f);
        canvas.drawCircle(cx, cy, scopeRadius, paint);

        // Inner lens reflection tint
        paint.setColor(Color.parseColor("#00F5D4"));
        paint.setAlpha(18);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, scopeRadius - 8, paint);
        paint.setAlpha(255);

        // Scope Crosshairs
        paint.setColor(Color.parseColor("#0A0D12"));
        paint.setStrokeWidth(3f);
        // Horizontal main line
        canvas.drawLine(cx - scopeRadius, reticleY, cx + scopeRadius, reticleY, paint);
        // Vertical main line
        canvas.drawLine(reticleX, cy - scopeRadius, reticleX, cy + scopeRadius, paint);

        // Mil-dots on crosshairs
        paint.setColor(Color.BLACK);
        for (int i = -4; i <= 4; i++) {
            if (i == 0) continue;
            // Horizontal ticks
            canvas.drawLine(reticleX + i * 45, reticleY - 6, reticleX + i * 45, reticleY + 6, paint);
            // Vertical elevation ticks
            canvas.drawLine(reticleX - 6, reticleY + i * 45, reticleX + 6, reticleY + i * 45, paint);
        }

        // Center Illuminated Red Dot / Reticle
        paint.setColor(Color.parseColor("#FF204E"));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(reticleX, reticleY, 3.5f, paint);

        // Elevation stadia lines at bottom
        paint.setStrokeWidth(2f);
        for (int j = 1; j <= 3; j++) {
            float sy = reticleY + j * 50;
            float sw = j * 30;
            canvas.drawLine(reticleX - sw, sy, reticleX + sw, sy, paint);
        }

        // Scope HUD text: "3.5x ZOOM", "ELEV: 100M"
        paint.setTextSize(10f);
        paint.setColor(Color.parseColor("#00F5D4"));
        if (pixelFont != null) paint.setTypeface(pixelFont);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("MAG: 3.5X", cx - scopeRadius + 35, cy - scopeRadius + 60, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(isSteady ? "[STEADY]" : "BREATH: " + (int)(steadyBreath * 100) + "%",
                cx + scopeRadius - 35, cy - scopeRadius + 60, paint);
    }

    private void drawFloatingTexts(Canvas canvas) {
        if (pixelFont != null) paint.setTypeface(pixelFont);
        paint.setTextAlign(Paint.Align.CENTER);
        for (FloatingText ft : floatingTexts) {
            paint.setColor(ft.color);
            paint.setTextSize(ft.size);
            paint.setAlpha((int) (ft.life * 255));
            canvas.drawText(ft.text, ft.x, ft.y, paint);
        }
        paint.setAlpha(255);
    }

    private void drawDeathScreen(Canvas canvas) {
        paint.setColor(Color.parseColor("#B0000000"));
        canvas.drawRect(0, 0, screenW, screenH, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        if (pixelFont != null) paint.setTypeface(pixelFont);

        paint.setColor(Color.parseColor("#FF1744"));
        paint.setTextSize(32f);
        canvas.drawText("YOU WERE ELIMINATED", screenW * 0.5f, screenH * 0.42f, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(14f);
        int sec = (int) Math.ceil(respawnTimerMs / 1000.0);
        canvas.drawText("RESPAWNING IN " + sec + "S...", screenW * 0.5f, screenH * 0.54f, paint);
    }

    private void drawMatchOverScreen(Canvas canvas) {
        paint.setColor(Color.parseColor("#D90A091A"));
        canvas.drawRect(0, 0, screenW, screenH, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        if (pixelFont != null) paint.setTypeface(pixelFont);

        if (wonMatch) {
            paint.setColor(Color.parseColor("#FFE14D"));
            paint.setTextSize(38f);
            canvas.drawText("VICTORY!", screenW * 0.5f, screenH * 0.38f, paint);
        } else {
            paint.setColor(Color.parseColor("#FF3366"));
            paint.setTextSize(38f);
            canvas.drawText("DEFEAT", screenW * 0.5f, screenH * 0.38f, paint);
        }

        paint.setColor(Color.WHITE);
        paint.setTextSize(16f);
        canvas.drawText("FINAL SCORE: " + myScore + " - " + opponentScore, screenW * 0.5f, screenH * 0.52f, paint);

        paint.setColor(Color.parseColor("#29D3F5"));
        paint.setTextSize(10f);
        canvas.drawText("FIRST TO " + targetKills + " KILLS REACHED", screenW * 0.5f, screenH * 0.62f, paint);
    }

    // ---------------- Particles Spawning ----------------

    private void spawnBloodParticles(float x, float y) {
        for (int i = 0; i < 20; i++) {
            float vx = (rng.nextFloat() * 2f - 1f) * 12f;
            float vy = (rng.nextFloat() * 2f - 1.5f) * 10f;
            particles.add(new Particle(x, y, vx, vy, Color.parseColor("#E63946"), 4f));
        }
    }

    private void spawnSparkParticles(float x, float y) {
        for (int i = 0; i < 16; i++) {
            float vx = (rng.nextFloat() * 2f - 1f) * 14f;
            float vy = (rng.nextFloat() * 2f - 1.8f) * 12f;
            particles.add(new Particle(x, y, vx, vy, Color.parseColor("#FFE66D"), 3f));
        }
    }

    // ---------------- Helper Data Classes ----------------

    private static class BulletTracer {
        float x1, y1, x2, y2;
        int color;
        float life = 1.0f;
        BulletTracer(float x1, float y1, float x2, float y2, int color) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.color = color;
        }
    }

    private static class Particle {
        float x, y, vx, vy;
        int color;
        float size;
        float life = 1.0f;
        Particle(float x, float y, float vx, float vy, int color, float size) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.color = color; this.size = size;
        }
    }

    private static class ShellCasing {
        float x, y, vx, vy, rot, vRot;
        float life = 1.0f;
        ShellCasing(float x, float y) {
            this.x = x; this.y = y;
            this.vx = 4f + (float) Math.random() * 4f;
            this.vy = -6f - (float) Math.random() * 4f;
            this.rot = 0f;
            this.vRot = 15f + (float) Math.random() * 20f;
        }
    }

    private static class FloatingText {
        String text;
        float x, y;
        int color;
        float size;
        float life = 1.0f;
        FloatingText(String text, float x, float y, int color, float size) {
            this.text = text; this.x = x; this.y = y; this.color = color; this.size = size;
        }
    }
}
