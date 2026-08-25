# Firmly Planted

Firmly Planted is an Android app for memorizing whole chapters or books of the Bible, using a
graduated cumulative-review method (new verses added a few at a time; everything already
learned reviewed on a lengthening schedule), inspired by Scripta Memoria and Andy Davis's
*How to Memorize Scripture for Life*.

Text sources: the ESV API (`api.esv.org`) by default, plus the Westminster Leningrad Codex
(Hebrew), the SBL Greek New Testament, and the Vietnamese Bible (1925) via fetch.bible, with
the rest of fetch.bible's catalog available behind "More". See [LICENSING.md](LICENSING.md) for
how each source's terms are handled — including why only a small rolling window of verses is
ever cached on-device, and why that cache clears when a project is marked complete.

## Building it

You'll need [Android Studio](https://developer.android.com/studio) (which bundles a matching
JDK and Gradle) — this repo doesn't commit a Gradle wrapper jar, so open the project folder in
Android Studio first and let it sync; Studio will fetch Gradle 8.9 automatically per
`gradle/wrapper/gradle-wrapper.properties`.

1. **Get a free ESV API key.** Sign up at https://api.esv.org/ (non-commercial use), then find
   your key on your account's API page.
2. **Add it locally.** Copy `local.properties.example` to `local.properties` (already
   gitignored) and fill in `ESV_API_KEY=...` and your Android SDK path (`sdk.dir=...` — Android
   Studio will usually fill this in for you on first sync).
3. **Open in Android Studio**: File → Open → select this folder. Let Gradle sync finish.
4. **Run it**: pick a device/emulator in the toolbar and hit Run (▶). First launch needs
   internet access to fetch translation metadata and any verses you start memorizing.

There's no CI/emulator available in the environment this project was scaffolded in, so treat
the first Android Studio build as the first real compile check — see the code comments (search
for "verify" / "worth confirming") for the handful of spots called out as needing that first
real-world check, mainly around the exact Material3 dropdown API version and the fetch.bible
book-code coverage for less common "More" catalog entries.

## Installing it on your own phone

- **Fastest — USB debug run**: enable Developer Options on your phone (Settings → About phone →
  tap "Build number" 7 times), then enable USB debugging inside Developer Options. Plug the
  phone in, allow the debugging prompt, pick it as the target device in Android Studio, and hit
  Run. The app installs and launches directly.
- **Sideload a built APK**: in Android Studio, Build → Build App Bundle(s)/APK(s) → Build
  APK(s), then transfer the resulting `.apk` (in `app/build/outputs/apk/debug/`) to your phone
  (email, USB, cloud drive) and open it — you'll need to allow "install unknown apps" for
  whichever app you used to open it.
- **Play Console internal testing** (no public listing needed): once you have a Play Console
  account (see below), upload a build to the "Internal testing" track and add your own Google
  account as a tester — you can then install it via a private Play Store link, which also
  covers auto-updates going forward.

## Publishing to the Google Play Store

1. Create a Google Play Console developer account (one-time $25 fee, a Google account you
   control).
2. In Android Studio, generate a signed release build: Build → Generate Signed Bundle/APK →
   Android App Bundle, creating (and safely backing up) a signing key — or enable Play App
   Signing, which lets Google manage the signing key for you after you upload an initial upload
   key.
3. Create the app listing in Play Console: title, description, screenshots, icon, content
   rating questionnaire, and a **privacy policy URL** (required even for a simple app — it
   should disclose that the app makes network requests to api.esv.org and fetch.bible to fetch
   Scripture text, and whether you add any analytics/crash reporting later).
4. Fill out the Data Safety form describing what data the app collects (as scaffolded, it
   collects none beyond what's needed to fetch Bible text — no accounts, no analytics).
5. Upload the `.aab` to a testing track first (Internal → Closed/Open → Production), and submit
   for review.
6. **Remember the ESV API's non-commercial restriction**: as long as this app is free with no
   ads, you're within the free API key's terms. If you ever want to monetize it, contact
   Crossway about a commercial ESV license before doing so — don't add ads/paid distribution
   against the free key.

See [LICENSING.md](LICENSING.md) for the full breakdown of what each text source allows, and
in particular the note on the Vietnamese 1925 text worth a second look before a public release.
