# SNIPE 1v1 - First-Person Sniper Duel (Android Studio - Java)

An arcade-inspired **1v1 First-Person Sniper Game** built natively in Java for Android Studio. Features peer-to-peer room matchmaking via short room codes, customizable win conditions (3, 5, or 10 kills), tactical cover mechanics (ducking & peeking), realistic bolt-action sniper rifle physics with optic zoom, and procedural 16-bit sound synthesis.

---

## 🎮 Game Features

1. **First-Person Sniper View & Tactical Combat**:
   - **Smooth Touch Aiming**: Drag left/center screen to pan and aim your sniper rifle across the arena.
   - **Optic Zoom (SCOPE)**: Toggle 3.5x sniper optic with illuminated red-dot, mil-dot crosshairs, and realistic breathing sway.
   - **Steady Aim**: Hold breath with the `[STEADY]` button for pinpoint precision shots.
   - **Bolt-Action Cycling & Recoil**: Heavy weapon recoil, muzzle flash, brass shell ejection, and bolt chambering cooldown.
   - **Cover & Peeking**: Tap `[DUCK]` to drop safely behind your concrete parapet (protects you from enemy fire). Tap `[< PEEK]` and `[PEEK >]` to peek out from cover!
   - **Hit Detection**: Instant-kill **HEADSHOTs** and body hits with floating pixel damage text and hit markers.
   - **Enemy Scope Glint**: Notice the warning lens glint when the enemy sniper is aiming right at you!

2. **Room Match System (1v1 LAN / Wi-Fi / Hotspot)**:
   - **Host Room**: Creates a peer-to-peer room, generates a short 5-6 character Room ID (derived from the local IP).
   - **Win Condition Selector**: Host chooses **3 Kills**, **5 Kills**, or **10 Kills**; dynamically synchronized to guest.
   - **Join Room**: Guest types the Room ID or IP to connect instantly over local Wi-Fi or mobile hotspot.
   - **Live Lobby**: Shows Player 1 vs Player 2 status and unlocks `[START]` when both players are connected.
   - **Tap to Copy**: Tap the Room ID badge to copy the code to the clipboard.

3. **Solo AI Practice Mode**:
   - Tap `[PLAY ONLINE]` -> `Practice vs AI Sniper` to play immediately on a single device without needing a second phone.
   - AI bot moves between cover spots, ducks, peeks, aims with realistic human delay, and fires back!

4. **Procedural Retro Sound Effects**:
   - Real-time PCM audio synthesis using Android `AudioTrack`:
     - Loud sniper rifle blast & sub-bass kick
     - Metallic bolt-action cycling click
     - Arcade headshot bell chime
     - Scope zoom whoosh
     - Victory & defeat fanfares
   - Zero external audio files required!

---

## 📁 Project Structure

```
SniperDuel_v2/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/snipe/
│       │   ├── MainActivity.java      # Main Menu & Player Card
│       │   ├── RoomActivity.java      # Lobby, Host/Guest Sync, Kills Selector
│       │   ├── JoinActivity.java      # Enter Room ID & Connect
│       │   ├── GameActivity.java      # Match Controller & HUD
│       │   ├── SniperGameView.java    # 60 FPS Canvas First-Person Arena
│       │   ├── BotSniper.java         # AI Opponent for Practice Mode
│       │   ├── NetworkManager.java    # Socket LAN Multiplayer Engine
│       │   ├── SoundEffects.java      # Procedural 16-bit Sound Synthesizer
│       │   └── Utils.java             # Fullscreen helper & Room Code codec
│       └── res/
│           ├── font/press_start_2p.ttf # Classic pixel font
│           ├── layout/                # UI screens (Main, Room, Join, Game)
│           ├── drawable/              # Pixelated bevel buttons & cards
│           └── values/                # Styles, colors, themes, strings
├── build.gradle                       # Root Gradle build script
├── settings.gradle                    # Project settings
└── gradle.properties
```

---

## 🚀 How to Run in Android Studio

1. Launch **Android Studio**.
2. Select **Open** (or `File` > `Open...`) and choose the `SniperDuel_v2` directory.
3. Allow Gradle to sync dependencies (`androidx.appcompat` and `material`).
4. Connect an Android phone via USB (or start an Android Virtual Device / Emulator in landscape).
5. Click the green **Run** button (`Shift + F10`).

---

## 📶 How to Play 1v1 Room Match (Two Devices)

1. Connect both phones to the **same Wi-Fi network**, OR turn on **Mobile Hotspot** on Phone 1 and connect Phone 2 to it.
2. **Phone 1 (Host)**:
   - Tap **CREATE ROOM**.
   - Select win condition: **3 Kills**, **5 Kills**, or **10 Kills**.
   - View your Room Code (e.g. `1K9X2Z`) in the top-right corner.
3. **Phone 2 (Guest)**:
   - Tap **JOIN ROOM**.
   - Type Phone 1's Room Code and tap **JOIN ROOM**.
4. Both player names will light up in the lobby.
5. Host taps **START** to begin the sniper duel!
