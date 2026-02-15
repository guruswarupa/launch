<p align="center">
  <img width="120" alt="Launch App Icon" alt="ChatGPT Image Feb 15, 2026, 02_36_14 PM" src="https://github.com/user-attachments/assets/bf4cc306-6966-4290-9e2a-36debd605df2" />
</p>

<h1 align="center">Launch</h1>

<p align="center">
  A clean, efficient, and minimalist Android launcher built for focus and productivity.
</p>
<p align="center">
       <a href="https://deepwiki.com/guruswarupa/launch">
         <img src="https://deepwiki.com/badge.svg" height="45" />
       </a>
</p>
<p align="center">
  <a href="https://f-droid.org/packages/com.guruswarupa.launchh/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="100" />
  </a>
</p>


## 📋 Table of Contents

- [Features](#-features)
- [Getting Started](#-getting-started)
- [Development](#-development)
- [Contributing](#-contributing)
- [Privacy & Permissions](#-privacy--permissions)
- [License](#-license)

---

## ✨ Features

### 🔍 Smart App Management
- Apps are sorted by usage frequency automatically
- Rarely used apps grouped and sorted alphabetically
- App usage stats shown beside each app icon
- Weekly phone usage summary with interactive graph
- Daily usage breakdowns with pie charts
- One-tap uninstall with long-press gesture
- Toggle between smooth grid and list views

### 🔎 Smart Universal Search
- Search apps, contacts, and perform web queries
- Built-in calculator directly in search bar
- Quick links to Play Store, browser, YouTube, and Google Maps
- Tap-to-call, SMS, or WhatsApp directly from contact results

### ⚡ Quick Actions
- **Time Widget** → Tap to open the Clock app
- **Date Widget** → Tap to open the Calendar app
- **Search Bar**:
  - Long press → Open Google in browser
  - Calculator support built-in
- **Weather Widget**:
  - Shows real-time weather and forecast
  - Change API key via Settings
- **Battery Widget**:
  - Real-time battery percentage display
  - Charging status indicator
  - Always visible on home screen


### 🔕 Focus Mode
- Long press focus icon to configure apps allowed during Focus Mode
- Temporarily hide distracting apps
- Set a countdown timer to auto-disable mode
- Cannot exit until timer ends — stay on track
- Visible timer countdown displayed on screen

### 🔐 App Lock
- Lock any app via settings
- **PIN-based authentication** before launching locked apps  
- **Fingerprint unlock supported** (if available on your device)  
- **1-minute timeout** after unlocking — no need to re-enter PIN during that window  
- After timeout, locked apps require authentication again  
- Ideal for securing sensitive apps like WhatsApp, Instagram, etc.

### ✅ Todo List Widget (Now Smarter)
- Add and manage daily tasks directly from the home screen  
- Set **due time** for each task  
- Assign **priority**: High, Medium, or Low  
- Enable **recurring tasks**: Daily, Weekly, or Custom  
- Mark tasks as complete with visual strikethrough  
- Tap "Add Todo" to schedule tasks with advanced options  
- Persistent task tracking with clean UI  

### 🔔 Notifications Widget
- View unread notification count badge
- Quick access to recent notifications on home screen
- Swipe to dismiss notifications directly from launcher
- Tap notifications to open the related app
- Requires notification access permission

### 🧮 Calculator Widget
- Full-featured calculator directly on home screen
- **Basic Mode**: Standard arithmetic operations
- **Scientific Mode**: Advanced functions (sin, cos, log, etc.)
- **Converter Mode**: Unit conversions and base conversions
- Calculation history with easy access
- Toggle between radians and degrees

### ⏱️ App Timer Manager
- Set usage timers for individual apps
- Quick presets: 1 minute, 5 minutes, 10 minutes
- Custom timer duration support
- Automatic app closure when timer expires
- Helps manage screen time and app usage

### 📱 App Categories & Organization
- Organize apps by type (Social, Productivity, Games, etc.)
- Filter apps by category
- Smart grouping of rarely used apps

### ⭐ Favorite Apps & Show All Mode
- Mark apps as favorites for quick access
- Toggle between "Favorites Only" and "Show All Apps" modes
- Customize which apps appear in main list
- Quick access to your most-used apps

### 📊 Enhanced Usage Statistics
- **Weekly Usage Graph**: Visual representation of your app usage over the past 7 days
- **Daily Usage Breakdown**: Tap any day in the weekly graph to see detailed usage
- **Interactive Pie Charts**: Visual breakdown of app usage for each day
- **App-by-App Analysis**: See exactly how much time you spent on each app
- **Total Time Tracking**: View total screen time per day at a glance
- Automatically refreshes when date changes

### 📱 Shake to Toggle Torch
- **Quick Flashlight Access**: Shake your device twice to toggle the torch
- **Background Service**: Works even when the launcher is in the background
- **Battery Efficient**: Only active when screen is on
- **One-Tap Enable**: Enable from settings to start using immediately
- Perfect for quick flashlight access without unlocking your phone

### 🎓 Interactive Feature Tutorial
- **Comprehensive Guide**: Step-by-step walkthrough of all launcher features
- **Visual Highlights**: Interactive overlays highlight each feature as you learn
- **Smart Navigation**: Automatically opens drawers and scrolls to relevant sections
- **Resume Support**: Pick up where you left off if interrupted
- **Skip Anytime**: Skip the tutorial or complete it at your own pace
- Perfect for new users to discover all features quickly

### 📦 APK Sharing
- Share APK files of installed apps
- Access from app context menu or dock
- Share via any installed app (Bluetooth, email, messaging, etc.)
- Useful for backing up apps or sharing with others

### 🎨 Third-Party Widget Support
- Add Android widgets from other apps
- Full widget management system
- Configure and customize widgets on home screen
- Support for all standard Android widgets

### 🎯 Onboarding Experience
- First-run setup wizard
- Guided introduction to key features
- Permission requests with explanations
- Quick start guide for new users
- Interactive feature tutorial after onboarding
- Step-by-step walkthrough of all features

---

## 💰 Finance Tracker
- Tap **"Add Income"** or **"Add Expense"** to track your finances
- Monthly income and expenses tracked automatically
- Long press on **Balance** to view recent transaction history
- Add custom descriptions to your transactions
- Net savings computed for the current month
- Lightweight, offline-first design

---

## ⚙️ Settings Features
- **Import/Export Data**: Backup or restore your launcher settings and data
- **Change Display Style**: Toggle between grid and list view easily
- **Change Wallpaper**: Set custom wallpapers for your home screen
- **Update Weather API Key**: Use your personal API key from openweathermap.org
- **Manage App Locks**: Configure PIN and fingerprint authentication
- **App Timer Settings**: Configure default timer durations
- **Widget Management**: Add, remove, and configure widgets
- **Workspace Configuration**: Customize workspace layouts

---

## 🤏 Gestures Guide

| Gesture                     | Result                                      |
|-----------------------------|---------------------------------------------|
| **Tap Time Widget**         | Opens Clock app                             |
| **Tap Date Widget**         | Opens Calendar app                          |
| **Long Press Search Bar**   | Opens Google in browser                     |
| **Long Press App Icon**     | Opens uninstall prompt                      |
| **Long Press Dock App**     | Remove or rename app                        |
| **Type in Search Bar**      | Instant calculator                          |
| **Long Press Focus Icon**   | Enter Focus Mode setup                      |
| **Long Press Balance**      | View transaction history                    |
| **Swipe Notification**      | Dismiss notification from widget            |
| **Shake Device (2x)**       | Toggle torch/flashlight                     |
| **Tap Weekly Usage Day**    | View detailed daily usage breakdown         |

---

## 🎙️ Voice Commands

Use voice in the search bar for fast interactions.

| Command Example           | Action                                         |
|---------------------------|------------------------------------------------|
| `Call Swaroop`            | Starts a phone call                           |
| `Message Swaroop`         | Opens SMS app                                 |
| `WhatsApp Swaroop`        | Opens WhatsApp chat                           |
| `Send hi to Swaroop`      | Sends WhatsApp message                        |
| `Search weather tomorrow` | Google search                                 |
| `Open YouTube`            | Launches YouTube                              |
| `Uninstall WhatsApp`      | Opens uninstall screen                        |
| `Bangalore to Mysore`     | Opens Google Maps with route                  |

> **Permissions Required**: Contacts, Phone, SMS

---

## 🔐 Privacy & Permissions

Launch requires **minimal permissions**:
- **Contacts** → for contact search and quick actions
- **SMS** → to send messages
- **Phone** → to make calls
- **Storage** → for notes, wallpapers, and backups
- **Usage Stats** → for app usage tracking and sorting (optional)
- **Notification Access** → for notifications widget (optional)
- **Location** → for weather widget location-based forecasts (optional)
- **Audio** → for voice search functionality (optional)
- **Camera** → for shake-to-toggle torch feature (optional)

All permissions are optional except for core launcher functionality. You can use the launcher with minimal permissions and enable additional features as needed.

---

## 🚀 Getting Started

1. Install the app  
2. Grant requested permissions  
3. Set "Launch" as your default launcher  
4. Choose your preferred layout (Grid/List)  
5. Add your favorite apps to the dock  
6. Start exploring and customizing  

---

## 🛠️ Development

### Prerequisites

- **Android Studio** (Hedgehog or later recommended)
- **JDK 11** or higher
- **Android SDK** with API level 24+ (minimum) and API level 35 (target)
- **Gradle** 8.8.0+ (included via wrapper)

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/guruswarupa/launch.git
   cd launch
   ```

2. Open the project in Android Studio

3. Sync Gradle files and wait for dependencies to download

4. Build the project:
   ```bash
   ./gradlew build
   ```

5. Run on an emulator or device:
   ```bash
   ./gradlew installDebug
   ```

For detailed development setup instructions, see [DEVELOPMENT.md](DEVELOPMENT.md).

### Project Structure

```
launch/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/guruswarupa/launch/  # Main source code
│   │   │   ├── res/                          # Resources (layouts, drawables, etc.)
│   │   │   └── AndroidManifest.xml
│   │   ├── androidTest/                      # Instrumented tests
│   │   └── test/                             # Unit tests
│   └── build.gradle.kts                      # App-level build config
├── gradle/
│   └── libs.versions.toml                    # Dependency versions
└── build.gradle.kts                           # Project-level build config
```

### Key Technologies

- **Kotlin** - Primary programming language
- **Jetpack Compose** - Modern UI toolkit
- **AndroidX** - Android support libraries
- **Room** - Local database
- **Material Design 3** - UI components

### Building for Release

```bash
./gradlew assembleRelease
```

The APK will be generated at `app/build/outputs/apk/release/app-release.apk`

---

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details on:

- How to report bugs
- How to suggest features
- How to submit pull requests
- Code style and standards
- Development workflow

### Quick Start for Contributors

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Commit your changes (`git commit -m 'Add some amazing feature'`)
5. Push to the branch (`git push origin feature/amazing-feature`)
6. Open a Pull Request

For more details, see [CONTRIBUTING.md](CONTRIBUTING.md).

---

## 📄 License

This project is licensed under the MIT License with additional restrictions. See the [LICENSE](LICENSE) file for details.

**Note**: This software may not be sold, rebranded, or used for commercial purposes without explicit permission.
