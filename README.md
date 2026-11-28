# medslave

You're going to doomscroll anyway, so make it your flashcards. medslave turns your own Anki deck into an Instagram-style feed and Reels. Every post, story and reel is a card from your deck, so the scroll you'd do anyway becomes revision. Everything stays on your phone.

## Features

### Feed and Reels
- **A home feed made from your deck.** Each subject has its own accounts. They post card answers and share stories, and new stories turn up every five minutes. Pull down to refresh the feed with new posts.
- **Hide cards you're done with.** A post's menu hides its card from posts, stories and reels; Settings › Hidden cards brings it back.
- **Profiles, stories and saved cards.** Tap an account to open its profile, with highlights and more posts. Bookmark any post or reel, and the archive keeps it as saved.cards. Your study heatmap is a post too, from daily.heatmap.
- **Reels review.** Cards scroll like reels and follow your finger. Swipe up if you knew it and tap Repost if you didn't. Swiping down goes back a card and takes back its answer, for cards reviewed since you opened Reels. You can also turn on the volume keys to answer: down if you knew it, up if you didn't. The like, comment, Repost (missed it), share, save and music buttons make it feel like the real thing.

### Deck import and review
- **Import any Anki deck (`.apkg`).** The old and new collection formats both work, as do zstd compression and big decks. You choose what each note type becomes and which field fills each slot. Known note types map themselves: AnKing, Anki's Cloze and Image Occlusion, Image Occlusion Enhanced, and MCQs. "Open with medslave" works from a file manager or a chat app. A deck link, `medslave://import?url=<the .apkg's link, percent-encoded>`, opens the app and imports that deck (anything that isn't an Anki package is refused); `scripts/apkg-link.zsh <url>` makes one.
- **Card types.** Basic, cloze (including sequential clozes), image occlusion and MathJax.
- **Review styles.** Swipe Tinder-style or flip, with animations as each new card comes in. Five colour themes plus Material You (your wallpaper's colours, Android 12+), each in light and dark, following the phone or set by hand. Decks can be shuffled or kept in order, and a session resumes where you left off. Missed cards come back 20–40 cards later.
- **Your progress.** A study heatmap, plus progress backup and restore as a JSON file.

### MCQs
- **Multiple-choice cards drawn like AkiraRemix MCQs.** The options are shuffled, you tap one to answer, and the right and wrong answers are marked. The explanation sits in its own card under the answer, or, with Hide explanation, on the back of the answer card: turn it over with a sideways swipe (up or down in Tinder). In the feed it folds away behind a chevron.

### Music and playlists
- **A song on most posts and reels.** Songs play as 30-second previews, sorted into genres you can switch on and off. Previews are cached and cleaned up for you.
- **Your own playlists.** Paste a list of songs or a playlist link, and long-press a playlist to edit it. An Apple Music album link brings in that album's songs as they are; a Spotify album is matched to the same album on Apple, so the songs are exact.

The deck, your progress and your settings stay inside the app, and nothing is uploaded. The only network use is the song previews and their cover art, and a check for a newer release on GitHub when the app starts (it offers to download and install it).

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

## Third-party

- [Carlito](https://github.com/googlefonts/carlito) font (SIL OFL 1.1).
- [MathJax](https://www.mathjax.org/) 3.2.2 (Apache 2.0).
- [zstd-jni](https://github.com/luben/zstd-jni) (BSD).
- AndroidX and Jetpack Compose (Apache 2.0).
- The card layout is a fork of [anki-prettify](https://github.com/pranavdeshai/anki-prettify).
