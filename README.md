# Whaleup GamesHub SDK — Android

A native Android SDK that embeds the Whaleup GamesHub experience inside any host application. The SDK renders a curated, full-screen game hub via a WebView and bridges communication between the web layer and the native host app.

---

## Table of Contents

- [Requirements](#requirements)
- [Project Structure](#project-structure)
- [Integration](#integration)
  - [1. Add the Module](#1-add-the-module)
  - [2. Declare Permissions](#2-declare-permissions)
  - [3. Launch GamesHub](#3-launch-gameshub)
- [BiomeSdkProps](#biomesdkprops)
- [UserConfig](#userconfig)
- [Callbacks](#callbacks)
  - [onMessage](#onmessage)
  - [onWhaleupSDKEvent](#onwhaleupsdkevent)
  - [onWhaleupSDKError](#onwhaleupsdkerror)
  - [onPageLoad / onPageError](#onpageload--onpageerror)
  - [onClose / onCloseSdk](#onclose--onclosesdk)
- [UserProfile](#userprofile)
- [Game Catalog](#game-catalog)
  - [Catalog Structure](#catalog-structure)
  - [AppEntry Fields](#appentry-fields)
  - [Categories](#categories)
  - [Default Games](#default-games)
- [Message System](#message-system)
  - [Message Types](#message-types)
  - [Message Actions](#message-actions)
- [API Endpoints](#api-endpoints)
- [Permissions](#permissions)
- [Architecture Overview](#architecture-overview)

---

## Requirements

| Requirement       | Value           |
|-------------------|-----------------|
| Min SDK           | API 24 (Android 7.0) |
| Compile SDK       | API 36          |
| Language          | Kotlin          |
| JVM Target        | Java 11         |

---

## Project Structure

```
whaleup-platform-android/
├── app/                        # Demo host application (used for testing)
│   └── src/main/               # Demo app source
├── gameshub/                   # SDK module (include this in your project)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── hub-catalog.json         # Default game catalog
│       └── java/com/whaleup/gameshub/
│           ├── data/                    # Data models & session state
│           │   ├── BiomeState.kt
│           │   ├── CatalogLoader.kt
│           │   ├── CatalogModels.kt
│           │   ├── GamesHubSession.kt
│           │   ├── MessageTypes.kt
│           │   ├── Models.kt
│           │   ├── PlayerPrefsManager.kt
│           │   └── UserProfile.kt
│           ├── launcher/                # SDK entry point
│           │   ├── BiomeSdkProps.kt
│           │   └── GamesHubLauncher.kt
│           ├── messaging/               # WebView ↔ Native bridge
│           │   ├── MessageRouter.kt
│           │   └── RouteAction.kt
│           ├── network/                 # API bridge & endpoints
│           │   ├── APIBridge.kt
│           │   └── HubEndpoint.kt
│           ├── ui/                      # Hub UI (RecyclerViews, Adapters)
│           │   ├── CategoryChipAdapter.kt
│           │   ├── FeaturedAdapter.kt
│           │   ├── GameCardAdapter.kt
│           │   └── GamesHubActivity.kt
│           ├── util/
│           │   └── ImageLoader.kt
│           └── webview/                 # WebView host for individual games
│               ├── ContextProvider.kt
│               ├── WhaleBridge.kt
│               └── HubWebViewActivity.kt
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

---

## Integration

### 1. Add the Module

In your root `settings.gradle.kts`, include the module:

```kotlin
include(":gameshub")
```

In your app's `build.gradle.kts`, declare the dependency:

```kotlin
dependencies {
    implementation(project(":gameshub"))
}
```

### 2. Declare Permissions

The SDK requires internet access. Ensure your host app's `AndroidManifest.xml` includes:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 3. Launch GamesHub

Call `GamesHubLauncher.open()` from anywhere in your host app to open the GamesHub:

```kotlin
import com.whaleup.gameshub.launcher.BiomeSdkProps
import com.whaleup.gameshub.launcher.GamesHubLauncher
import com.whaleup.gameshub.data.UserConfig

val props = BiomeSdkProps(
    userConfig = UserConfig(
        userId = "user_123",
        sessionId = "host_session_123",
        apiBaseUrl = "https://api.yourdomain.com",
        timezone = "Asia/Kolkata",
        authToken = "your_auth_token",
        name = "John Doe",
        avatar = "https://example.com/avatar.png",
        compositeEndpoint = "/api/composite"
    ),
    onWhaleupSDKEvent = { event ->
        Log.d("GamesHub", "Event: ${event.type} / ${event.action}")
    },
    onWhaleupSDKError = { error ->
        Log.e("GamesHub", "Error: ${error.type} / ${error.action}")
    },
    onClose = {
        // Handle an ordinary hub/game close
    },
    onCloseSdk = {
        // Handle an explicit request to close the entire SDK
    }
)

GamesHubLauncher.open(context, props)
```

---

## BiomeSdkProps

`BiomeSdkProps` is the top-level configuration object passed to `GamesHubLauncher.open()`.

| Property         | Type                           | Required | Default  | Description |
|------------------|--------------------------------|----------|----------|-------------|
| `userConfig`     | `UserConfig`                   | ✅ Yes   | —        | User identity and API configuration |
| `onMessage`      | `((JsMessage) -> Unit)?`       | No       | `null`   | Raw WebView messages forwarded to the host |
| `onWhaleupSDKError` | `((SDKError) -> Unit)?`     | No       | `null`   | SDK error events (load failures, JS errors, etc.) |
| `onWhaleupSDKEvent` | `((SDKEvent) -> Unit)?`     | No       | `null`   | SDK lifecycle events (coins earned, game exited, etc.) |
| `onPageLoad`     | `((String) -> Unit)?`          | No       | `null`   | Called with the URL when a page loads successfully |
| `onPageError`    | `((String) -> Unit)?`          | No       | `null`   | Called with an error message when a page fails to load |
| `onClose`        | `(() -> Unit)?`                | No       | `null`   | Called for an ordinary `close` or exit request |
| `onCloseSdk`     | `(() -> Unit)?`                | No       | `null`   | Called for an explicit `closeSdk` request; falls back to `onClose` when omitted |
| `allowedDomains` | `List<String>?`                | No       | `null`   | Allowlist of domains the WebView may navigate to |

---

## UserConfig

`UserConfig` carries the user identity and API routing information.

| Property             | Type            | Required | Description |
|----------------------|-----------------|----------|-------------|
| `userId`             | `String`        | ✅ Yes   | Unique identifier for the current user |
| `sessionId`          | `String`        | ✅ Yes   | Unique session identifier for the host application session |
| `apiBaseUrl`         | `String`        | ✅ Yes   | Base URL of the backend API (e.g. `https://api.example.com`) |
| `timezone`           | `String`        | ✅ Yes   | Timezone for day reset / events (e.g. `Asia/Kolkata`) |
| `authToken`          | `String?`       | No       | Bearer token for authenticated API calls |
| `name`               | `String?`       | No       | Display name shown in the hub |
| `avatar`             | `String?`       | No       | URL of the user's avatar image |
| `compositeEndpoint`  | `String?`       | No       | Path for the composite API route (e.g. `/api/composite`) |
| `userAgent`          | `String?`       | No       | Custom User-Agent string injected into the WebView |
| `allowedDomains`     | `List<String>?` | No       | Allowed domains for WebView navigation |

---

## Callbacks

### onMessage

Receives every raw message forwarded from the WebView before internal routing.

```kotlin
onMessage = { message: JsMessage ->
    // message.type   – e.g. "navigation", "gameplay"
    // message.action – e.g. "launchGame", "coinsEarned"
    // message.data   – Map<String, Any?> with message payload
}
```

### onWhaleupSDKEvent

Receives lifecycle and gameplay events from the SDK.

```kotlin
onWhaleupSDKEvent = { event: SDKEvent ->
    when (event.action) {
        "coinsEarned"    -> handleCoinsEarned(event.data)
        "gemsEarned"     -> handleGemsEarned(event.data)
        "beginGameExit"  -> handleGameExit(event.data)
    }
}
```

### onWhaleupSDKError

Receives error events such as load failures, network interruptions, and JS crashes.

```kotlin
onWhaleupSDKError = { error: SDKError ->
    // error.type   – e.g. "loadFailure", "jsError"
    // error.action – e.g. "gameLoadFailed", "fatalJsError"
    // error.data   – Map<String, Any?> with error details
}
```

### onPageLoad / onPageError

Fine-grained WebView page lifecycle hooks.

```kotlin
onPageLoad  = { url: String -> /* page loaded successfully */ },
onPageError = { msg: String -> /* page failed to load */ }
```

### onClose / onCloseSdk

`onClose` handles ordinary close/exit requests. `onCloseSdk` handles an explicit `closeSdk` request and falls back to `onClose` when omitted.

```kotlin
onClose = {
    finish() // or navController.popBackStack()
},
onCloseSdk = {
    finish()
}
```

---

## UserProfile

The SDK maintains a structured user profile that is kept in sync with the backend and persisted via `SharedPreferences`.

```
UserProfile
├── basic
│   ├── userId          String?
│   ├── userName        String?
│   ├── avatarUrl       String?
│   └── authToken       String?
├── login
│   ├── lastLoggedInOn  String?
│   ├── loginDay        Int     (default: 1)
│   ├── loginStreak     Int     (default: 1)
│   ├── dailyLoginAwarded  Boolean
│   ├── isFirstVisit    Boolean
│   └── ftueRewardGiven Boolean
├── gameStats
│   ├── gamesPlayedToday    Int
│   ├── gamesPlayedTotal    Int
│   ├── mostPlayedGame      String
│   ├── mostPlayedGameCount Int
│   └── totalPlayTimeSec    Int
├── earnings
│   ├── gemsEarnedToday     Int
│   ├── gemsEarnedTotal     Int
│   ├── coinsEarnedTotal    Int
│   ├── totalCoinsEarnedToday Int
│   └── currentGems         Int
└── claimableRewards
    ├── loginRewardCoinsForToday    Int
    ├── perGameRewardCoinsForToday  Int
    ├── maxEarnableCoinForToday     Int
    ├── claimableGameRewardCoins    Int
    ├── claimableLoginRewardCoins   Int
    ├── claimableSignupRewardCoins  Int
    ├── lockedLoginRewardCoins      Int
    ├── lockedGameRewardCoins       Int
    └── monthlyRewardsBonusAwarded  Boolean
```

---

## Game Catalog

The game catalog (`hub-catalog.json`) is bundled as an asset and loaded at runtime. It defines which games appear in the hub, how they are categorised, and which ones are featured.

### Catalog Structure

```json
{
  "hub": {
    "version": 1
  },
  "categories": ["Trending", "Puzzle", "Casual", "Sport & Racing", "Arcade", "Action"],
  "apps": {
    "featured": [ /* AppEntry[] – displayed as a horizontal banner row */ ],
    "grid":     [ /* AppEntry[] – displayed as a scrollable grid */      ]
  }
}
```

### AppEntry Fields

Each game entry inside `featured` or `grid` has the following fields:

| Field       | Type     | Required | Description |
|-------------|----------|----------|-------------|
| `id`        | `String` | ✅ Yes   | Unique identifier for the game (used for API calls) |
| `name`      | `String` | ✅ Yes   | Display name shown in the hub |
| `iconUrl`   | `String` | ✅ Yes   | URL for the game icon / thumbnail image |
| `entryUrl`  | `String` | ✅ Yes   | URL of the game loaded inside the WebView |
| `type`      | `String` | No       | Game type. Currently: `"web"` (default) |
| `category`  | `String` | No       | Must match one of the values in `categories` |
| `rating`    | `Double` | No       | Star rating (0.0 – 5.0); default: `0.0` |

### Categories

The SDK ships with the following default categories:

| Category        |
|-----------------|
| `Trending`      |
| `Puzzle`        |
| `Casual`        |
| `Sport & Racing`|
| `Arcade`        |
| `Action`        |

### Default Games

The SDK ships with three default games:

| Game               | ID                   | Category | Rating | Type |
|--------------------|----------------------|----------|--------|------|
| Crypto Buster Game | `crypto-buster-game` | Arcade   | 4.5 ⭐  | web  |
| Runner Game        | `runner-game`        | Action   | 4.8 ⭐  | web  |
| Ludo               | `ludo`               | Casual   | 4.7 ⭐  | web  |

> **Featured:** Crypto Buster Game and Runner Game appear in the featured banner.  
> **Grid:** All three games appear in the scrollable grid.

#### Adding a Custom Game

To add a game, append an entry to the `featured` and/or `grid` arrays in `hub-catalog.json`:

```json
{
  "id": "my-game",
  "name": "My Awesome Game",
  "iconUrl": "https://example.com/icon.png",
  "entryUrl": "https://example.com/game/index.html",
  "type": "web",
  "category": "Casual",
  "rating": 4.2
}
```

---

## Message System

The SDK uses a bidirectional bridge between the WebView and the native layer. Messages are JSON objects with `type`, `action`, and an optional `data` payload.

### Message Types

| Type                   | Constant                            | Description |
|------------------------|-------------------------------------|-------------|
| `navigation`           | `BiomeMessageType.NAVIGATION`        | Hub/game navigation events |
| `gameplay`             | `BiomeMessageType.GAMEPLAY`          | In-game events (start, end, rewards) |
| `stateSync`            | `BiomeMessageType.STATE_SYNC`        | Profile and config sync between native and web |
| `loadFailure`          | `BiomeMessageType.LOAD_FAILURE`      | Hub or game failed to load |
| `networkInterruption`  | `BiomeMessageType.NETWORK_INTERRUPTION` | Network lost / restored |
| `activityRecreation`   | `BiomeMessageType.ACTIVITY_RECREATION` | Android activity was recreated |
| `outOfMemory`          | `BiomeMessageType.OUT_OF_MEMORY`     | Device low on memory |
| `jsError`              | `BiomeMessageType.JS_ERROR`          | JavaScript error in the WebView |
| `navigationError`      | `BiomeMessageType.NAVIGATION_ERROR`  | Back navigation or URL blocked |
| `criticalFailure`      | `BiomeMessageType.CRITICAL_FAILURE`  | Unrecoverable SDK failure |
| `analytics`            | `BiomeMessageType.ANALYTICS`         | Analytics tracking events |
| `share`                | `BiomeMessageType.SHARE`             | Share / clipboard actions |
| `playerPrefs`          | `BiomeMessageType.PLAYER_PREFS`      | Persistent per-game player preferences |

### Message Actions

#### Navigation

| Action         | Constant                         | Description |
|----------------|----------------------------------|-------------|
| `hubLoaded`    | `BiomeMessageAction.HUB_LOADED`  | Hub finished loading; SDK sends user profile |
| `launchGame`   | `BiomeMessageAction.LAUNCH_GAME` | User tapped a game; requires `data.gameId` |
| `gameLoaded`   | `BiomeMessageAction.GAME_LOADED` | Game WebView is ready; SDK calls `game-started` API |
| `exitGame`     | `BiomeMessageAction.EXIT_GAME`   | User exited the game; SDK returns to hub |
| `close`        | `BiomeMessageAction.CLOSE`       | Close hub through `onClose` |
| `closeSdk`     | `BiomeMessageAction.CLOSE_SDK`   | Close the SDK through `onCloseSdk` |

#### Gameplay

| Action            | Constant                            | Description |
|-------------------|-------------------------------------|-------------|
| `gameStarted`     | `BiomeMessageAction.GAME_STARTED`   | Game session registered with the backend |
| `gameCompleted`   | `BiomeMessageAction.GAME_COMPLETED` | Game ended; SDK calls `game-ended` API |
| `coinsEarned`     | `BiomeMessageAction.COINS_EARNED`   | Player earned coins; forwarded via `onWhaleupSDKEvent` |
| `gemsEarned`      | `BiomeMessageAction.GEMS_EARNED`    | Player earned gems; forwarded via `onWhaleupSDKEvent` |

#### State Sync

| Action              | Constant                               | Description |
|---------------------|----------------------------------------|-------------|
| `requestProfile`    | `BiomeMessageAction.REQUEST_PROFILE`   | Web requests user profile from native |
| `requestConfig`     | `BiomeMessageAction.REQUEST_CONFIG`    | Web requests game config; requires `data.gameId` |
| `updateProfile`     | `BiomeMessageAction.UPDATE_PROFILE`    | Native sends updated profile to web |
| `updateGameConfig`  | `BiomeMessageAction.UPDATE_GAME_CONFIG`| Native sends game config to web |
| `restoreState`      | `BiomeMessageAction.RESTORE_STATE`     | Re-sent after activity recreation |

#### Player Prefs

| Action                | Constant                                  | Description |
|-----------------------|-------------------------------------------|-------------|
| `getPlayerPref`       | `BiomeMessageAction.GET_PLAYER_PREF`      | Read a persistent preference by key |
| `setPlayerPref`       | `BiomeMessageAction.SET_PLAYER_PREF`      | Write a persistent preference |
| `deletePlayerPref`    | `BiomeMessageAction.DELETE_PLAYER_PREF`   | Delete a preference by key |
| `migratePlayerPrefs`  | `BiomeMessageAction.MIGRATE_PLAYER_PREFS` | Bulk-import prefs from another source |
| `userLoggedOut`       | `BiomeMessageAction.USER_LOGGED_OUT`      | Clear player prefs on logout |

#### Share

| Action             | Constant                              | Description |
|--------------------|---------------------------------------|-------------|
| `shareRequest`     | `BiomeMessageAction.SHARE_REQUEST`    | Open native share sheet with image/title/url |
| `copyToClipboard`  | `BiomeMessageAction.COPY_TO_CLIPBOARD`| Copy text to device clipboard |

#### Analytics

| Action          | Constant                           | Description |
|-----------------|------------------------------------|-------------|
| `trackEvent`    | `BiomeMessageAction.TRACK_EVENT`   | Custom event tracking |
| `trackScreen`   | `BiomeMessageAction.TRACK_SCREEN`  | Screen view tracking |

---

## API Endpoints

All network calls are routed through `APIBridge` using `HubEndpoint`.

| Name                  | Path              | Route URI                 | Description |
|-----------------------|-------------------|---------------------------|-------------|
| `CATALOG`             | `/catalog`        | `/catalog`                | Fetch remote game catalog (if configured) |
| `GET_USER_PROFILE`    | `/api/composite`  | `user/get-user`           | Fetch full user profile from backend |
| `GET_USER_CONFIG`     | `/api/composite`  | `admin/config`            | Fetch admin/game configuration |
| `GAME_STARTED`        | `/api/composite`  | `game/game-started`       | Register a game session start |
| `GAME_ENDED`          | `/api/composite`  | `game/game-ended`         | Register a game session end |
| `GET_GULLAK`          | `/api/composite`  | `gullak/get-gullak`       | Fetch reward piggy-bank (Gullak) data |
| `CLAIM_GULLAK`        | `/api/composite`  | `gullak/claim-gullak`     | Claim accumulated Gullak rewards |

The `apiBaseUrl` and `compositeEndpoint` in `UserConfig` control where these requests are sent.

---

## Permissions

The SDK declares no dangerous permissions. The following normal permissions are required and declared in the module manifest:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## Architecture Overview

```
Host App
   │
   └── GamesHubLauncher.open(context, BiomeSdkProps)
            │
            ├── GamesHubSession (global session)
            │
            ├── GamesHubActivity  ─── Hub WebView (hub-catalog.json)
            │        │
            │        └── WhaleBridge ──► MessageRouter
            │                                │
            │                  ┌─────────────┼─────────────┐
            │                  ▼             ▼             ▼
            │             RouteAction   APIBridge    BiomeState
            │          (LoadGame, etc.) (REST calls) (Profile, Prefs)
            │
            └── HubWebViewActivity ─── Game WebView (entryUrl)
                     │
                     └── WhaleBridge ──► MessageRouter (same pipeline)
```

- **`GamesHubActivity`** renders the hub (featured banner + category chips + game grid).  
- **`HubWebViewActivity`** renders individual games in a full-screen WebView.  
- **`WhaleBridge`** is the JavaScript interface that receives JSON messages from the web layer.  
- **`MessageRouter`** parses messages and emits `RouteAction` objects.  
- **`APIBridge`** executes HTTP calls and returns results back to the WebView.  
- **`BiomeState`** / **`PlayerPrefsManager`** persist user profile and per-game state.
