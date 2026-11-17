package com.example.snipe;

import java.util.Random;

/**
 * AI Bot for single-player practice / offline 1v1 sniper duel.
 * Simulates human sniper behavior: taking cover, peeking, aiming, scope glint, and firing.
 */
public class BotSniper {

    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public enum Stance {
        STAND,
        DUCK,
        PEEK_LEFT,
        PEEK_RIGHT
    }

    public interface BotListener {
        void onBotShoot(boolean hitPlayer, boolean isHeadshot);
        void onBotMoved(int coverSlot, Stance stance, boolean isScoping);
    }

    private final Random rng = new Random();
    private BotListener listener;
    private Difficulty difficulty = Difficulty.MEDIUM;

    private int coverSlot = 1; // 0: Left Window, 1: Center Sandbag, 2: Right Tower
    private Stance currentStance = Stance.STAND;
    private boolean isScoping = false;
    private boolean isAlive = true;

    private long stateTimer = 0;
    private long nextActionTime = 1500;
    private long aimStartTime = 0;
    private boolean isAimingAtPlayer = false;

    public BotSniper(BotListener listener) {
        this.listener = listener;
    }

    public void setDifficulty(Difficulty diff) {
        this.difficulty = diff;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void update(long dtMs, boolean playerDucked) {
        if (!isAlive) return;

        stateTimer += dtMs;
        if (stateTimer >= nextActionTime) {
            stateTimer = 0;
            decideNextMove();
        }

        // If bot is currently aiming at player, count down to firing
        if (isAimingAtPlayer) {
            aimStartTime += dtMs;
            long aimTargetTime = (difficulty == Difficulty.EASY) ? 1600 : (difficulty == Difficulty.HARD) ? 650 : 1100;
            if (aimStartTime >= aimTargetTime) {
                isAimingAtPlayer = false;
                fireAtPlayer(playerDucked);
            }
        }
    }

    private void decideNextMove() {
        int roll = rng.nextInt(100);
        if (roll < 30) {
            // Duck into cover
            currentStance = Stance.DUCK;
            isScoping = false;
            isAimingAtPlayer = false;
            nextActionTime = 1000 + rng.nextInt(1500);
        } else if (roll < 55) {
            // Change position
            coverSlot = rng.nextInt(3);
            currentStance = (rng.nextBoolean()) ? Stance.PEEK_LEFT : Stance.PEEK_RIGHT;
            isScoping = true;
            startAiming();
            nextActionTime = 2000 + rng.nextInt(1500);
        } else {
            // Stand and scope
            currentStance = Stance.STAND;
            isScoping = true;
            startAiming();
            nextActionTime = 2200 + rng.nextInt(1200);
        }

        if (listener != null) {
            listener.onBotMoved(coverSlot, currentStance, isScoping);
        }
    }

    private void startAiming() {
        if (currentStance != Stance.DUCK) {
            isAimingAtPlayer = true;
            aimStartTime = 0;
        }
    }

    private void fireAtPlayer(boolean playerDucked) {
        if (currentStance == Stance.DUCK) return;

        boolean hit = false;
        boolean headshot = false;

        int hitAccuracy = (difficulty == Difficulty.EASY) ? 45 : (difficulty == Difficulty.HARD) ? 88 : 70;
        int headshotAccuracy = (difficulty == Difficulty.EASY) ? 20 : (difficulty == Difficulty.HARD) ? 55 : 35;

        if (!playerDucked) {
            hit = rng.nextInt(100) < hitAccuracy;
            if (hit) {
                headshot = rng.nextInt(100) < headshotAccuracy;
            }
        } else {
            // Player is safely in cover!
            hit = false;
        }

        if (listener != null) {
            listener.onBotShoot(hit, headshot);
        }

        // Bot ducks after firing to reload
        currentStance = Stance.DUCK;
        isScoping = false;
        isAimingAtPlayer = false;
        nextActionTime = 1500 + rng.nextInt(1000);
        if (listener != null) {
            listener.onBotMoved(coverSlot, currentStance, isScoping);
        }
    }

    public void kill() {
        isAlive = false;
        isAimingAtPlayer = false;
        currentStance = Stance.DUCK;
        isScoping = false;
    }

    public void respawn() {
        isAlive = true;
        coverSlot = rng.nextInt(3);
        currentStance = Stance.STAND;
        stateTimer = 0;
        nextActionTime = 1200;
        if (listener != null) {
            listener.onBotMoved(coverSlot, currentStance, isScoping);
        }
    }

    public int getCoverSlot() { return coverSlot; }
    public Stance getCurrentStance() { return currentStance; }
    public boolean isScoping() { return isScoping; }
    public boolean isAlive() { return isAlive; }
    public boolean isAimingAtPlayer() { return isAimingAtPlayer; }
}
