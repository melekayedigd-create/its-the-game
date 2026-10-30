# Arcane Arena — Android + HC-05 Arduino controller

RPG arena fighter: 3 heroes (Knight, Mage, Ranger) against waves of bots and a boss every 5 waves.
Play with the on-screen joystick + buttons, or connect your Arduino Uno + HC-05: the touch controls
disappear and a small window at the bottom shows what each Arduino button does (and lights up when pressed).

## 1. Get the APK (pick one)

### A — GitHub Actions (no Android Studio needed)
1. Create a new empty GitHub repository and upload **everything in this folder** (including the hidden `.github` folder).
   Easiest: `git init && git add . && git commit -m "Arcane Arena" && git branch -M main && git remote add origin <your repo URL> && git push -u origin main`
2. Open the repo → **Actions** → "Build Arcane Arena APK" runs automatically (or press *Run workflow*).
3. When it is green, open the run → **Artifacts** → download `ArcaneArena-apk` → unzip → `app-debug.apk`.

### B — Android Studio
Open this folder (File → Open), wait for Gradle sync, then **Build → Build APK(s)**.
The APK is in `app/build/outputs/apk/debug/app-debug.apk`.

## 2. Install on the phone
Send `app-debug.apk` to the phone (WhatsApp, Drive, USB…), open it, allow "Install unknown apps" for the app you opened it with.

## 3. Arduino
1. Wire it as in `arduino/wiring.png`.
2. Upload `arduino/ArcaneArenaPad/ArcaneArenaPad.ino` (Arduino IDE → board "Arduino Uno").
3. On the phone: Settings → Bluetooth → pair **HC-05** (PIN `1234` or `0000`).
4. In the game tap **🔌 Connect Arduino** → pick HC-05 (allow the Bluetooth permission the first time).

## Arduino buttons
| Pin | Action |
|-----|--------|
| D2 / D3 / D4 / D5 | Up / Down / Left / Right |
| D6 | Attack (hold = keep attacking) · OK in menus |
| D7 | Skill 1 |
| D8 | Skill 2 (ultimate) |
| D9 | Dash / Blink / Roll |
| A0 | Swap hero |
| A1 | Pause |
On the title screen: Left/Right = choose starting hero, Attack = Play.

## Heroes
| Hero | Attack | Skill 1 | Skill 2 | Dash |
|------|--------|---------|---------|------|
| 🛡️ Knight | sword slash | Whirlwind (spin) | Earthquake (stun all around) | Charge (hits on the way) |
| 🔮 Mage | arcane bolt | Frost Nova (freeze + slow) | Meteor (big AoE + burn) | Blink (teleport) |
| 🏹 Ranger | fast arrows | Multishot (7 arrows) | Arrow Rain (zone) | Roll (invulnerable) |
Attacks auto-aim at the nearest bot. Resting heroes regenerate HP, so swap often. If a hero falls, the next one jumps in.
