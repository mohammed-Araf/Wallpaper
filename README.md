## 🎨 WallPaper: Shared Live Lockscreen

WallPaper is an interactive Android application that allows partners, friends, or family members to draw, sketch, and edit a shared live wallpaper together in real-time. What you draw on your canvas instantly updates on your partner's lockscreen.

### ✨ Key Features

*   **Real-Time Sync**: Powered by Firebase Realtime Database for instantaneous updates across devices.
*   **Interactive Canvas**: A fully featured sketching tool built with Jetpack Compose.
    *   **Sketch Mode**: Draw with 7 vibrant colors and multiple brush sizes.
    *   **Eraser Mode**: Precisely remove parts of your strokes with varying eraser sizes.
    *   **Create Mode**: Add text, place emojis/stickers, and upload images to the canvas.
    *   **Transform Mode**: Move, scale, and rotate any sticker, text, or image with intuitive multi-touch gestures.
*   **Live Wallpaper Engine**: A custom Android `WallpaperService` renders the shared canvas efficiently as your system wallpaper.
*   **Chronological Layering**: Elements stack naturally based on when they were created, making collaborative editing seamless.
*   **Modern Aesthetics**: 
    *   OLED-optimized true-dark backgrounds (`#08080F`).
    *   Edge-to-edge immersive UI.
    *   Fluid, spring-based animations and micro-interactions.
    *   Tactile haptic feedback on tool selections and actions.
*   **Secure Pairing**: Generate a 6-digit code to securely link your device with a partner.

### 🛠️ Tech Stack

*   **Language**: Kotlin
*   **UI Toolkit**: Jetpack Compose (`Material 3`, `Compose Animation Core`)
*   **Backend / Realtime Database**: Firebase Realtime Database & Firebase Auth (Anonymous Login)
*   **Storage**: Firebase Cloud Storage (for images)
*   **System Integration**: Android `WallpaperService`

### 🚀 Getting Started

1.  Clone the repository:
    ```bash
    git clone https://github.com/mohammed-Araf/Wallpaper.git
    ```
2.  Open the project in **Android Studio**.
3.  Ensure you have your own `google-services.json` file placed in the `app/` directory (you will need to create a Firebase project and add Android support).
4.  Build and run the project on an Android device or emulator running API 26 or higher.

### 📱 How to Use

1.  **Pair**: Launch the app and share your 6-digit pairing code with your partner, or enter their code.
2.  **Edit**: Tap "Open Canvas" from the Home Screen.
3.  **Create**: Use the tools at the bottom to draw, add stickers, type text, or upload photos.
4.  **Transform**: Tap any item you created to move, pinch-to-zoom, or rotate it with two fingers.
5.  **Set Wallpaper**: Return to the Home Screen and tap "Set as Wallpaper" to apply the canvas to your device's lockscreen and home screen.
