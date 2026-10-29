# Installation (sideload, no Play Store)

Document this in `docs/install.md` with screenshots later.

## Enable Developer Mode on Google TV / Android TV

1. **Settings → System → About**
2. Scroll to **Android TV OS build** and press OK on it **7 times** until "You are now a developer" appears.
3. Go back to **Settings → System → Developer options**.

## Option A: Wireless ADB from a computer (recommended)

**Google TV / Android TV 11+ (wireless debugging):**
1. Developer options → turn on **Wireless debugging** → **Pair device with pairing code**. Note the IP:port and code shown.
2. On your computer (with Android platform-tools installed):
   ```bash
   adb pair <TV_IP>:<PAIR_PORT>      # enter the pairing code
   adb connect <TV_IP>:<CONNECT_PORT>  # port shown on the Wireless debugging screen
   adb install -r camgrid-tv.apk
   ```

**Older devices (network debugging):**
1. Developer options → turn on **USB debugging** / **Network debugging**.
2. ```bash
   adb connect <TV_IP>:5555
   # Accept the "Allow debugging?" prompt on the TV
   adb install -r camgrid-tv.apk
   ```

## Option B: Without a computer

1. Install a file downloader app on the TV from the Play Store.
2. Settings → Apps → Security & restrictions → **Unknown sources** → allow that app.
3. Download the APK from the project's GitHub Releases page and install it.

## Updating

`adb install -r` with the new APK, or download it again. Layouts and the session are kept.

## After installing

Turn off Wireless/USB debugging when you're done. The app does not need it to run.

---


> Screenshots to be added in Phase 5.

## First start

1. Open **CamGrid TV**. The sign-in screen shows an address like `http://192.168.x.x:8743` and a
   6-digit code.
2. On a phone or computer on the same Wi-Fi, open the address, enter the code, then your
   BitVision email, password and the digits in the picture.
3. Your cameras open in a 2×2 grid. Hold OK on a tile for options, or use *Edit layout from phone
   or computer* to set up your grid from the phone.
