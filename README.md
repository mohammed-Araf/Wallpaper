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

### Home

Tap a canvas to open it, long-press for Home / Lock / Invite / Members / Leave. The dock has **+** (partner or group), schedule, **Live** (apply the live wallpaper) and settings.

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
