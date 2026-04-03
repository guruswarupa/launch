// Documentation content data
export interface DocSection {
  heading: string;
  content: string;
}

export interface DocPage {
  title: string;
  sections: DocSection[];
}

export const documentationContent: Record<string, DocPage> = {
  introduction: {
    title: 'Introduction to Launch',
    sections: [
      {
        heading: 'What is Launch?',
        content: `Launch is a clean, efficient, and minimalist Android launcher built for focus and productivity. It replaces your default home screen with a powerful interface that keeps your most important apps and features at your fingertips.`
      },
      {
        heading: 'Key Benefits',
        content: `• **Productivity First**: Focus mode, Pomodoro timer, and app usage tracking
• **Privacy Focused**: 100% open source, no ads, no tracking
• **Highly Customizable**: Themes, layouts, workspaces, and more
• **Feature Rich**: Unified search, voice commands, advanced sensors
• **Lightweight**: Fast performance with minimal battery drain`
      },
      {
        heading: 'System Requirements',
        content: `• Android 8.0 (API level 26) or higher
• 100 MB free storage space
• 2 GB RAM recommended
• Internet connection for web features`
      }
    ]
  },
  
  installation: {
    title: 'Installation & Setup',
    sections: [
      {
        heading: 'Download & Install',
        content: `**From Google Play Store**
1. Open Play Store on your device
2. Search for "Launch - Productive Launcher"
3. Tap "Install" and wait for download
4. Tap "Open" to launch

**Direct APK Download**
1. Download APK from GitHub releases
2. Enable "Install unknown apps" for your browser
3. Open downloaded APK file
4. Follow installation prompts`
      },
      {
        heading: 'Initial Setup',
        content: `**Step 1: Set as Default Launcher**
When you first open Launch, you'll be prompted to set it as your default home app:
1. Press the home button
2. Select "Launch" from the list of launchers
3. Tap "Always" to make it default

**Step 2: Grant Permissions**
Launch will guide you through required permissions:
- Usage stats permission (for app usage tracking)
- Notification access (for notification widget)
- Accessibility service (for screen lock features)

**Step 3: Customize Your Layout**
- Choose grid size (apps per row)
- Select preferred theme
- Configure initial widgets`
      },
      {
        heading: 'Migration Tips',
        content: `• Your app layout and folders will be preserved
• Widgets need to be re-added manually
• Take time to explore gesture controls
• Check out workspaces for organizing apps`
      }
    ]
  },
  
  permissions: {
    title: 'Required Permissions',
    sections: [
      {
        heading: 'Why Permissions Matter',
        content: `Launch requests several permissions to provide full launcher functionality. All permissions are optional where possible, and data is stored locally on your device.`
      },
      {
        heading: 'Essential Permissions',
        content: `**Query All Packages**
Allows Launch to display and search your installed apps. This is required for any third-party launcher.

**Usage Stats Access**
Enables app usage tracking, screen time monitoring, and app frequency sorting. Used for smart app management features.

**Home Launcher**
Sets Launch as your default home screen. You can change this in Android settings anytime.`
      },
      {
        heading: 'Optional Permissions',
        content: `**Contacts Access**
Enables contact search in unified search bar and quick call actions from search results.

**Notification Access**
Enables integration with active media sessions for the media controller widget.

**Accessibility Service**
Enables screen locking with double-tap gesture and fingerprint unlock integration.

**Device Administrator**
Allows turning off screen with double-tap on search bar.

**Storage Access**
Required for notes, wallpaper backups, and file sharing features.

**Camera**
Used for QR scanner in control center.

**Microphone**
Enables voice commands for hands-free operation.

**Phone/SMS**
Provides calling and messaging shortcuts from contacts search.

**Activity Recognition**
Powers step counter and physical activity tracking widgets.

**Body Sensors**
Accesses heart rate and other fitness sensor data when available.`
      },
      {
        heading: 'Managing Permissions',
        content: `You can manage permissions anytime:
1. Open Launch Settings
2. Navigate to "Permissions"
3. Toggle individual permissions
4. Review what each permission enables

Note: Core launcher functionality works independently even if you decline optional permissions.`
      }
    ]
  },

  'search-bar': {
    title: 'Unified Search Bar',
    sections: [
      {
        heading: 'Overview',
        content: `The unified search bar at the top of your home screen is the command center of Launch. It provides instant access to apps, contacts, files, web searches, and more—all from a single input field.`
      },
      {
        heading: 'Search Modes',
        content: `**All** (Default)
Searches across all sources simultaneously: apps, contacts, files, web, Maps, Play Store, and YouTube.

**Apps Only**
Quickly find and launch installed applications. Results show app icons and names.

**Contacts Only**
Search your contact list. Tap results to call or message.

**Files Only**
Find documents, images, videos, and other files stored on your device.

**Maps**
Search locations directly in Google Maps. Shows nearby places and addresses.

**Web**
Performs Google search for web results. Opens in your default browser.

**Play Store**
Search for apps, games, and content on Google Play Store.

**YouTube**
Search YouTube videos and channels directly.`
      },
      {
        heading: 'Features',
        content: `**Instant Auto-complete**
As you type, suggestions appear showing matching results from all enabled search sources.

**Quick Calculator**
Type math expressions directly (e.g., "25*4+10") and get instant results without opening calculator app.

**Recent Searches**
Shows your recent searches for quick re-access. Tap history icon to view.

**Voice Input**
Tap microphone icon to use voice search instead of typing.

**Long Press Action**
Long press the search bar to quickly open Google in your browser.`
      },
      {
        heading: 'Tips',
        content: `• Start typing immediately when home screen is focused
• Use specific search modes to narrow results
• Calculator supports basic and scientific operations
• Search results update in real-time as you type
• Swipe search results left/right to navigate`
      }
    ]
  },

  'focus-mode': {
    title: 'Focus Mode & Pomodoro',
    sections: [
      {
        heading: 'Focus Mode',
        content: `Focus Mode helps you stay productive by limiting distractions. When enabled, it can hide distracting apps, enable Do Not Disturb, and keep you on task.`
      },
      {
        heading: 'Setting Up Focus Mode',
        content: `**Configure Focus Sessions**
1. Long press the Focus icon (⏱) in your widget area
2. Select apps to block during focus sessions
3. Choose duration: 15 min, 30 min, 45 min, 1h, 2h, 3h, or 4h
4. Enable "Do Not Disturb" if desired
5. Optionally enable "Lock Drawer" to prevent accessing hidden apps

**During Focus Mode**
- Blocked apps remain accessible but won't appear in main view
- DND silences notifications (if enabled)
- A subtle indicator shows focus mode is active
- Tap focus icon to end session early`
      },
      {
        heading: 'Pomodoro Timer',
        content: `The built-in Pomodoro technique automates work/break cycles:

**How It Works**
1. Start a focus session with Pomodoro enabled
2. Work for your chosen duration (typically 25 min)
3. Timer automatically starts a short break (5 min)
4. After 4 cycles, take a longer break (15-30 min)

**Customization**
- Adjust work session length (15m-4h)
- Set break duration (3m-30m)
- Enable/disable sound notifications
- Choose whether to auto-start breaks

**Benefits**
• Maintains productivity rhythm
• Prevents burnout with regular breaks
• Tracks completed pomodoros
• Builds healthy work habits`
      }
    ]
  },

  'productivity-widgets': {
    title: 'Productivity Widgets',
    sections: [
      {
        heading: 'All-in-One Productivity Hub',
        content: `Launch includes a comprehensive suite of productivity widgets that can be mixed and matched on your home screen. Each widget is fully customizable and functional.`
      },
      {
        heading: 'Calculator Widget',
        content: `**Modes**
• **Basic**: Standard arithmetic operations (+, -, ×, ÷)
• **Scientific**: Trigonometry, logarithms, exponents, roots
• **Converter**: Length, weight, temperature, currency conversion

**Features**
• Calculation history tape
• Copy/paste support
• Landscape mode for scientific calculator
• Persistent across sessions`
      },
      {
        heading: 'Todo List Widget',
        content: `**Task Management**
• Add tasks with due times
• Set priority levels (High, Medium, Low)
• Create recurring tasks (daily, weekly, monthly)
• Mark tasks complete with visual feedback
• View completion statistics

**Organization**
• Sort by priority, due date, or creation date
• Filter active/completed tasks
• Search tasks by keyword
• Color-coded priority indicators`
      },
      {
        heading: 'Weather Widget',
        content: `**Powered by Open-Meteo**
• Location-based forecasts
• Current temperature and conditions
• Hourly and daily forecasts
• Weather icons (sunny, cloudy, rain, etc.)
• Feels-like temperature
• Humidity and wind speed

**Customization**
• Choose Celsius or Fahrenheit
• Select update frequency
• Show/hide additional details`
      },
      {
        heading: 'Countdown Timer Widget',
        content: `**Features**
• Multiple timers simultaneously
• Set duration from 1 minute to 24 hours
• Calendar event integration (auto-suggests meeting times)
• Persistent timers survive app restarts
• Visual progress ring
• Audio alerts when complete

**Use Cases**
• Cooking timers
• Meeting countdowns
• Study sessions
• Workout intervals`
      }
    ]
  },

  // Additional feature documentation continues similarly...
  
  'app-lock': {
    title: 'App Lock & Timers',
    sections: [
      {
        heading: 'App Lock',
        content: `Protect sensitive apps with PIN or biometric authentication. Locked apps require verification before opening.`
      },
      {
        heading: 'Setting Up App Lock',
        content: `**Initial Setup**
1. Long press any app icon
2. Select "Lock App" from context menu
3. Set a 4-digit PIN (or use biometrics if available)
4. Confirm PIN

**Unlocking Apps**
• Tap locked app icon
• Enter PIN or use fingerprint/face unlock
• App launches after successful authentication

**Managing Locked Apps**
• Settings > Privacy > App Lock
• See list of all locked apps
• Unlock individual apps or change PIN
• Enable/disable biometric option`
      },
      {
        heading: 'App Timers',
        content: `**Daily Usage Limits**
Set time limits for apps to prevent overuse:

1. Long press app icon
2. Select "Set Time Limit"
3. Choose daily limit (15min, 30min, 1h, 2h, 4h, unlimited)
4. App icon dims when limit reached
5. Optional: Block app completely when limit exceeded

**Features**
• Daily reset at midnight
• Usage tracking graph
• Warning notifications before limit
• Override option with confirmation
• Separate weekday/weekend limits`
      }
    ]
  },

  'hidden-apps': {
    title: 'Hidden Apps & Workspaces',
    sections: [
      {
        heading: 'Hidden Apps',
        content: `Keep rarely-used or private apps out of sight without uninstalling them. Hidden apps remain functional and can be accessed when needed.`
      },
      {
        heading: 'Hiding Apps',
        content: `**Method 1: Long Press**
1. Long press app icon in app drawer
2. Select "Hide App"
3. App moves to hidden section

**Method 2: Settings**
1. Go to Settings > Appearance > Hidden Apps
2. Toggle apps to hide/unhide
3. Changes apply immediately

**Accessing Hidden Apps**
• Scroll to bottom of app drawer
• Tap "Show hidden apps"
• Authenticate if app lock enabled
• Tap hidden app to unhide or launch`
      },
      {
        heading: 'Workspaces',
        content: `**What Are Workspaces?**
Workspaces are filtered views showing only selected apps. Perfect for separating work/personal apps or creating themed collections.

**Creating a Workspace**
1. Long press home screen
2. Tap "Create Workspace"
3. Name your workspace (e.g., "Work", "Games", "Social")
4. Select apps to include
5. Choose icon color/theme

**Using Workspaces**
• Swipe left/right on workspace tabs
• Each workspace shows only its assigned apps
• Apps can belong to multiple workspaces
• Switch workspaces instantly

**Managing Workspaces**
• Edit: Long press workspace name
• Delete: Remove workspace (apps return to main view)
• Reorder: Drag workspace tabs
• Rename: Tap workspace name twice`
      }
    ]
  },

  'control-center': {
    title: 'Control Center Shortcuts',
    sections: [
      {
        heading: 'Quick Settings Panel',
        content: `The Control Center provides instant access to frequently-used toggles and settings without digging through menus.`
      },
      {
        heading: 'Accessing Control Center',
        content: `**Swipe Down**
Swipe down from top edge of home screen (like notification shade on stock Android)

**Swipe Up**
On tablets, swipe up from bottom edge

**Keyboard Shortcut**
Press Ctrl+Space when using keyboard with Android`
      },
      {
        heading: 'Available Toggles',
        content: `**Connectivity**
• Wi-Fi on/off
• Mobile data
• Bluetooth
• Airplane mode
• NFC
• Hotspot

**Sound & Display**
• DND (Do Not Disturb)
• Silent/Vibrate/Ring profiles
• Brightness slider
• Night mode / Blue light filter
• Auto-rotate lock

**Utilities**
• Flashlight/Torch
• QR Scanner
• Screenshot
• Screen recording
• Cast/Screen mirroring
• Location/GPS

**Quick Actions**
• Calculator
• Camera
• Voice recorder
• Notes
• Recent apps
• Power menu`
      },
      {
        heading: 'Customizing Control Center',
        content: `**Reorder Toggles**
1. Settings > Control Center
2. Long press and drag toggles
3. Arrange in preferred order
4. Top row shows primary toggles

**Add/Remove Toggles**
• Tap "+" to add more toggles
• Tap "-" to remove unused ones
• Some system toggles cannot be removed

**Appearance**
• Choose grid size (3x3, 4x4, 5x5)
• Select toggle style (rounded, square)
• Enable/disable labels`
      }
    ]
  },

  'app-management': {
    title: 'Smart App Management',
    sections: [
      {
        heading: 'Intelligent App Organization',
        content: `Launch automatically organizes your apps based on usage patterns, making it easier to find what you need.`
      },
      {
        heading: 'Usage-Based Sorting',
        content: `**Most Used Apps**
Your frequently opened apps appear at the top of the app drawer for quick access.

**Recently Added**
Newly installed apps show with a "New" badge for easy identification.

**Rarely Used Group**
Apps you haven't opened in 30+ days are grouped alphabetically in a separate section to reduce clutter.

**Adaptive Learning**
The launcher learns from your habits and adjusts app positions throughout the day based on typical usage patterns.`
      },
      {
        heading: 'Weekly Usage Statistics',
        content: `**Usage Graph**
Visual representation of your app usage over the past week:
• Total screen time
• Most used apps by time spent
• Number of unlocks per day
• Peak usage hours

**Interactive Pie Charts**
Tap the graph to see detailed breakdown:
• By category (Social, Productivity, Games, etc.)
• By individual app
• Day vs night usage
• Weekday vs weekend comparison`
      },
      {
        heading: 'Quick App Actions',
        content: `**Long Press Menu**
Long press any app icon reveals:
• **Uninstall**: Remove app (with confirmation)
• **Share APK**: Send app installer to others
• **App Info**: Open Android app info screen
• **Lock**: Require PIN to open
• **Hide**: Move to hidden apps
• **Add to Favorites**: Pin to top
• **Set Time Limit**: Configure daily usage cap
• **Remove from Workspace**: If applicable

**Drag & Drop**
• Rearrange apps in grid
• Create folders by dragging app onto another
• Move apps between pages`
      }
    ]
  },

  'voice-commands': {
    title: 'Voice Commands',
    sections: [
      {
        heading: 'Hands-Free Control',
        content: `Use your voice to control Launch, make calls, send messages, and more—no touching required.`
      },
      {
        heading: 'Activating Voice Commands',
        content: `**Methods to Activate**
1. Say "Hey Launch" (always listening when enabled)
2. Tap microphone icon in search bar
3. Long press home button (if configured)
4. Shake device twice (optional gesture)

**Setup**
1. Settings > Voice Commands
2. Enable "Hey Launch" hotword detection
3. Train voice model (recommended)
4. Configure sensitivity`
      },
      {
        heading: 'Available Commands',
        content: `**App Control**
• "Open [app name]"
• "Launch [app name]"
• "Close [app name]"
• "Show me my apps"

**Calling & Messaging**
• "Call [contact name]"
• "Send message to [contact]"
• "Text [contact] saying [message]"
• "Read my messages"

**Search**
• "Search for [query]"
• "Look up [topic]"
• "Find [file type] files"
• "Search YouTube for [video]"

**System Control**
• "Turn on flashlight"
• "Enable WiFi"
• "Set brightness to 50%"
• "Take a screenshot"
• "Show notifications"

**Shortcuts**
• "Go home"
• "Go back"
• "Show recent apps"
• "Lock screen"`
      },
      {
        heading: 'Privacy & Accuracy',
        content: `**Privacy**
• Voice processing happens on-device when possible
• No voice recordings sent to servers
• Disable hotword detection anytime
• Microphone permission required

**Improving Accuracy**
• Speak clearly and naturally
• Reduce background noise
• Train voice model during setup
• Update contact names pronunciation`
      }
    ]
  },

  'finance-tracker': {
    title: 'Finance Tracker',
    sections: [
      {
        heading: 'Personal Finance Management',
        content: `Track income, expenses, and savings right from your home screen with the built-in finance widget.`
      },
      {
        heading: 'Getting Started',
        content: `**Adding Transactions**
1. Tap the Finance widget
2. Choose "Income" or "Expense"
3. Enter amount
4. Select category (Food, Transport, Salary, etc.)
5. Add note (optional)
6. Tap Save

**Categories**
Pre-configured categories include:
• Income: Salary, Freelance, Investments, Gifts
• Expenses: Food, Transport, Shopping, Bills, Entertainment, Health
• Custom: Create your own categories`
      },
      {
        heading: 'Features',
        content: `**Transaction History**
• View recent transactions list
• Filter by date range
• Search by keyword or amount
• Edit or delete entries

**Monthly Overview**
• Total income vs expenses
• Savings calculation
• Category-wise spending breakdown
• Month-over-month comparison

**Budgets**
• Set monthly budget per category
• Get alerts when approaching limit
• Track budget progress visually

**Data Export**
• Export to CSV for spreadsheet analysis
• Email reports to yourself
• Backup and restore data`
      },
      {
        heading: 'Tips',
        content: `• Log transactions immediately for accuracy
• Review weekly spending every Sunday
• Set realistic monthly budgets
• Use notes to remember purchase details
• Export data monthly for tax records`
      }
    ]
  },

  'sensors': {
    title: 'Advanced Sensors',
    sections: [
      {
        heading: 'Hardware Sensor Integration',
        content: `Launch leverages your device's built-in sensors to provide useful real-time information through dedicated widgets.`
      },
      {
        heading: 'Compass Widget',
        content: `**Features**
• Real-time directional heading (0-360°)
• Cardinal directions (N, NE, E, SE, S, SW, W, NW)
• Visual compass rose
• Accuracy indicator

**Usage**
• Hold device flat for accurate reading
• Calibrate by moving in figure-8 motion
• Tap to toggle widget on/off
• Shows magnetic north

**Note**: Requires magnetometer sensor (not available on all devices)`
      },
      {
        heading: 'Pressure Sensor',
        content: `**Displays**
• Atmospheric pressure in hPa/mbar
• Altitude estimation in meters
• Pressure trend (rising/falling/stable)

**Applications**
• Weather prediction aid (falling pressure often precedes storms)
• Altitude tracking for hiking
• Indoor positioning assistance

**Accuracy**: ±1 hPa typically`
      },
      {
        heading: 'Temperature Sensor',
        content: `**Monitors**
• Ambient air temperature (°C/°F)
• Device temperature (CPU/battery)
• Historical temperature graph

**Limitations**
• Affected by device heat
• Best accuracy when screen off briefly
• Not all devices have ambient temp sensor`
      },
      {
        heading: 'Noise Decibel Meter',
        content: `**Real-Time Analysis**
• Current noise level in dB
• Noise level classification:
  - Quiet (< 50 dB)
  - Moderate (50-70 dB)
  - Loud (70-90 dB)
  - Very Loud (> 90 dB)
• Visual decibel meter
• Peak hold indicator

**Use Cases**
• Monitor workplace noise safety
• Check concert/event volume levels
• Sleep environment optimization

**Calibration**: Adjust baseline in settings for your environment`
      },
      {
        heading: 'Physical Activity Tracking',
        content: `**Step Counter**
• Daily step count
• Distance traveled (km/miles)
• Calories burned estimate
• Weekly/monthly trends

**Workout Detection**
• Automatic activity recognition
• Walking, running, cycling detection
• Workout session logging

**Integration**
• Syncs with Google Fit (optional)
• Historical data charts
• Goal setting and achievements`
      }
    ]
  },

  'torch': {
    title: 'Shake to Toggle Torch',
    sections: [
      {
        heading: 'Quick Flashlight Access',
        content: `Instantly turn your flashlight on or off by shaking your device twice—no need to fumble with buttons or widgets.`
      },
      {
        heading: 'How It Works',
        content: `**Activation**
1. Shake device twice in quick succession
2. Torch turns on/off immediately
3. Visual feedback shows torch status
4. Works even when screen is off

**Sensitivity Settings**
1. Go to Settings > Gestures > Shake to Torch
2. Adjust sensitivity: Low, Medium, High
3. Test shake pattern
4. Enable/disable as needed`
      },
      {
        heading: 'Smart Features',
        content: `**Screen-On Requirement**
By default, shake gesture only works when screen is on to prevent accidental activation. Option to enable with screen off available.

**Background Operation**
Shake detection runs efficiently in background with minimal battery impact (< 1% per day).

**Timeout Options**
• Auto-off after 1/2/5/10 minutes
• Manual off only (default)
• Double shake to toggle (same gesture)`
      },
      {
        heading: 'Battery Efficiency',
        content: `• Uses hardware sensors only
• No continuous CPU wake locks
• Optimized shake detection algorithm
• Can be disabled to save power`
      }
    ]
  },

  'apk-sharing': {
    title: 'APK Sharing',
    sections: [
      {
        heading: 'Share Installed Apps',
        content: `Easily share installed applications with friends via Bluetooth, email, messaging apps, or any file-sharing method.`
      },
      {
        heading: 'Sharing Methods',
        content: `**From App Context Menu**
1. Long press app icon in drawer
2. Select "Share APK"
3. Choose sharing method:
   - Bluetooth
   - Gmail/Email
   - WhatsApp/Telegram
   - Google Drive
   - Any file-sharing app

**From Dock**
1. Long press dock app icon
2. Tap "Share" option
3. Select recipient app

**Batch Sharing**
1. Settings > Apps > Share Multiple APKs
2. Select multiple apps
3. Share as ZIP archive`
      },
      {
        heading: 'Features',
        content: `**APK Extraction**
• Extracts original APK from system
• Preserves app signature
• Includes split APKs for modern apps
• Shows APK size before sharing

**File Management**
• Saved to /Downloads/Launch APKs/
• Named as "AppName_version.apk"
• Option to delete after sharing
• Browse shared APKs history

**Compatibility Check**
• Warns if recipient device may not support APK
• Shows minimum Android version required
• Indicates architecture (ARM/x86)`
      },
      {
        heading: 'Legal Notice',
        content: `• Only share apps you have rights to distribute
• Respect app licenses and copyrights
• Don't share paid apps without permission
• Personal use recommended`
      }
    ]
  },

  'gestures': {
    title: 'Gesture Controls',
    sections: [
      {
        heading: 'Intuitive Gesture Interface',
        content: `Launch provides extensive gesture support for quick actions and efficient navigation throughout the launcher.`
      },
      {
        heading: 'App Icon Gestures',
        content: `**Tap**
• Single tap: Open app
• Double tap: Quick action (configurable)

**Long Press**
Opens context menu with options:
• App info
• Uninstall
• Share APK
• Lock app
• Hide app
• Add to favorites
• Set time limit
• Remove from workspace
• Uninstall (system apps grayed out)`
      },
      {
        heading: 'Widget Gestures',
        content: `**Time/Date Widget**
• Tap time: Open Clock app
• Tap date: Open Calendar app

**Search Bar**
• Long press: Open Google in browser
• Type: Instant search/calculator
• Tap mic: Voice search

**Focus Mode Icon**
• Tap: Toggle focus mode
• Long press: Configure focus settings

**Notification Widget**
• Swipe left: Dismiss notification
• Tap: Open related app
• Tap badge: Expand notifications

**Usage Graph**
• Tap day: View detailed breakdown
• Swipe: Navigate weeks`
      },
      {
        heading: 'Navigation Gestures',
        content: `**Home Screen Swiping**
• Swipe left/right: Change pages
• Swipe up: Open app drawer (if enabled)
• Swipe down: Control center
• Pinch out: Overview mode
• Pinch in: Edit mode

**Dock Gestures**
• Swipe up on dock: Show all dock apps
• Long press dock app: Remove/rename

**Edge Swipes**
• Swipe from left edge: Previous page
• Swipe from right edge: Next page
• Swipe from top: Notifications`
      },
      {
        heading: 'Special Gestures',
        content: `**Shake Gestures**
• Shake twice: Toggle torch
• Shake phone: Activate voice commands (optional)

**Flip to DND**
• Place phone face-down: Automatically enable Do Not Disturb
• Configurable in Settings > Gestures

**Double Tap**
• Double tap search bar: Turn off screen
• Double tap empty space: Lock screen (requires accessibility permission)

**Drawing Gestures**
• Draw 'c': Open camera
• Draw 'w': Open WhatsApp
• Draw 'm': Open messages
(Configure in Settings > Gestures > Draw letters)`
      }
    ]
  },

  'customization': {
    title: 'Deep Customization',
    sections: [
      {
        heading: 'Make Launch Yours',
        content: `Every aspect of Launch can be customized to match your preferences and workflow.`
      },
      {
        heading: 'Layout Options',
        content: `**Grid Configuration**
• Apps per row: 3, 4, 5, or 6
• Icon size: Small, Medium, Large, Extra Large
• Page count: Unlimited home screen pages
• Dock rows: 1 or 2 rows

**Display Mode**
• Grid view (default)
• List view (single column)
• Compact mode (smaller spacing)
• Minimalist mode (icons only, no labels)`
      },
      {
        heading: 'Themes & Appearance',
        content: `**Built-in Themes**
Choose from preset themes:
• Light
• Dark
• AMOLED Black
• Material You (dynamic colors)
• Custom gradient themes

**Custom Theme Builder**
• Background color/opacity
• Text color and font
• Icon pack support (1000+ packs compatible)
• Accent color selection
• Shadow intensity
• Corner radius (rounded vs square icons)

**Wallpapers**
• Static images
• Live wallpapers supported
• Wallpaper picker with categories
• Blur effect toggle
• Darken overlay option`
      },
      {
        heading: 'Font Customization',
        content: `**Font Styles**
• System default
• Roboto (multiple weights)
• Downloadable fonts from Google Fonts
• Custom .ttf/.otf files

**Font Sizes**
• Tiny, Small, Medium, Large, Extra Large
• Preview before applying
• Separate size for labels vs widgets

**Text Effects**
• Bold, Italic
• Uppercase toggle
• Letter spacing
• Shadow on/off`
      },
      {
        heading: 'Behavior Settings',
        content: `**Animation Speed**
• Animation scale: 0.5x, 1x, 1.5x, 2x
• Transition effects: Fade, Slide, Zoom, Cube, Stack

**Scrolling**
• Bounce effect on/off
• Infinite scroll toggle
• Scroll speed adjustment

**Clock Format**
• 12-hour (AM/PM)
• 24-hour format
• Show/hide seconds
• Timezone selection`
      }
    ]
  },

  'privacy-dashboard': {
    title: 'Privacy Dashboard',
    sections: [
      {
        heading: 'Complete Privacy Control',
        content: `Monitor and manage which apps have access to sensitive permissions on your device.`
      },
      {
        heading: 'Permission Overview',
        content: `**Tracked Permissions**
• Camera access
• Microphone access
• Location (GPS)
• Contacts
• Storage/File access
• Phone/SMS
• Calendar
• Call logs
• Media/Audio/Video

**Visual Indicators**
• Green dot: Permission currently in use
• Yellow dot: Used in last 24 hours
• Gray dot: Not used recently
• Red outline: Suspicious usage pattern`
      },
      {
        heading: 'Features',
        content: `**One-Tap Management**
• Revoke permissions directly from dashboard
• Bulk revoke by permission type
• Set permission schedules (time-based access)
• Get notified when apps access sensitive data

**Access Timeline**
• See when each app last used a permission
• View frequency of access
• Identify unusual patterns (e.g., flashlight app accessing contacts)

**Privacy Report**
• Weekly summary of permission usage
• Export privacy audit log
• Compare with similar apps
• Security recommendations`
      },
      {
        heading: 'Additional Privacy Tools',
        content: `**Permission Auto-Reset**
Automatically revoke permissions from apps you haven't used in months (Android 11+ feature enhanced by Launch).

**Incognito Mode**
• Hide recent apps from recents
• Disable usage tracking temporarily
• Clear search history one-tap

**App Ops Integration**
Advanced users can fine-tune permissions beyond standard Android settings (requires ADB setup).`
      }
    ]
  },

  'web-apps': {
    title: 'Web Apps Support',
    sections: [
      {
        heading: 'Progressive Web App Integration',
        content: `Add any website as a standalone app with custom icons, full-screen mode, and ad-blocking capabilities.`
      },
      {
        heading: 'Adding Web Apps',
        content: `**Method 1: From Browser**
1. Visit website in Chrome/Firefox
2. Tap menu (⋮) > "Add to Home screen"
3. Name the web app
4. Tap "Add"
5. Icon appears in app drawer

**Method 2: From Launch**
1. Long press empty space
2. Select "Add Web App"
3. Enter name and URL
4. Choose custom icon (optional)
5. Configure settings
6. Tap "Add"`
      },
      {
        heading: 'Features',
        content: `**WebView Customization**
• Built-in WebView with full browsing
• Custom address bar
• Full-screen mode (hides URL bar)
• JavaScript enabled/disabled
• Cookie management
• Cache controls

**Ad Blocking**
• Built-in ad blocker for web apps
• Blocks banners, popups, video ads
• Whitelist favorite sites
• Custom filter lists

**Standalone Experience**
• Opens in separate window
• Appears in recent apps
• Independent from browser
• Custom splash screen
• Orientation lock (portrait/landscape)`
      },
      {
        heading: 'Managing Web Apps',
        content: `**Edit Settings**
Long press web app icon > Settings:
• Change name
• Update URL
• Select new icon
• Clear cache/data
• Enable/disable ad block
• Set homepage

**Organize**
• Add to favorites
• Include in workspaces
• Hide from main view
• Create folders with other apps

**Remove**
Long press > "Remove Web App" deletes from launcher (doesn't affect browser bookmarks).`
      }
    ]
  },

  'speed-test': {
    title: 'Network Speed Test',
    sections: [
      {
        heading: 'Built-In Network Testing',
        content: `Measure your internet speed directly from the home screen widget without installing additional apps.`
      },
      {
        heading: 'Running Speed Test',
        content: `**Quick Test**
1. Tap the Network Stats widget
2. Tap "Run Speedtest"
3. Wait for test to complete (~15 seconds)
4. View results instantly

**Detailed Test**
• Download speed (Mbps)
• Upload speed (Mbps)
• Ping/Latency (ms)
• Jitter (stability metric)
• Server location
• Connection type (WiFi/4G/5G)`
      },
      {
        heading: 'Features',
        content: `**Real-Time Monitoring**
• Current network speed in status bar
• WiFi signal strength indicator
• Data usage tracker
• Network type detector (2G/3G/4G/5G/WiFi)

**History & Trends**
• Speed test history with timestamps
• Daily/weekly/monthly averages
• Speed comparison graphs
• Peak performance tracking
• Export test results

**Server Selection**
• Auto-select nearest server
• Manual server choice
• Test against multiple servers
• Save favorite servers`
      },
      {
        heading: 'Data Usage Tracking',
        content: `**Mobile Data**
• Daily usage limit setting
• Warning at threshold
• Auto-disable data at limit
• Per-app usage breakdown

**WiFi Usage**
• Estimate data consumed
• Track connected time
• Signal quality history

**Billing Cycle**
• Set cycle reset date
• Projected usage estimate
• Overage alerts`
      }
    ]
  },

  'github-widget': {
    title: 'GitHub Contributions Widget',
    sections: [
      {
        heading: 'Track Your Coding Activity',
        content: `Visual display of your GitHub contribution graph, stats, and achievements right on your home screen.`
      },
      {
        heading: 'Setup',
        content: `**Connect GitHub Account**
1. Tap GitHub widget
2. Enter GitHub username
3. Authorize Launch (OAuth)
4. Widget displays your contributions

**Privacy**
• Read-only access (no write permissions)
• Username stored locally
• Disconnect anytime
• No data shared with third parties`
      },
      {
        heading: 'Features',
        content: `**Contribution Graph**
• Classic GitHub heatmap visualization
• Color-coded by contribution count
• Scrollable yearly view
• Tap days to see details

**Statistics**
• Total contributions (this year)
• Current streak (consecutive days)
• Longest streak achieved
• Contribution breakdown by:
  - Commits
  - Pull requests
  - Issues opened
  - Reviews completed

**Achievements**
• Badges for milestones
• Streak celebrations
• Top contributor alerts
• Year-end summary`
      },
      {
        heading: 'Widget Sizes',
        content: `**Small (2x2)**
• Current streak
• Total contributions

**Medium (3x2)**
• Mini contribution graph (last 3 months)
• Key stats

**Large (4x3)**
• Full year graph
• Complete statistics
• Achievement showcase`
      }
    ]
  },

  'gestures-guide': {
    title: 'Gestures Guide',
    sections: [
      {
        heading: 'Complete Gesture Reference',
        content: `Launch provides an extensive gesture system for efficient navigation and quick actions throughout the launcher.`
      },
      {
        heading: 'Home Screen Gestures',
        content: `**Swiping**
• Swipe left/right: Navigate between home screen pages
• Swipe up from bottom: Open app drawer
• Swipe down from top: Open Control Center
• Pinch out (two fingers spreading): Enter overview/edit mode
• Pinch in (two fingers together): Exit edit mode

**Edge Swipes**
• Swipe from left edge: Go to previous page
• Swipe from right edge: Go to next page
• Swipe from top edge: Quick settings panel`
      },
      {
        heading: 'App Icon Gestures',
        content: `**Tap Gestures**
• Single tap: Open/launch app
• Double tap: Quick action (configurable per app)

**Long Press Menu**
Opens context menu with options:
• App info: View Android app details
• Uninstall: Remove app from device
• Share APK: Send app installer to others
• Lock app: Require PIN/biometric to open
• Hide app: Move to hidden apps section
• Add to favorites: Pin to top of drawer
• Set time limit: Configure daily usage cap
• Remove from workspace: If in a workspace
• Rename: Change app label (system apps excluded)`
      },
      {
        heading: 'Widget Gestures',
        content: `**Time/Date Widget**
• Tap time display: Open Clock app
• Tap date display: Open Calendar app

**Search Bar**
• Long press: Open Google in browser
• Type text: Instant search or calculator
• Tap microphone: Voice search activation

**Focus Mode Icon**
• Tap: Toggle focus mode on/off
• Long press: Configure focus settings

**Notification Widget**
• Swipe left on notification: Dismiss
• Tap notification: Open related app
• Tap badge count: Expand full notifications

**Usage Statistics Graph**
• Tap specific day: View detailed breakdown
• Swipe horizontally: Navigate between weeks
• Tap category in pie chart: Filter by category`
      },
      {
        heading: 'Dock Gestures',
        content: `**Swipe Up on Dock**
Reveals all dock apps in expanded view

**Long Press Dock App**
Opens menu to:
• Remove from dock
• Rename label
• Change icon
• Rearrange position

**Drag & Drop**
• Drag apps into dock: Add to dock
• Drag out of dock: Remove from dock
• Rearrange dock icons: Reorder positions`
      },
      {
        heading: 'Special Gestures',
        content: `**Shake Gestures**
• Shake twice: Toggle flashlight/torch
• Shake phone: Activate voice commands (optional)

**Flip to DND**
Place phone face-down on surface to automatically enable Do Not Disturb mode

**Double Tap Actions**
• Double tap search bar: Turn off screen
• Double tap empty space: Lock screen (requires accessibility permission)
• Double tap app icon: Quick action shortcut

**Drawing Gestures**
Draw letter shapes to launch apps quickly:
• Draw 'c': Open Camera
• Draw 'w': Open WhatsApp
• Draw 'm': Open Messages
• Draw 'd': Open Dialer
(Configure custom letters in Settings > Gestures > Draw letters)

**Three-Finger Swipe**
• Swipe down: Take screenshot
• Swipe up: Open recent apps`
      },
      {
        heading: 'Customization',
        content: `**Gesture Settings**
Navigate to Settings > Gestures to:
• Enable/disable specific gestures
• Adjust shake sensitivity
• Configure double-tap actions
• Set custom drawing letters
• Modify swipe thresholds
• Calibrate gesture recognition

**Accessibility Options**
• Increase gesture size for easier detection
• Reduce motion for sensitive users
• Haptic feedback on gesture success
• Sound effects toggle`
      }
    ]
  },

  'settings': {
    title: 'Settings & Configuration',
    sections: [
      {
        heading: 'Complete Settings Overview',
        content: `Launch offers comprehensive customization through its settings menu. Access settings by long-pressing an empty area on the home screen or tapping the gear icon in the Control Center.`
      },
      {
        heading: 'Appearance Settings',
        content: `**Theme**
• Light mode
• Dark mode
• AMOLED Black
• Material You (dynamic colors)
• Custom gradient themes

**Grid & Layout**
• Apps per row: 3, 4, 5, or 6
• Icon size: XS, S, M, L, XL
• Page count: Unlimited
• Dock rows: 1 or 2
• Display mode: Grid, List, Compact, Minimalist

**Fonts**
• Font family selection (system fonts + custom)
• Font size: Tiny to Extra Large
• Text style: Bold, Italic, Uppercase
• Letter spacing adjustment
• Shadow effects toggle

**Colors**
• Background color/opacity
• Text color selection
• Accent color picker
• Icon pack support (1000+ packs)
• Corner radius (rounded vs square icons)

**Wallpapers**
• Static image picker
• Live wallpapers support
• Wallpaper blur effect
• Darken overlay option
• Category browsing`
      },
      {
        heading: 'Behavior Settings',
        content: `**Animations**
• Animation speed: 0.5x, 1x, 1.5x, 2x
• Transition effects: Fade, Slide, Zoom, Cube, Stack, Flip
• Scroll animation toggle

**Scrolling**
• Bounce effect on/off
• Infinite scroll toggle
• Scroll speed adjustment
• Overscroll behavior

**Clock & Date**
• 12-hour / 24-hour format
• Show/hide seconds
• Timezone selection
• Date format options
• Week start day (Monday/Sunday)

**Search Bar**
• Default search mode
• Show/hide microphone
• Calculator integration toggle
• Recent searches limit
• Clear search history

**Lock Screen**
• Double-tap to sleep
• Always show lock clock
• Lock clock style
• Shortcut customization`
      },
      {
        heading: 'Gestures & Input',
        content: `**Shake Gestures**
• Shake to torch: Enable/disable
• Sensitivity: Low, Medium, High
• Screen-on requirement toggle
• Shake to activate voice commands

**Drawing Letters**
• Enable/disable gesture drawing
• Configure custom letters (a-z)
• Assign apps to letters
• Test gesture recognition
• Sensitivity adjustment

**Edge Swipes**
• Enable/disable edge swipes
• Left edge action
• Right edge action
• Swipe width threshold

**Flip to DND**
• Enable/disable flip detection
• Sensitivity calibration
• Exclude times (e.g., nighttime)`
      },
      {
        heading: 'Workspaces & Organization',
        content: `**Workspace Management**
• Create new workspace
• Edit existing workspaces
• Delete workspace
• Reorder workspace tabs
• Workspace color themes

**Hidden Apps**
• View hidden apps list
• Unhide individual apps
• Hide additional apps
• Authentication requirements

**Folders**
• Folder naming
• Folder icon selection
• Folder color themes
• Auto-name suggestions
• Folder preview style`
      },
      {
        heading: 'Notifications & Status',
        content: `**Notification Widget**
• Enable/disable widget
• Notification count badge
• Show/hide app names
• Swipe-to-dismiss toggle
• Clear all button visibility

**Status Bar**
• Show/hide status bar
• Clock visibility
• Battery indicator style
• Network speed monitor
• Custom status icons

**DND Integration**
• Automatic DND during focus mode
• Scheduled DND times
• Allow exceptions (favorites, alarms)
• Flip-to-DND toggle`
      },
      {
        heading: 'Permissions & Security',
        content: `**Permission Manager**
• Grant required permissions
• Revoke optional permissions
• Permission explanations
• One-tap permission reset

**App Lock Settings**
• Change PIN/password
• Enable/disable biometrics
• Lock timeout duration
• Locked apps list
• Fingerprint setup

**Privacy Dashboard**
• Permission usage timeline
• Recent permission access
• Suspicious activity alerts
• Privacy report export
• Auto-reset permissions`
      },
      {
        heading: 'Backup & Restore',
        content: `**Backup Data**
• Backup layout configuration
• Save widget arrangements
• Export workspace setups
• Backup hidden apps list
• Cloud backup (Google Drive)

**Restore**
• Import backup file
• Selective restore options
• Merge with current setup
• Factory reset option

**Migration**
• Import from other launchers
• Export to share with others
• Sync across devices`
      },
      {
        heading: 'Advanced Settings',
        content: `**Developer Options**
• Debug mode toggle
• Log viewer
• Performance metrics
• Memory usage monitor
• Animation debugging

**Accessibility Service**
• Screen lock with double-tap
• Enhanced gesture detection
• Voice command enhancements
• Automation features

**Automation Rules**
• Time-based profile switching
• Location-based automation
• App-specific settings
• Conditional triggers

**About Launch**
• Version information
• Open source licenses
• Contributor credits
• Check for updates
• Report bugs
• Feature requests`
      }
    ]
  },

  'privacy-security': {
    title: 'Privacy & Security',
    sections: [
      {
        heading: 'Your Privacy Matters',
        content: `Launch is committed to protecting your privacy and security. As an open-source launcher, all code is transparent and auditable. We believe in minimal data collection and maximum user control.`
      },
      {
        heading: 'Data Collection Policy',
        content: `**What We DON'T Collect**
• No personal data transmitted to servers
• No usage tracking or analytics
• No advertising IDs collected
• No location history stored remotely
• No contact information uploaded
• No browsing history monitored

**What Stays Local**
• All app usage statistics
• Search history
• Gesture patterns
• Customization preferences
• Workspace configurations
• Hidden apps list
• All data stored only on your device

**Open Source Commitment**
• 100% open source codebase
• Available on GitHub for review
• Community-audited security
• No hidden trackers or spyware
• No ads or ad SDKs`
      },
      {
        heading: 'Permission Transparency',
        content: `**Essential Permissions**
Query All Packages: Required to display and search your installed apps (mandatory for any launcher)

Usage Stats Access: Enables app usage tracking and frequency sorting (optional, can be declined)

Home Launcher: Sets Launch as default home screen (revocable anytime)

**Optional Permissions**
Contacts Access: Powers contact search in unified search bar (decline if not needed)

Notification Access: Enables notification widget features (optional)

Accessibility Service: Enables screen locking gestures and enhanced automation (optional)

Device Administrator: Allows turning off screen with gestures (optional)

Storage Access: Required for notes, backups, and file sharing features (optional)

Camera: Used for QR scanner functionality (optional)

Microphone: Enables voice commands (optional)

Phone/SMS: Provides calling/messaging shortcuts (optional)

Activity Recognition: Powers step counter and fitness tracking (optional)

Body Sensors: Accesses heart rate and fitness data (optional)`
      },
      {
        heading: 'Security Features',
        content: `**App Lock**
• PIN protection for sensitive apps
• Biometric authentication (fingerprint/face)
• Configurable lock timeout
• Failed attempt warnings
• Emergency bypass disabled

**Privacy Dashboard**
• Real-time permission monitoring
• Visual indicators for active permissions
• Timeline of permission usage
• Suspicious activity detection
• One-tap permission revocation

**Hidden Apps**
• Conceal rarely-used or private apps
• Optional authentication to view hidden apps
• Separate from main app drawer
• Maintains full functionality

**Secure Folders**
• Create password-protected workspaces
• Isolate sensitive apps
• Hide entire workspace contents
• Quick hide gesture`
      },
      {
        heading: 'Privacy Best Practices',
        content: `**Recommended Settings**
1. Enable app lock for sensitive apps (banking, messaging, email)
2. Review permission dashboard monthly
3. Use hidden apps for rarely-used applications
4. Enable auto-reset for unused app permissions
5. Regularly clear search history if concerned
6. Use incognito mode for private browsing sessions

**Permission Management**
• Start with minimal permissions
• Grant permissions as needed
• Review permissions quarterly
• Revoke permissions from unused apps
• Watch for suspicious permission combinations

**Security Tips**
• Use strong PIN for app lock
• Enable biometric when available
• Don't share device unlock pattern
• Keep launcher updated
• Review automation rules periodically
• Be cautious with accessibility permissions`
      },
      {
        heading: 'Privacy Controls',
        content: `**Incognito Mode**
• Temporarily disable usage tracking
• Hide apps from recent list
• Clear search history one-tap
• Pause notification logging
• Auto-disable after session

**Auto-Reset Permissions**
Automatically revoke permissions from apps not used in 3+ months (Android 11+ feature enhanced by Launch)

**Permission Schedules**
Set time-based permission access:
• Camera: Only during daytime
• Microphone: Disabled at night
• Location: Work hours only

**Privacy Notifications**
Get alerted when:
• App accesses camera/microphone
• Location accessed in background
• Unusual permission activity detected
• New permission requested`
      },
      {
        heading: 'Data Portability',
        content: `**Export Your Data**
• Export layout configuration
• Backup workspace setups
• Save hidden apps list
• Export finance tracker data (CSV)
• Download usage statistics

**Import Data**
• Restore from backup files
• Import from other launchers
• Migrate to new device
• Share configurations

**Right to Deletion**
• Factory reset clears all data
• Uninstall removes local data
• No server-side data to delete
• Complete data sovereignty`
      },
      {
        heading: 'Third-Party Services',
        content: `**Integrated Services**
Weather: Powered by Open-Meteo (privacy-focused, no API key required)

GitHub Widget: OAuth authentication, read-only access, credentials stored locally

Web Apps: Uses your browser's WebView, respects website privacy policies

Voice Commands: On-device processing when possible, no cloud transmission

**External Links**
Play Store links open in official app
GitHub links open in browser or GitHub app
Downloaded APKs from trusted sources only
No affiliate tracking links`
      },
      {
        heading: 'Security Updates',
        content: `**Update Policy**
• Regular security patches
• Prompt vulnerability fixes
• Transparent changelog
• Backward compatibility maintained
• Manual update check available

**Reporting Vulnerabilities**
• GitHub Issues for bug reports
• Security@ email for sensitive issues
• Responsible disclosure policy
• Bounty program for critical findings
• Public security advisories

**Version History**
Check Settings > About for:
• Current version number
• Release date
• Security patch level
• Changelog link
• Update availability`
      }
    ]
  },
};
