package com.example.snipe;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

/** JOIN SCREEN: type the Room ID shown on the host's phone. */
public class JoinActivity extends AppCompatActivity {

    private EditText etRoomId;
    private Button btnJoin;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join);
        Utils.fullscreen(this);

        etRoomId = findViewById(R.id.etRoomId);
        btnJoin = findViewById(R.id.btnJoin);
        tvStatus = findViewById(R.id.tvStatus);

        btnJoin.setOnClickListener(v -> join());
    }

    private void join() {
        final String code = etRoomId.getText().toString().trim();
        final String ip = Utils.decodeRoom(code);
        if (code.isEmpty() || ip == null) {
            etRoomId.setError("Invalid Room ID");
            return;
        }

        btnJoin.setEnabled(false);
        tvStatus.setText("CONNECTING...");

        new Thread(() -> {
            try {
                NetworkManager.get().join(ip);
                runOnUiThread(() -> {
                    Intent i = new Intent(this, RoomActivity.class);
                    i.putExtra("host", false);
                    i.putExtra("roomId", code.toUpperCase());
                    startActivity(i);
                    finish();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    tvStatus.setText("COULD NOT CONNECT\n" + e.getMessage());
                    btnJoin.setEnabled(true);
                });
            }
        }).start();
    }
}
