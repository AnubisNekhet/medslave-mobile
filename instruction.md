# Build: the app tour (first deck import)

Instructions for Claude, for a later session. Read `CLAUDE.md` first for the layout. Don't start until the user asks for it.

## Goal

The first time someone has a deck loaded, walk them through the app's main features with a coach-mark tour: **dim the screen, cut a spotlight around one control, show a "finger" circle pressing it, and say what it does in one line.** The tour **never performs the action**. It doesn't open the credits, a deck, settings, a profile or a menu, and it doesn't hide or block anything. It only shows where things are and how to reach them.

This is part of the existing card-review tutorial (`Tutorial` in `ui/Panels.kt`, the swipe demo). It's reset by the same option, Settings › Reviewing › **Show the tutorial again**.

## When it shows

- **Deck view tour:** the first time the Menu (`Menu` in `ui/Home.kt`) is on screen with a deck loaded. That's right after the first import finishes (`importGo` → `loadDeck`), or on launch if a deck is already there and the tour was never seen.
- **Home tour (Reels Mode only):** the first time the feed (`FeedView`) is on screen with a deck loaded, once its posts have arrived (the first `R.feed`/`addPosts`).
- **Card tutorial:** unchanged. It's the existing `Tutorial`, shown on the first card (`showT()` in `App.kt`).
- Each part shows once. Keep one flag per part in the store, e.g. `medslave:tour` = `{"menu":1,"feed":1}`. `SEEN` stays the card tutorial's key.
- **Reset:** `Rs.resetTutorial()` clears `SEEN` (as now) **and** `medslave:tour`, so all three show again. The Reviewing row's subtitle should then read something like "The tour and the swipe demo show again".
- **Don't show while** a dialog, sheet, the import window or Settings is open, or a story or profile is open in the feed. Wait until the screen is clear.

## Steps

The copy is a starting point; keep each line short and in the app's tone. Where a step has a gesture, the finger circle shows it: tap, long press (hold, then a ring fills) or swipe.

**Deck view (Menu, both modes)**

1. **Logo + "medslave"** (tap): "Tap the logo for the credits: a pie of every deck's subjects."
2. **The deck pages** (swipe sideways across the subject list; only when there are 2+ decks): "Swipe for your other decks."
3. **All subjects / All decks** (tap): "Study every card in this deck, or in all of them." With one deck, there's only All decks.
4. **A subject row** (tap the first one): "Tap a subject to start it." Add ", as reels" in Reels Mode. If it has a ⌄ picker button, also: "⌄ picks a subdeck."
5. **The gear** (top right; tap): "Settings: decks, progress, feed filters, appearance."
6. **Reels Mode only, the bottom bar's Home** (tap): "Home is your feed." The Reels button: "Reels picks up where you left off."

**Home (the feed, Reels Mode)**

These targets live inside the feed WebView (`feed.html`/`feed.js`):

1. **The archive button** `#arch` (top left; tap): "Your saved cards. Tap the bookmark on any post to save it."
2. **The stories row** `#stories` (tap the first ring): "Stories: a few cards from each subject. Tap an account to open its profile."
3. **The heatmap post** `[data-i="h"]` (tap a day): "Your daily cards. Tap a day for its count; keep the streak going."
4. **A post's ⋯** (the first real post's `.f-more`; tap): "⋯ → Hide keeps a card out of your feed and reels; Block hides everything that account posts." **Don't open the menu.** At most, draw a static mock of the two-item menu next to the spotlight.
5. **The account name on a post** (tap): "Tap an account for its profile. Follow it for the Following filter."
6. **The bottom bar's Home, long press:** "Hold Home to switch feed filters." The finger circle holds, its ring fills, and the bar buzzes once (`rs.reels.buzz()`). Then it lets go **without** opening `FilterPick`.

Then the card tutorial follows naturally on the first card the person opens.

## How to build it

- **One overlay composable**, e.g. `Tour(rs)` in a new `ui/Tour.kt`, drawn from `MainActivity` above everything except dialogs. It has:
  - a full-screen dim: black at ~55%, the same as `Tutorial`'s background.
  - a **spotlight hole** around the target. Draw it with `Canvas` + `BlendMode.Clear` on an offscreen layer (`graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }`). Use a rounded rect padded ~6dp, or a circle for round buttons.
  - a **finger**: a ~36dp semi-transparent circle in the text colour with a soft ring.
    - Tap: grow in, press (scale .85 + a ripple ring), release.
    - Long press: press and hold ~700ms while an arc fills around it.
    - Swipe: travel across the target.
    - Loop each gesture every ~1.6s, and respect `rs.rm()` (reduced motion: show it statically).
  - a **caption card** in the app's card style (`P.card`, `P.border`, 8dp corners), above or below the target, whichever has room. It has the line of text, step dots, **Next** (primary `Btn`) and **Skip** (marks that part seen).
  - Tapping anywhere also goes to the next step. **Touches never reach the app underneath**: consume them all, so nothing opens.
- **Finding Compose targets:** add `Modifier.tourTarget(rs, "menu.logo")` (an `onGloballyPositioned` that stores `boundsInRoot()` in a `mutableStateMapOf` on `Rs`) to the logo row, the deck pager, the All subjects / All decks row, the first subject row, the gear, and the `NavBar` Home and Reels items. Skip a step whose target isn't registered, e.g. the pager with one deck.
- **Finding feed targets:** add `R.rects(json)` to `feed.js`. It returns, through a new bridge call `App.rects(json)`, each selector's `getBoundingClientRect()` in CSS px. Scroll the feed to the top first (`R.top(true)`).
  - Multiply by the WebView's density, then add the WebView's position in the root, to get root px.
  - Re-ask after the posts arrive, since the heatmap and the first post load late, and on `scroll`. During the tour, it's fine to lock the feed's scrolling with a `R.tourLock(true)` that sets `overflow:hidden` on `#feed`.
- **Order:** the Menu tour runs on the Menu. The feed tour runs when the person first lands on the feed: after the import, Reels Mode opens the feed, so the feed tour will usually come first there. That's fine; each part is independent.
- **Text sizes and colours** follow `Theme.kt` (`Txt`, `P.text`, `P.faint`). The finger and spotlight edge use `P.text`; nothing uses alpha on text.

## Done when

- Fresh install → import a deck: the tour shows on the first view, step by step, and nothing behind it opens or changes. No card is hidden or blocked, and no credits, settings, profile or filter picker opens.
- Skip or finish → it never shows again, until Settings › Reviewing › Show the tutorial again, after which all three parts (Menu, feed, card swipe demo) show again.
- Light and dark themes, phone and tablet widths, Reels Mode on and off, and one deck vs several all look right. The spotlight sits on the right control after rotation, or after the feed's posts load in.
- Bump `versionCode`/`versionName`, build (see `CLAUDE.md`), and install on the phone with `adb install -r`. Then add a short line about `Tour` to `CLAUDE.md`'s layout.
