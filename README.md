# Firmly Planted

Firmly Planted is an Android and iPhone app for memorizing whole chapters or books of the Bible, using a
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

## Building it for iPhone

The app is Kotlin Multiplatform: everything except a thin Android shell (`app/`) and a thin
iOS shell (`iosApp/`) lives in `shared/`, with the UI written once in Compose Multiplatform.

1. You'll need a Mac with Xcode and a JDK (Android Studio's bundled one is fine), plus the same
   `local.properties` with your `ESV_API_KEY` as above — the key is compiled into both apps.
2. Open `iosApp/iosApp.xcodeproj` in Xcode, pick an iPhone simulator, and hit Run (▶). The
   "Compile Kotlin Framework" build phase runs Gradle to build `shared/` for you, so the first
   build takes a few minutes.
3. **On your own iPhone**: select the `iosApp` target → Signing & Capabilities, choose your
   Team (a free Apple ID works, but the app then stops launching after 7 days; the $99/year
   Apple Developer Program removes that and enables TestFlight/App Store), then pick your
   phone as the run destination.

If you set up the Xcode project again from scratch, it needs `-lsqlite3` in Other Linker Flags
and Dead Code Stripping on — Room's iOS framework won't link without them.

## Installing it on your own phone (Android)

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

See [LICENSING.md](LICENSING.md) for the full breakdown of what each text source allows, and
in particular the note on the Vietnamese 1925 text worth a second look before a public release.
