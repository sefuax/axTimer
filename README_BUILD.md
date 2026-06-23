# AX Timer — Termux Build Guide
**Owner:** @axSaaFe | **Telegram:** @asklybux

---

## Prerequisites (Termux setup)

```bash
# 1. Update and install required packages
pkg update && pkg upgrade -y
pkg install -y openjdk-17 gradle wget unzip

# 2. Set JAVA_HOME
export JAVA_HOME=$PREFIX/opt/openjdk-17
export PATH=$JAVA_HOME/bin:$PATH

# Persist in shell config (optional)
echo 'export JAVA_HOME=$PREFIX/opt/openjdk-17' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

---

## Setup Android SDK

```bash
# 3. Download command-line tools
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools

wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip
mv cmdline-tools latest

# 4. Accept licenses and install SDK
export ANDROID_HOME=$HOME/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

sdkmanager --licenses  # type 'y' for all
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

---

## Build the APK

```bash
# 5. Navigate to the project
cd ~/axtimer

# 6. Download the Gradle wrapper jar (one-time)
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar

# 7. Make gradlew executable
chmod +x gradlew

# 8. Set SDK path
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 9. Build!
./gradlew assembleDebug
```

**Output APK:**
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Install on device

```bash
# Via ADB (USB debugging enabled on device)
adb install app/build/outputs/apk/debug/app-debug.apk

# Or copy to device storage and install manually
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/
```

---

## App Usage

1. **Launch** AX Timer → 3-second animated splash → Home screen
2. **Select** a session duration (5 min → 3 hours)
3. **Tap START SESSION** → grants overlay permission if needed
4. **Float widget appears** above all other apps
5. **While using any app** — watch the 5:00 countdown
6. **Press [1] then [2]** (or [2] then [1]) before countdown ends → +2 points, cycle resets
7. **Press [✅]** at any time to end the session and see your score
8. **Press [❌ CLOSE]** to stop everything

---

## Architecture

```
SplashActivity          Animated 3s splash, navigates to Home
HomeActivity            Duration selection, Start/Reset buttons
PermissionActivity      SYSTEM_ALERT_WINDOW permission flow
TimerService            Foreground service — orchestrator
  └─ TimerEngine        Drift-corrected countdown (SystemClock)
  └─ FloatingOverlay    WindowManager overlay widget
```

---

## Permissions

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw floating widget over other apps |
| `FOREGROUND_SERVICE` | Keep timer alive in background |
| `WAKE_LOCK` | Prevent CPU sleep during session |
| `POST_NOTIFICATIONS` | Show persistent timer notification |

---

*AX Timer — Built for productivity, engineered for reliability.*
