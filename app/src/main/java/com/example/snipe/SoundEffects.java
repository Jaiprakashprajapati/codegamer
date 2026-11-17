package com.example.snipe;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Procedural retro 16-bit sound synthesizer using AudioTrack.
 * Generates instant audio feedback with zero external sound files.
 */
public class SoundEffects {

    public static boolean muted = false;
    private static final int SAMPLE_RATE = 22050;
    private static final ExecutorService audioPool = Executors.newCachedThreadPool();
    private static final Random rng = new Random();

    public static void playSniperShot() {
        audioPool.execute(() -> {
            int numSamples = (int) (SAMPLE_RATE * 0.45); // 450 ms
            short[] buffer = new short[numSamples];
            for (int i = 0; i < numSamples; i++) {
                double t = (double) i / SAMPLE_RATE;
                double decay = Math.exp(-t * 12.0);

                // Initial explosive punch
                double noise = (rng.nextDouble() * 2.0 - 1.0) * Math.exp(-t * 22.0);

                // Deep low-frequency sub-bass boom
                double freq = 120.0 * Math.exp(-t * 18.0) + 38.0;
                double boom = Math.sin(2.0 * Math.PI * freq * t);

                double sample = (noise * 0.7 + boom * 0.5) * decay;
                sample = Math.max(-1.0, Math.min(1.0, sample));
                buffer[i] = (short) (sample * 32000);
            }
            playPcm(buffer);
        });
    }

    public static void playBoltAction() {
        audioPool.execute(() -> {
            int numSamples = (int) (SAMPLE_RATE * 0.35);
            short[] buffer = new short[numSamples];

            // Click 1 (pull back) around t=0.04
            int click1 = (int) (SAMPLE_RATE * 0.04);
            int click2 = (int) (SAMPLE_RATE * 0.22);

            for (int i = 0; i < numSamples; i++) {
                double sample = 0;
                if (i >= click1 && i < click1 + 400) {
                    double dt = (double) (i - click1) / SAMPLE_RATE;
                    sample += Math.sin(2 * Math.PI * 900 * dt) * Math.exp(-dt * 80);
                }
                if (i >= click2 && i < click2 + 600) {
                    double dt = (double) (i - click2) / SAMPLE_RATE;
                    sample += Math.sin(2 * Math.PI * 1400 * dt) * Math.exp(-dt * 90);
                }
                sample = Math.max(-1.0, Math.min(1.0, sample));
                buffer[i] = (short) (sample * 28000);
            }
            playPcm(buffer);
        });
    }

    public static void playHeadshot() {
        audioPool.execute(() -> {
            int numSamples = (int) (SAMPLE_RATE * 0.4);
            short[] buffer = new short[numSamples];
            for (int i = 0; i < numSamples; i++) {
                double t = (double) i / SAMPLE_RATE;
                double bell = Math.sin(2 * Math.PI * 2200 * t) * 0.6
                        + Math.sin(2 * Math.PI * 3300 * t) * 0.4;
                double decay = Math.exp(-t * 9.0);
                buffer[i] = (short) (bell * decay * 30000);
            }
            playPcm(buffer);
        });
    }

    public static void playHit() {
        audioPool.execute(() -> {
            int numSamples = (int) (SAMPLE_RATE * 0.15);
            short[] buffer = new short[numSamples];
            for (int i = 0; i < numSamples; i++) {
                double t = (double) i / SAMPLE_RATE;
                double thump = Math.sin(2 * Math.PI * 90 * t);
                double decay = Math.exp(-t * 25.0);
                buffer[i] = (short) (thump * decay * 28000);
            }
            playPcm(buffer);
        });
    }

    public static void playScope() {
        audioPool.execute(() -> {
            int numSamples = (int) (SAMPLE_RATE * 0.12);
            short[] buffer = new short[numSamples];
            for (int i = 0; i < numSamples; i++) {
                double t = (double) i / SAMPLE_RATE;
                double freq = 400 + 800 * (t / 0.12);
                double sample = Math.sin(2 * Math.PI * freq * t) * Math.exp(-t * 15.0);
                buffer[i] = (short) (sample * 16000);
            }
            playPcm(buffer);
        });
    }

    public static void playVictory() {
        audioPool.execute(() -> {
            double[] notes = {523.25, 659.25, 783.99, 1046.50}; // C5, E5, G5, C6
            int noteLen = (int) (SAMPLE_RATE * 0.12);
            int totalLen = noteLen * 4 + (int) (SAMPLE_RATE * 0.3);
            short[] buffer = new short[totalLen];

            for (int n = 0; n < notes.length; n++) {
                int start = n * noteLen;
                double freq = notes[n];
                int len = (n == 3) ? (noteLen + (int) (SAMPLE_RATE * 0.3)) : noteLen;
                for (int i = 0; i < len; i++) {
                    double t = (double) i / SAMPLE_RATE;
                    double decay = Math.exp(-t * 4.0);
                    double s = (Math.sin(2 * Math.PI * freq * t) + 0.3 * Math.sin(4 * Math.PI * freq * t)) * decay;
                    buffer[start + i] = (short) (Math.max(-1.0, Math.min(1.0, s)) * 26000);
                }
            }
            playPcm(buffer);
        });
    }

    public static void playDefeat() {
        audioPool.execute(() -> {
            double[] notes = {392.00, 349.23, 311.13, 261.63}; // G4, F4, D#4, C4
            int noteLen = (int) (SAMPLE_RATE * 0.18);
            short[] buffer = new short[noteLen * 4];

            for (int n = 0; n < notes.length; n++) {
                int start = n * noteLen;
                double freq = notes[n];
                for (int i = 0; i < noteLen; i++) {
                    double t = (double) i / SAMPLE_RATE;
                    double decay = Math.exp(-t * 5.0);
                    double s = Math.sin(2 * Math.PI * freq * t) * decay;
                    buffer[start + i] = (short) (s * 24000);
                }
            }
            playPcm(buffer);
        });
    }

    private static void playPcm(short[] buffer) {
        if (muted) return;
        try {
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(buffer.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();

            track.write(buffer, 0, buffer.length);
            track.play();
            // Release after playing
            new Thread(() -> {
                try {
                    Thread.sleep(buffer.length * 1000L / SAMPLE_RATE + 100);
                    track.release();
                } catch (Exception ignored) {}
            }).start();
        } catch (Exception ignored) {
        }
    }
}
