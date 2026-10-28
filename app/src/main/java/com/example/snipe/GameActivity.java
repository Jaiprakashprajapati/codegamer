package com.example.snipe;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * GameActivity: Orchestrates the 1v1 First-Person Sniper Match.
 * Supports both LAN Wi-Fi / Hotspot Room Matches and Solo AI Practice Mode.
 */
public class GameActivity extends AppCompatActivity {

    private SniperGameView gameView;
    private TextView tvPlayerScore, tvEnemyScore, tvGoal, tvStance;
    private Button btnFire, btnScope, btnSteady, btnDuck, btnPeekLeft, btnPeekRight;
    private View matchOverPanel;
    private Button btnRematch, btnLeave, btnExit;

    private boolean isBotMode = false;
    private boolean isHost = true;
    private String myName = "YOU";
    private String opponentName = "ENEMY";
    private int targetKills = 5;

    private BotSniper bot;
    private final Handler botHandler = new Handler(Looper.getMainLooper());
    private Runnable botLoop;

    private final NetworkManager nm = NetworkManager.get();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        Utils.fullscreen(this);

        Intent intent = getIntent();
        targetKills = intent.getIntExtra("kills", 5);
        isHost = intent.getBooleanExtra("isHost", true);
        isBotMode = intent.getBooleanExtra("isBotMode", !nm.isConnected());

        myName = intent.getStringExtra("myName");
        if (myName == null || myName.isEmpty()) myName = Utils.getName(this);

        opponentName = intent.getStringExtra("opponentName");
        if (opponentName == null || opponentName.isEmpty()) {
            opponentName = isBotMode ? "BOT_SNIPER" : "OPPONENT";
        }

        bindViews();
        setupGameView();
        setupControls();

        if (isBotMode) {
            setupBotMode();
        } else {
            setupNetworkMode();
        }
    }

    private void bindViews() {
        gameView = findViewById(R.id.gameView);
        tvPlayerScore = findViewById(R.id.tvPlayerScore);
        tvEnemyScore = findViewById(R.id.tvEnemyScore);
        tvGoal = findViewById(R.id.tvGoal);
        tvStance = findViewById(R.id.tvStance);

        btnFire = findViewById(R.id.btnFire);
        btnScope = findViewById(R.id.btnScope);
        btnSteady = findViewById(R.id.btnSteady);
        btnDuck = findViewById(R.id.btnDuck);
        btnPeekLeft = findViewById(R.id.btnPeekLeft);
        btnPeekRight = findViewById(R.id.btnPeekRight);
        btnExit = findViewById(R.id.btnExit);

        matchOverPanel = findViewById(R.id.matchOverPanel);
        btnRematch = findViewById(R.id.btnRematch);
        btnLeave = findViewById(R.id.btnLeave);

        tvPlayerScore.setText(myName + ": 0");
        tvEnemyScore.setText(opponentName + ": 0");
        tvGoal.setText("TARGET: " + targetKills + " KILLS");
    }

    private void setupGameView() {
        gameView.setTargetKills(targetKills);
        gameView.setGameEventListener(new SniperGameView.GameEventListener() {
            @Override
            public void onPlayerFired(float impactX, float impactY, boolean headshot, boolean bodyHit) {
                if (!isBotMode && nm.isConnected()) {
                    nm.send("FIRE:" + (headshot ? "HEAD" : bodyHit ? "BODY" : "MISS"));
                }
            }

            @Override
            public void onPlayerStanceChanged(BotSniper.Stance stance) {
                updateStanceLabel(stance);
                if (!isBotMode && nm.isConnected()) {
                    nm.send("STANCE:" + stance.name());
                }
            }

            @Override
            public void onPlayerScopeChanged(boolean isScoping) {
                btnSteady.setVisibility(isScoping ? View.VISIBLE : View.GONE);
                if (!isBotMode && nm.isConnected()) {
                    nm.send("SCOPE:" + isScoping);
                }
            }

            @Override
            public void onPlayerDied() {
                updateStanceLabel(BotSniper.Stance.STAND);
            }

            @Override
            public void onScoreUpdated(int p1Kills, int p2Kills) {
                runOnUiThread(() -> {
                    tvPlayerScore.setText(myName + ": " + p1Kills);
                    tvEnemyScore.setText(opponentName + ": " + p2Kills);
                });
            }

            @Override
            public void onMatchEnded(boolean victory) {
                runOnUiThread(() -> matchOverPanel.setVisibility(View.VISIBLE));
            }
        });
    }

    private void setupControls() {
        btnFire.setOnClickListener(v -> gameView.fireWeapon());

        btnScope.setOnClickListener(v -> gameView.toggleScope());

        btnSteady.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                gameView.setSteady(true);
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                gameView.setSteady(false);
            }
            return true;
        });

        btnDuck.setOnClickListener(v -> {
            if (gameView.getPlayerStance() == BotSniper.Stance.DUCK) {
                gameView.setStance(BotSniper.Stance.STAND);
            } else {
                gameView.setStance(BotSniper.Stance.DUCK);
            }
        });

        btnPeekLeft.setOnClickListener(v -> {
            if (gameView.getPlayerStance() == BotSniper.Stance.PEEK_LEFT) {
                gameView.setStance(BotSniper.Stance.STAND);
            } else {
                gameView.setStance(BotSniper.Stance.PEEK_LEFT);
            }
        });

        btnPeekRight.setOnClickListener(v -> {
            if (gameView.getPlayerStance() == BotSniper.Stance.PEEK_RIGHT) {
                gameView.setStance(BotSniper.Stance.STAND);
            } else {
                gameView.setStance(BotSniper.Stance.PEEK_RIGHT);
            }
        });

        btnExit.setOnClickListener(v -> confirmExit());

        btnRematch.setOnClickListener(v -> {
            matchOverPanel.setVisibility(View.GONE);
            gameView.resetMatch();
            if (isBotMode) {
                bot.respawn();
            } else {
                nm.send("REMATCH");
            }
        });

        btnLeave.setOnClickListener(v -> finish());
    }

    private void updateStanceLabel(BotSniper.Stance stance) {
        runOnUiThread(() -> {
            switch (stance) {
                case DUCK:
                    tvStance.setText("STANCE: IN COVER (DUCKED)");
                    tvStance.setTextColor(0xFF3DDC6E);
                    break;
                case PEEK_LEFT:
                    tvStance.setText("STANCE: PEEKING LEFT");
                    tvStance.setTextColor(0xFFFFE14D);
                    break;
                case PEEK_RIGHT:
                    tvStance.setText("STANCE: PEEKING RIGHT");
                    tvStance.setTextColor(0xFFFFE14D);
                    break;
                default:
                    tvStance.setText("STANCE: STANDING (EXPOSED)");
                    tvStance.setTextColor(0xFF29D3F5);
                    break;
            }
        });
    }

    // ---------------- Solo AI Bot Mode ----------------

    private void setupBotMode() {
        bot = new BotSniper(new BotSniper.BotListener() {
            @Override
            public void onBotShoot(boolean hitPlayer, boolean isHeadshot) {
                runOnUiThread(() -> gameView.triggerOpponentShot(hitPlayer, isHeadshot));
            }

            @Override
            public void onBotMoved(int coverSlot, BotSniper.Stance stance, boolean isScoping) {
                runOnUiThread(() -> gameView.updateOpponentState(coverSlot, stance, isScoping));
            }
        });

        botLoop = new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    boolean playerDucked = (gameView.getPlayerStance() == BotSniper.Stance.DUCK);
                    bot.update(50, playerDucked);
                    botHandler.postDelayed(this, 50);
                }
            }
        };
        botHandler.postDelayed(botLoop, 50);
    }

    // ---------------- 1v1 Networked Multiplayer Mode ----------------

    private void setupNetworkMode() {
        nm.setListener(new NetworkManager.Listener() {
            @Override
            public void onMessage(String msg) {
                runOnUiThread(() -> handleNetworkMessage(msg));
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        Toast.makeText(GameActivity.this, "Opponent disconnected!", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        });
    }

    private void handleNetworkMessage(String msg) {
        try {
            if (msg.startsWith("FIRE:")) {
                String hitType = msg.substring(5);
                boolean isHit = "HEAD".equals(hitType) || "BODY".equals(hitType);
                boolean isHeadshot = "HEAD".equals(hitType);
                gameView.triggerOpponentShot(isHit, isHeadshot);
            } else if (msg.startsWith("STANCE:")) {
                BotSniper.Stance s = BotSniper.Stance.valueOf(msg.substring(7));
                gameView.updateOpponentState(1, s, false);
            } else if (msg.startsWith("SCOPE:")) {
                boolean scoping = Boolean.parseBoolean(msg.substring(6));
                gameView.updateOpponentState(1, BotSniper.Stance.STAND, scoping);
            } else if (msg.startsWith("REMATCH")) {
                matchOverPanel.setVisibility(View.GONE);
                gameView.resetMatch();
                Toast.makeText(this, "Rematch started!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {}
    }

    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle("Exit Match")
                .setMessage("Are you sure you want to leave the duel?")
                .setPositiveButton("Exit", (d, w) -> finish())
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (botLoop != null) {
            botHandler.removeCallbacks(botLoop);
        }
        if (!isBotMode) {
            nm.close();
        }
    }
}
