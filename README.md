# Ebb (personal build)

Cycle + anniversary tracker for Android. Partner mode, meds-aware predictions, Korean day count (100일) and Japanese month count (ヶ月記念日), reminders, home-screen widget, Google Calendar sync.

Every push builds a signed APK in **Actions → Build APK → Artifacts → ebb-apk**.

- Web app: `app/src/main/assets/index.html` (runs standalone in a browser too)
- Native: `app/src/main/java/com/kyu/ebb/` (WebView shell, reminders, widget, calendar)
- `app/ebb-personal.keystore` is a personal signing key so updates install over each other. Keep this repo private.
