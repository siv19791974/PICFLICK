# PicFlick 📸

A privacy-first social photo sharing app built with Jetpack Compose and Firebase.

## 🌟 Features

### Core Experience
- **📷 Photo Sharing** - Take photos, apply filters, and share with friends
- **❤️ Social Interactions** - Like, react with emojis, and comment on photos
- **💬 Real-time Chat** - Message your friends directly
- **🔒 Privacy First** - Friends-only by default, block/unblock users

### Photo Features
- **🎨 Filters** - Apply stunning photo filters (Vintage, Noir, Vivid, Chrome, etc.)
- **👆 Double-Tap to Like** - Instagram-style heart animation
- **🏷️ Tag Friends** - Tag friends in your photos
- **🔍 Explore** - Discover photos from the community with search

### Social Features
- **👥 Friend System** - Follow/unfollow with mutual friendship
- **🛡️ Safety Tools** - Report inappropriate content, block users
- **🔔 Smart Notifications** - Granular control over push and in-app notifications
- **📊 Streaks** - Keep your posting streak alive

### Privacy & Security
- **🔐 Private by Default** - Photos only visible to accepted friends
- **🚫 Block System** - Block users to prevent interaction
- **⚠️ Content Reporting** - 7 categories for reporting inappropriate content
- **📵 Quiet Hours** - Do not disturb mode with customizable hours

## 🛠️ Tech Stack

- **UI:** Jetpack Compose with Material Design 3
- **Backend:** Firebase (Auth, Firestore, Storage, FCM)
- **Image Loading:** Coil
- **Architecture:** MVVM with Repository pattern
- **Language:** Kotlin

## 📱 Screenshots

*(Add screenshots here)*

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17+
- Firebase project with:
  - Authentication (Email/Password)
  - Cloud Firestore
  - Cloud Storage
  - Cloud Messaging (FCM)

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/picflick.git
   cd picflick
   ```

2. **Add your Firebase config**
   - Download `google-services.json` from Firebase Console
   - Place it in `app/` directory

3. **Build and run**
   ```bash
   ./gradlew assembleDebug
   ```

## 🏗️ Architecture

```
app/
├── data/           # Data classes (Flick, UserProfile, etc.)
├── repository/     # Firebase operations
├── viewmodel/      # Screen state management
├── ui/
│   ├── screens/    # Main screens (Home, Profile, etc.)
│   ├── components/ # Reusable UI components
│   └── theme/      # Colors, typography
└── service/        # FCM messaging service
```

## 🎯 Key Design Decisions

### Privacy-First Approach
- Default photo visibility: Friends only
- Mutual friendship required for full interaction
- Blocked users cannot see any content

### Simplified Notifications
- Single toggle per notification type (not push vs in-app)
- Master switch to disable all
- Quiet hours with presets

### Modern UI Patterns
- Pull-to-refresh on all list screens
- Double-tap to like with animated heart
- Zoomable photo viewer
- Material 3 dynamic theming

## 📝 License

MIT License - See [LICENSE](LICENSE) for details

## 🙏 Acknowledgments

- Built with [Jetpack Compose](https://developer.android.com/jetpack/compose)
- Powered by [Firebase](https://firebase.google.com/)

---

**Made with ❤️ for photo lovers who value privacy**
