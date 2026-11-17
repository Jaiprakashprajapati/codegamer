package com.example.snipe;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

/**
 * ROOM SCREEN (shared by host and guest)
 *
 * Host : creates the room, waits for an opponent, picks 3/5/10 kills, presses START.
 * Guest: sees the host's choice (can't change it) and waits for the host to start.
 *
 * Messages:  NAME:<name>   KILLS:<n>   START:<n>
 * Handshake: guest sends NAME  ->  host replies with NAME + KILLS  ->  START button unlocks.
 */
public class RoomActivity extends AppCompatActivity {

    private boolean isHost;
    private String myName;
    private String opponentName = "OPPONENT";
    private boolean startingGame = false;

    private TextView tvRoomId, tvP1, tvP2, tvHint;
    private RadioGroup rgKills;
    private Button btnStart;

    private final NetworkManager nm = NetworkManager.get();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);
        Utils.fullscreen(this);

        isHost = getIntent().getBooleanExtra("host", true);
        myName = Utils.getName(this);

        tvRoomId = findViewById(R.id.tvRoomId);
        tvP1 = findViewById(R.id.tvP1);
        tvP2 = findViewById(R.id.tvP2);
        tvHint = findViewById(R.id.tvHint);
        rgKills = findViewById(R.id.rgKills);
        btnStart = findViewById(R.id.btnStart);
        Button btnBack = findViewById(R.id.btnBack);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        tvRoomId.setOnClickListener(v -> {
            String raw = tvRoomId.getText().toString().replace("ROOM ID:", "").trim();
            if (!raw.isEmpty() && !raw.contains("-")) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Snipe Room ID", raw));
                    Toast.makeText(this, "Room Code " + raw + " copied!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (isHost) setupHost(); else setupGuest();
    }

    // ---------------- HOST ----------------
    private void setupHost() {
        String ip = Utils.getLocalIp();
        tvRoomId.setText("ROOM ID: " + (ip == null ? "----" : Utils.encodeRoom(ip)));
        tvP1.setText(myName);
        tvP2.setText("WAITING...");
        tvHint.setText(ip == null ? "TURN ON WI-FI OR HOTSPOT FIRST"
                                  : "SHARE THE ROOM ID WITH YOUR FRIEND (TAP TO COPY)");
        btnStart.setEnabled(false);

        rgKills.setOnCheckedChangeListener((g, id) -> nm.send("KILLS:" + getKills()));
        btnStart.setOnClickListener(v -> {
            int k = getKills();
            nm.send("START:" + k);
            openGame(k);
        });

        nm.setListener(listener);
        new Thread(() -> {
            try {
                nm.host();               // waits here until a friend joins
                nm.startListening();
            } catch (IOException e) {
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        Toast.makeText(this, "Room closed", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }
        }).start();
    }

    // ---------------- GUEST ----------------
    private void setupGuest() {
        String code = getIntent().getStringExtra("roomId");
        tvRoomId.setText("ROOM ID: " + (code != null ? code : "----"));
        tvP1.setText("HOST");
        tvP2.setText(myName);
        tvP2.setBackgroundResource(R.drawable.px_card_red);
        tvHint.setText("WAITING FOR HOST...");
        btnStart.setVisibility(View.GONE);
        for (int i = 0; i < rgKills.getChildCount(); i++) {
            rgKills.getChildAt(i).setEnabled(false);   // only the host chooses
        }

        nm.setListener(listener);
        nm.startListening();
        nm.send("NAME:" + myName);       // introduce myself to the host
    }

    // ---------------- messages ----------------
    private final NetworkManager.Listener listener = new NetworkManager.Listener() {
        @Override
        public void onMessage(String msg) {
            try {
                if (msg.startsWith("NAME:")) {
                    final String n = msg.substring(5);
                    runOnUiThread(() -> onOpponentName(n));
                } else if (msg.startsWith("KILLS:")) {
                    final int k = Integer.parseInt(msg.substring(6));
                    runOnUiThread(() -> selectKills(k));
                } else if (msg.startsWith("START:")) {
                    final int k = Integer.parseInt(msg.substring(6));
                    runOnUiThread(() -> openGame(k));
                }
            } catch (NumberFormatException ignored) { }
        }

        @Override
        public void onDisconnected() {
            runOnUiThread(() -> {
                if (!startingGame && !isFinishing()) {
                    Toast.makeText(RoomActivity.this, "Opponent left the room", Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        }
    };

    private void onOpponentName(String name) {
        opponentName = name;
        if (isHost) {
            tvP2.setText(name);
            tvP2.setBackgroundResource(R.drawable.px_card_red);
            nm.send("NAME:" + myName);
            nm.send("KILLS:" + getKills());
            btnStart.setEnabled(true);
            tvHint.setText("PICK KILLS, THEN PRESS START");
        } else {
            tvP1.setText(name);
            tvHint.setText("WAITING FOR HOST TO START...");
        }
    }

    private int getKills() {
        int id = rgKills.getCheckedRadioButtonId();
        if (id == R.id.rb5) return 5;
        if (id == R.id.rb10) return 10;
        return 3;
    }

    private void selectKills(int k) {
        rgKills.check(k == 5 ? R.id.rb5 : k == 10 ? R.id.rb10 : R.id.rb3);
    }

    private void openGame(int kills) {
        startingGame = true;
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra("kills", kills);
        i.putExtra("isHost", isHost);
        i.putExtra("myName", myName);
        i.putExtra("opponentName", opponentName);
        i.putExtra("isBotMode", false);
        startActivity(i);
        finish();     // when the match ends you return to the home screen
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!startingGame) {         // left the room -> cancel / disconnect
            nm.setListener(null);
            nm.close();
        }
    }
}
