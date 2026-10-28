package com.example.snipe;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/** Small helpers shared by all screens. */
public class Utils {

    /** Hide action bar + status/navigation bars (full-screen game look). Call AFTER setContentView. */
    public static void fullscreen(Activity a) {
        if (a instanceof AppCompatActivity && ((AppCompatActivity) a).getSupportActionBar() != null) {
            ((AppCompatActivity) a).getSupportActionBar().hide();
        }
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(a.getWindow(), a.getWindow().getDecorView());
        c.hide(WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    // ---------- player name (saved on the phone) ----------
    public static String getName(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("snipe", Context.MODE_PRIVATE);
        return sp.getString("name", "PLAYER");
    }

    public static void setName(Context ctx, String name) {
        name = name.trim().toUpperCase();
        if (name.isEmpty()) name = "PLAYER";
        if (name.length() > 10) name = name.substring(0, 10);
        ctx.getSharedPreferences("snipe", Context.MODE_PRIVATE).edit().putString("name", name).apply();
    }

    // ---------- Room ID  <->  IP address ----------
    // Turns 192.168.43.1 into a short code like "1K9X2Z" (and back), so players type a Room ID.

    public static String encodeRoom(String ip) {
        try {
            String[] p = ip.split("\\.");
            if (p.length != 4) return ip;
            long v = 0;
            for (String s : p) v = (v << 8) | (Integer.parseInt(s) & 0xFF);
            return Long.toString(v, 36).toUpperCase();
        } catch (NumberFormatException e) {
            return ip;
        }
    }

    /** Returns the IP for a Room ID. A raw IP (with dots) is also accepted. Null if invalid. */
    public static String decodeRoom(String code) {
        code = code.trim();
        if (code.contains(".")) return code;
        try {
            long v = Long.parseLong(code.toLowerCase(), 36);
            return ((v >> 24) & 255) + "." + ((v >> 16) & 255) + "." + ((v >> 8) & 255) + "." + (v & 255);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** This phone's Wi-Fi / hotspot IPv4 address, or null if not found. */
    public static String getLocalIp() {
        try {
            String fallback = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp()) continue;
                String name = ni.getName().toLowerCase();
                boolean isWifiOrAp = name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan") || name.contains("p2p");
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (isWifiOrAp) {
                            return ip;
                        }
                        if (fallback == null) fallback = ip;
                    }
                }
            }
            return fallback;
        } catch (Exception ignored) { }
        return null;
    }
}
