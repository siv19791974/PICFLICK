# PicFlick — Play Store Listing Assets

## App Information

| Field | Value |
|-------|-------|
| **App Name** | PicFlick |
| **Default Language** | English (US) |
| **Category** | Social |
| **Tags** | Photo Sharing, Social Network, Private Sharing, Friends, Chat |

---

## Short Description (Max 80 characters)

```
Share photos privately with friends. No ads, no algorithms — just your people.
```

**Character count:** 76

---

## Full Description (Max 4000 characters)

```
PicFlick is the private photo sharing app built for real friends.

NO ADS. NO ALGORITHMS. NO ENDLESS SCROLLING.
Just you and the people you actually care about, sharing moments that matter.

---

HOW IT WORKS

1. CAPTURE — Take a photo or pick from your gallery
2. SHARE — Post to your friends' feed (Friends Only) or go Public
3. REACT — Friends leave emoji reactions on your best shots
4. CHAT — Message friends directly, one-on-one or in groups
5. CONNECT — Send friend requests, build your circle

---

KEY FEATURES

PHOTO SHARING
- Upload photos with captions and privacy controls
- Choose "Friends Only" or "Public" for every post
- Tag friends directly in photos
- Full-screen photo viewer with pinch-to-zoom
- Automatic thumbnail generation for fast loading

SOCIAL FEED
- See photos from friends in a clean, chronological feed
- 30-day home feed keeps things fresh
- Reaction counters show who's loving your shots
- Comment and reply on photos with threaded conversations

CHAT & MESSAGING
- One-on-one messaging with photo sharing
- Group chats for your closest circles
- Typing indicators and read receipts
- Push notifications for new messages

FRIENDS & DISCOVERY
- Send and receive friend requests
- Find friends from your contacts
- Explore trending and popular photos
- Follow creators and build your network

SAFETY & CONTROL
- Block or mute users at any time
- Report inappropriate content
- Delete your photos or your entire account anytime
- Full privacy controls on every post

STORAGE & SUBSCRIPTIONS
- Free tier with daily upload limits
- Upgrade to Standard, Plus, Pro, or Ultra for more storage
- Unlimited uploads with Ultra tier
- Manage your storage usage in-app

MULTI-LANGUAGE
Available in 12 languages:
English, Arabic, Chinese, French, German, Greek, Hindi, Japanese, Korean, Portuguese, Spanish, Albanian

---

BUILT FOR PRIVACY

- No third-party ad trackers
- No data sold to advertisers
- Photos shared only with who you choose
- Firebase-secured infrastructure
- Account deletion permanently removes all your data

---

Download PicFlick today and start sharing with the people who matter.

Questions? Contact us through in-app Help & Support.
```

**Character count:** ~2,100 (well within 4,000 limit)

---

## Graphic Assets Required

| Asset | Specs | Status |
|-------|-------|--------|
| **App Icon** | 512x512px PNG (already in `mipmap-xxxhdpi`) | ✅ Exists |
| **Feature Graphic** | 1024x500px JPEG/PNG | ❌ Need to create |
| **Phone Screenshots** | Min 2, max 8. 16:9 or 9:16, 320px+ shortest side | ❌ Need to capture |
| **Tablet Screenshots** | Optional. 16:9 or 9:16 | — |

### Suggested Screenshot Flow (8 max)

1. **Home Feed** — Show friends' photos in grid with reactions visible
2. **Full-Screen Photo** — Show zoomed photo with reaction picker and comment section
3. **Upload Flow** — Camera/gallery picker with filter options and privacy toggle
4. **Friends/Find Friends** — Search and friend request UI
5. **Chat Conversation** — Active chat with photo messages and typing indicator
6. **Profile** — User profile with photo grid, bio, and stats
7. **Notifications** — Notification list with friend requests and reactions
8. **Settings/Storage** — Subscription tiers and storage management

---

## Content Rating (Google Play Questionnaire)

### App Category: Social Networking

| Question | Answer |
|----------|--------|
| Does the app contain violence? | No |
| Does the app contain fear/horror? | No |
| Does the app contain sexual content or nudity? | No (user-generated content may include; moderated via reporting) |
| Does the app contain user-to-user interactions? | **Yes** — Chat, comments, friend requests, photo sharing |
| Does the app share user location? | No |
| Does the app contain in-app purchases? | **Yes** — Subscription tiers (Standard, Plus, Pro, Ultra) |
| Does the app contain ads? | **No** |
| Does the app contain gambling? | No |
| Does the app contain alcohol/tobacco/drugs? | No |
| Does the app contain profanity/crude humor? | User-generated content may contain; app does not promote |

**Expected Rating:** PEGI 12 / ESRB Teen (due to user-to-user interactions and unmoderated user-generated content)

---

## Data Safety Form (Google Play Required)

### Data Collection

| Data Type | Collected? | Shared? | Purpose |
|-----------|-----------|---------|---------|
| Name | Yes | No | Profile display |
| Email address | Yes | No | Authentication (Google Sign-In) |
| User IDs | Yes | No | App functionality |
| Photos and videos | Yes | No | Core app purpose |
| App interactions | Yes (analytics) | No | App improvement |
| Crash logs | Yes | No | Bug fixing |
| Device IDs | Yes (FCM token) | No | Push notifications |
| Purchase history | Yes (via Google Play) | No | Subscription management |
| Phone number | **No** | — | — |
| Precise location | **No** | — | — |
| Contacts | **No** | — | — |
| Financial info | **No** | — | — |

### Data Usage & Sharing

- **Is data encrypted in transit?** ✅ Yes (Firebase TLS/HTTPS)
- **Is data encrypted at rest?** ✅ Yes (Firebase Cloud Storage encryption)
- **Can users request data deletion?** ✅ Yes (in-app Delete Account feature)
- **Is data shared with third parties?** ❌ No (only Google Firebase infrastructure)
- **Is data used for advertising?** ❌ No
- **Is data used for tracking?** ❌ No

### Security Practices

- Firebase Authentication with Google Sign-In
- Firestore Security Rules restrict data access
- Cloud Functions enforce server-side validation
- No password storage (OAuth 2.0 only)

---

## Release Notes Template (For Updates)

```
What's new in version {VERSION}:

- Improved photo feed loading performance
- Enhanced chat messaging with typing indicators
- New reaction picker with more emoji options
- Bug fixes and stability improvements

Thanks for using PicFlick! Share your feedback via Settings > Help & Support.
```

---

## Contact & Support

| Field | Value |
|-------|-------|
| **Website** | (Add your website URL here, or link to GitHub repo) |
| **Email** | (Add support email here) |
| **Privacy Policy** | Link to `PRIVACY_POLICY.md` hosted on GitHub Pages or your domain |
| **Terms of Service** | (Create a separate TERMS_OF_SERVICE.md if needed) |

---

## Action Items

1. ✅ Create `PRIVACY_POLICY.md` — **DONE**
2. ✅ Create `PLAY_STORE_LISTING.md` — **DONE**
3. ⬜ **Host privacy policy publicly** — Upload to GitHub Pages, Firebase Hosting, or your own domain
4. ⬜ **Create feature graphic** (1024x500px) — Use Canva/Figma with app screenshots + branding
5. ⬜ **Capture phone screenshots** (8 max) — Use Android Studio emulator or physical device
6. ⬜ **Fill Content Rating questionnaire** in Play Console
7. ⬜ **Complete Data Safety form** in Play Console
8. ⬜ **Create Terms of Service** (separate from Privacy Policy)

---

*Generated for PicFlick v1.2.6*
