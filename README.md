## WallPaper — Shared Live Wallpaper

Draw together on a canvas that becomes your partner's / group's live wallpaper and lock screen in real time.

### Highlights

- **Liquid-glass UI** — floating translucent panels with live backdrop blur (Android 12+, via [Haze](https://github.com/chrisbanes/haze)), specular edges and soft ambient color fields. Older Android versions fall back to frosted translucent panels.
- **Four themes** — Auto · Light · Dark · **OLED** (true `#000000`, no ambient glow — pixels stay off). Seven accent colors. All apply instantly across the app and editor.
- **Canvas thumbnails** on the home screen, refreshed every time you leave a canvas.
- **Instant canvas switching** — switch rooms from the pill at the top of the editor without leaving it. Firebase disk persistence means previously opened canvases render immediately, even offline.
- **Group roles** — owners can switch members to view-only; view-only members get a read-only editor.

### Pairing

After sign-in, a short permissions screen (notifications, scheduled switching, background sync) appears once. The pairing screen shows **your code as a QR** plus the 6-digit code; tap **Scan** to read your partner's QR, or type their code. Group join codes have QR codes too (create screen and member list) and a scan button on the join field. Scanning uses Google Play services' code scanner, so no camera permission is needed.

**Developer account** — when signed in as the developer email set in `MainActivity.kt`, a developer icon appears on the pairing screen. It skips pairing and opens a simulated partner canvas stored only on this device (unpair from Settings to leave it).

### Gestures

| Fingers | Gesture | Action |
|---|---|---|
| 1 | Drag | Draw / erase / move the tool's target |
| 1 | Draw, then hold still | Perfect shape: straight line, clean segments (H, L, Z…), circle / ellipse, triangle, square / rectangle |
| 1 | Tap | Dot (pen) · place text (text tool) · erase a whole stroke (stroke eraser) |
| 1 | Tap a text (move / text tool) | Edit it: look (plain · shadow · label · dark label · outline), font, color |
| 1 | Tap an image | Next frame: plain · rounded · white border · shadow · circle |
| 1 | Drag an item to the bottom | Delete (bin appears) — two-finger drags never delete, so items can be placed near the bin |
| 1 | Drag empty space while zoomed | Pan the view |
| 2 | Pinch / twist on an item | Scale + rotate the item |
| 2 | Pinch on empty space | Zoom / pan the view (outline shows the real wallpaper area) |
| 2 | Tap | **Undo** |
| 3 | Tap | **Redo** |
| 3 | Swipe down / up | Hide / show all tools (focus mode) |

A stroke is cancelled automatically if a second finger lands mid-stroke, so zooming never leaves stray marks. Every gesture can be turned off in **Settings → Gestures**. Tap the `%` chip to reset zoom.

### Editor tools (bottom dock)

Move · Pen · Eraser · Text · Stickers · Image. Tap the active tool again to open its tray (colors, size, opacity; eraser mode and size). Stickers: tap to drop in the middle, or long-press and drag onto the canvas. **⋯** (top right) holds background colors, set as Home / Lock, fit to screen and clear.

### Sticker market

The sticker button opens three tabs: **Emoji**, **Animated** and **Live**. Every animated and live sticker is drawn by the app in a cartoon "2.5D" style: thick ink outlines, an extruded underside, a gloss highlight, a ground shadow and chunky rounded lettering. They animate in the editor and on the home-screen wallpaper (about 20 fps, only while visible); the lock screen shows a still frame.

- **Animated**: heart, star, fire, sun, sleepy moon, ghost, party popper, rainbow, LOVE.
- **Live**: me (status avatar) · clock (digital, analog) · countdown · pet · steps · km today · speed · steps + km · speed + km · billboard (all three) · distance.
  - **Me** is a cartoon you dress up (boy / girl, skin, hair style and colour, outfit, day / night card, motorbike or scooter) that acts out what you're doing right now: on the phone (sitting with the phone, chat bubbles popping), riding (helmet, shaking bike, exhaust smoke), cycling, walking, music (headphones, notes), phone charging (sitting sadly next to the phone on the charger), chilling (phone down, mug of tea) and sleeping (in bed, Zzz). A battery chip and "· 12m" show the battery and how long it's been. Tap your own avatar with the Move tool to change its look.
  - Detection runs on the owner's phone from screen on/off, charger, battery, music playing and Google's activity transitions (in vehicle / on bicycle / walking), with GPS speed as a backup. Glances under 10 s don't count. Sleep is worked out by the viewer: a rest of 30+ min at night in the owner's time zone (or 4+ h any time). The status is only published while you have a Me sticker on a canvas. **Ghost mode** (tap your avatar) pauses status, battery and location for an hour, until morning or until you turn it off; others see a ghost "on a break". **Nudge**: tap someone else's Me sticker with the Move tool; they get a notification and your avatar jumps and waves "hey!" on their screen for a minute (delivered through the canvas, so their app or live wallpaper needs to be running). **Battery buddy**: if someone whose Me sticker is on your canvas drops to 15% or less and isn't charging, you get one notification ("🔋 Sneha's phone is at 12%"); it re-arms once they charge or pass 25%.
  - **Countdown** counts down to a date and time you pick ("Until we meet · 12 days · 4h 22m", then hh:mm:ss on the last day, then "It's here!"). Everyone in the canvas sees the same one and can tap it with the Move tool to change the title, date or icon. Clocks and countdowns use the viewer's own clock and are left off the still lock-screen image.
  - **Pet** is shared by everyone in the canvas and roams freely over it. Adopt a cat, dog or bunny; it grows from a slow little baby (0–5 days) to a young one that zooms around (5–15), a grown-up (15–30) and a slow old timer with a beard (30+). Drop a **food** bowl (Live tab, or "Drop food" in its sheet) and it runs over and eats it. Hungry after 14 h: dogs and bunnies wander around sniffing behind other stickers, cats sit and sulk. Weak after 2 days, and it dies after 3 days without food. To bury a dead pet, drag it to the bin, which becomes a little pit. You get notified when it's hungry, weak or has died (never at night).
  - Stat stickers show the numbers of whoever placed them, and others see them update. The steps sticker upgrades with the day's count (3k → 6k → 10k → 15k: new colour, medal, sparkles, a gold shimmer, then a crown).
  - Live stat stickers carry a **name plate** (your first name by default) so group members can tell whose numbers are whose. With the **Text** tool, tap the sticker to rename it. The plate is part of the sticker, so it only moves with the Move tool.
  - Distance, using positions rounded to ~110 m:
    - Two people: "so close!" under 1 km, and hearts with "together!" under 150 m.
    - Groups (3+): a mini map. Everyone keeps their real direction from the group's centre, with log-scaled distance so metres and kilometres both fit. Each person is linked to their nearest neighbour (n−1 links) and each link is labelled with the real distance, with hearts for people together. Each phone adapts its own update rate to its nearest person, so every pair stays current.

Privacy: stats are shared as plain numbers (`users/{uid}/stats`). Positions are rounded on the phone and only written to canvases that contain a distance sticker (`canvas_sessions/{room}/presence`), and removed when the sticker is gone. Permissions (steps, location, "allow all the time") are asked when you place a live sticker.

Battery: the step counter is the hardware sensor, batched. Location runs only while one of your stickers needs it and adapts its interval: 15–30 min when nobody is near, faster as someone gets closer (3 min → 1 min → 30 s), 10 min when you're both settled in the same place (campus / office), and 5 s only while moving faster than 12 km/h for speed. Android limits background location to a few updates per hour when no app screen is visible, so the fastest rates apply while the app or the wallpaper is in use.

### Home

Tap a canvas to open it, long-press for Home / Lock / Invite / Members / Leave. The Home / Lock badges only appear on canvases that are really on screen.

- **Home**: if the live wallpaper isn't applied yet, Android's wallpaper picker opens (choose Home, or Home and lock). Afterwards, Home just switches which canvas is shown.
- **Lock**: sets that canvas as the lock screen right away. Tap Lock on the same canvas again to stop syncing it.

The dock has **+** (partner or group), schedule and settings.

### Background updates

- The home wallpaper only redraws while it's visible, coalesces bursts of changes into one frame, and drops its network listener after a minute off-screen (re-attaching from the local cache when shown again).
- The lock screen bitmap is only re-rendered while the screen is off or locked (or right after you pick a new lock canvas), so nothing is drawn while you're using other apps.
- While the live wallpaper is applied, Android keeps its service alive, and that service also keeps the lock screen in sync, with no extra notification. Without the live wallpaper, lock-screen updates run while Android keeps the app in memory.

### Settings

Appearance (theme, accent, glass blur, ambient motion) · Canvas (smooth strokes, haptics, keep screen on) · Gestures · Wallpaper (apply, schedule, stop lock-screen sync) · Partner · System (permissions, clear previews, privacy, terms).

### Performance notes

- All element types sync with incremental child listeners; edits apply locally first, so drawing, erasing and undo never wait on the network.
- The eraser and undo work on in-memory data (previously each eraser movement downloaded every stroke).
- Stroke paths are pre-built once; images are decoded off the main thread into an LRU cache and drawn without per-frame copies.
- Transform updates are throttled to ~8 writes/s and own echoes are ignored.

### Tech

Kotlin · Jetpack Compose (BOM 2025.01) · Material 3 · Haze · Firebase Auth / Realtime Database (with disk persistence) / Messaging · `WallpaperService` + `WallpaperManager.FLAG_LOCK`.

### Getting started

1. Open in Android Studio.
2. Put your own `google-services.json` in `app/`.
3. Run on Android 8.0+ (API 26). Live blur needs Android 12+.

### License

Copyright © 2026 Mohammed Araf

WallPaper is free and open-source software, released under the **GNU General Public License v3.0**. See [LICENSE](LICENSE).

- You may use, study, change and share it.
- If you distribute it, or anything built from it, you must release it under the same GPLv3 terms, with its full source code. Copies stay free and open source.

**Commercial licensing:** companies that want to use WallPaper in a closed-source or proprietary product can get a separate commercial license. Please [open an issue](https://github.com/mohammed-Araf/Wallpaper/issues) or contact the author through GitHub to discuss terms.

**Contributing:** by submitting a contribution, you agree that it is licensed under GPLv3 and that the author may also include it in commercially licensed versions of WallPaper.
