package com.example.snipe;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/** HOME SCREEN */
public class MainActivity extends AppCompatActivity {

    private TextView tvPlayerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Utils.fullscreen(this);

        tvPlayerName = findViewById(R.id.tvPlayerName);

        findViewById(R.id.playerCard).setOnClickListener(v -> showNameDialog());

        findViewById(R.id.btnPlayOnline).setOnClickListener(v -> showOnlineOrPracticeDialog());

        findViewById(R.id.btnCreate).setOnClickListener(v -> {
            Intent i = new Intent(this, RoomActivity.class);
            i.putExtra("host", true);
            startActivity(i);
        });

        findViewById(R.id.btnJoin).setOnClickListener(v ->
                startActivity(new Intent(this, JoinActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvPlayerName.setText(Utils.getName(this));
    }

    private void showOnlineOrPracticeDialog() {
        String[] options = {"Practice vs AI Sniper (Solo 1v1)", "Online Global Matchmaking (Coming Soon)"};
        new AlertDialog.Builder(this)
                .setTitle("Select Game Mode")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showKillGoalSelectorDialog();
                    } else {
                        Toast.makeText(this, "Global online multiplayer servers coming soon! Use Create Room / Join Room for 1v1 duels.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showKillGoalSelectorDialog() {
        String[] goals = {"3 Kills", "5 Kills", "10 Kills"};
        new AlertDialog.Builder(this)
                .setTitle("Win Condition")
                .setItems(goals, (dialog, which) -> {
                    int kills = (which == 0) ? 3 : (which == 1) ? 5 : 10;
                    Intent intent = new Intent(this, GameActivity.class);
                    intent.putExtra("kills", kills);
                    intent.putExtra("isHost", true);
                    intent.putExtra("isBotMode", true);
                    intent.putExtra("myName", Utils.getName(this));
                    intent.putExtra("opponentName", "AI_SNIPER");
                    startActivity(intent);
                })
                .show();
    }

    private void showNameDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        input.setText(Utils.getName(this));
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
                .setTitle("Your player name")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    Utils.setName(this, input.getText().toString());
                    tvPlayerName.setText(Utils.getName(this));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
