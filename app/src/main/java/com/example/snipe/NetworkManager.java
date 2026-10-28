package com.example.snipe;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Singleton managing peer-to-peer Wi-Fi / Hotspot / LAN socket connections
 * between the Host phone and Guest phone.
 */
public class NetworkManager {

    public static final int PORT = 8988;
    private static NetworkManager instance;

    public interface Listener {
        void onMessage(String msg);
        void onDisconnected();
    }

    private ServerSocket serverSocket;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private volatile Listener listener;
    private volatile boolean isRunning = false;

    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread listenThread;

    private NetworkManager() {}

    public static synchronized NetworkManager get() {
        if (instance == null) {
            instance = new NetworkManager();
        }
        return instance;
    }

    /**
     * Start hosting: listens on PORT and blocks until a peer joins.
     */
    public void host() throws IOException {
        close();
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(PORT));
        socket = serverSocket.accept();
        setupStreams();
    }

    /**
     * Join an existing host at the specified IP address.
     */
    public void join(String ip) throws IOException {
        close();
        socket = new Socket();
        socket.connect(new InetSocketAddress(ip, PORT), 5000);
        setupStreams();
    }

    private void setupStreams() throws IOException {
        socket.setTcpNoDelay(true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
        isRunning = true;
    }

    /**
     * Set the message/connection event listener.
     */
    public void setListener(Listener l) {
        this.listener = l;
    }

    /**
     * Start reading incoming messages in a background thread.
     */
    public void startListening() {
        if (listenThread != null && listenThread.isAlive()) {
            return;
        }
        isRunning = true;
        listenThread = new Thread(() -> {
            try {
                String line;
                while (isRunning && in != null && (line = in.readLine()) != null) {
                    final String msg = line;
                    Listener l = listener;
                    if (l != null) {
                        l.onMessage(msg);
                    }
                }
            } catch (IOException ignored) {
            } finally {
                if (isRunning) {
                    isRunning = false;
                    Listener l = listener;
                    if (l != null) {
                        l.onDisconnected();
                    }
                }
            }
        }, "SnipeNetworkListener");
        listenThread.start();
    }

    /**
     * Send a string message to the connected peer asynchronously.
     */
    public void send(String msg) {
        if (!isRunning || out == null) return;
        sendExecutor.execute(() -> {
            try {
                if (out != null) {
                    out.println(msg);
                    out.flush();
                }
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Check if socket is actively connected.
     */
    public boolean isConnected() {
        return isRunning && socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Close all sockets and streams cleanly.
     */
    public synchronized void close() {
        isRunning = false;
        try {
            if (out != null) out.close();
        } catch (Exception ignored) {}
        try {
            if (in != null) in.close();
        } catch (Exception ignored) {}
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (Exception ignored) {}

        socket = null;
        serverSocket = null;
        in = null;
        out = null;
    }
}
