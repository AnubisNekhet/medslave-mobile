# medslave

An Android flashcard app for your own Anki decks. Import an `.apkg` and study it in Tinder-style swipes, a Flip style, or Reels Mode. The deck stays on your phone.

## Features

- **Import:** any Anki deck package (`.apkg`). Handles the old and new collection formats, zstd compression and big decks. You choose what each note type becomes (Basic, Cloze, Image occlusion, or Skip) and which field fills each slot. Known note types map themselves: AnKing, Anki's Cloze and Image Occlusion, Image Occlusion Enhanced, and PYQ-style MCQs. Files from a file manager or chat app open in the app with "Open with medslave".
- **Cards:** basic, cloze (including sequential clozes), image occlusion (Anki's, AnKing's one-by-one boxes, and Image Occlusion Enhanced), and MathJax.
- **Studying:** shuffled or ordered decks with resume. Missed cards come back 20–40 cards later.
- **Review styles:** Tinder (swipe right if you knew it, left if you didn't), Flip (swipe up or down), New Card Reveal animations, and five colour themes in light and dark.
- **Reels Mode:** the review screen dressed up as a reels feed, with likes, comments, Repost (= missed it), share and a song per card. The home screen becomes a social feed made from your deck. Each subject has its own accounts, which post card answers and share them as stories; new stories turn up every five minutes.
- **Your data:** a study activity heatmap and progress backup and restore as a JSON file.

The deck, progress and settings are stored inside the app, and nothing is uploaded. The only network use is Reels Mode's 30-second song previews and their cover art.

## Building

You need JDK 17 and the Android SDK (platform 35).

```sh
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # signed when keystore.properties exists (see below)
```

The SDK location goes in `local.properties` (`sdk.dir=…`) or in `ANDROID_HOME`.

### Signing

A release build is signed when `keystore.properties` exists next to `settings.gradle.kts`:

```
storeFile=release.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

Both files are gitignored. Keep a backup: Android installs an update over the old app only when it's signed with the same key.

### CI

`.github/workflows/build.yml` builds every push and pull request and attaches the APK to the run as an artifact. To get signed release APKs, add these repository secrets: `KEYSTORE_BASE64` (`base64 -i release.keystore`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Without them, CI builds a debug APK.

### Releases

`.github/workflows/release.yml` publishes a GitHub Release with the signed APK whenever a version tag is pushed. The tag must match `versionName` in `app/build.gradle.kts`, and it needs the signing secrets above.

```sh
git tag v1.3 && git push origin v1.3
```

## Code

- `app/src/main/java/pizza/kulhad/medslave/`
  - `App.kt`: app state (`Rs`). The study session, card motion, progress backup, and deck import and delete.
  - `Importer.kt`: `.apkg` → deck.
  - `Deck.kt`: the stored deck (`deck.db` plus `media/` in the app's files).
  - `Order.kt`: the seeded shuffle and resume.
  - `Store.kt`: progress and settings.
  - `CardWeb.kt`: the card face, a WebView with a JS bridge.
  - `Reels.kt`: Reels Mode.
  - `ui/`: the Compose screens.
- `app/src/main/assets/web/`: the card page (`card.html`, `card.js`, `style.css`), fonts and MathJax.
- `app/src/main/assets/reels.json`: Reels Mode's songs, comments and accounts.
- `art/logo.svg`: the logo (the studying man) as an SVG. The app icon (`res/drawable/ic_launcher_foreground.xml`, an adaptive icon with a monochrome layer for themed icons) and the in-app logo (`res/drawable/logo.xml`) use the same path.

## Third-party

- [Carlito](https://github.com/googlefonts/carlito) font (SIL OFL 1.1).
- [MathJax](https://www.mathjax.org/) 3.2.2 (Apache 2.0).
- [zstd-jni](https://github.com/luben/zstd-jni) (BSD).
- AndroidX and Jetpack Compose (Apache 2.0).
- The card layout is a fork of [anki-prettify](https://github.com/pranavdeshai/anki-prettify).
