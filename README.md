# WebClass Plus Android

A native Android app that wraps **WebClass LMS** with a modern, Material Design 3 UI and enhanced features for students.

## Features

- ✅ **Modern Native UI**
    - Built with Jetpack Compose and Material Design 3.
    - Three Theme Variants: Light, Dark, Pastel + Dynamic Color support.
    - Supports Tablet layouts with responsive navigation (Sidebar/Bottom Bar).
    - Custom Color Theme Editor.
- ✅ **Enhanced Video Player** (v2.2.0)
    - **Native Playback**: Automatically detects and plays embedded videos using ExoPlayer.
    - **Immersive Mode**: Hides system bars and app navigation for distraction-free viewing.
    - **Full Localization**: Japanese UI for controls and settings.
    - **Background Playback**: Audio continues even when screen is off (optional).
- ✅ **Advanced Navigation**
    - Seamless tab navigation (Home, Bookmarks, User, Settings).
    - "Sticky" Home tab focus when browsing deep course content.
    - Safe-area aware design (supports camera cutouts/notches).
- ✅ **Course Management**
    - Filter courses by Year/Semester.
    - View course contents, details, and history.
    - Bookmark favorite courses.
- ✅ **Focus Mode**
    - Hides system bars to help you concentrate on materials.

## Version History

### v3.7.0 (Latest)
- **Browser Bug Fixes, New Start Animation & Optimizations**:
    - **In-App Browser Split-Screen Fixes**: Locked currently running textbooks/contents to the primary pane when split screen is enabled. Created an advanced selection highlight and redirection system to target the split/auxiliary pane automatically.
    - **New Elegant Brand Splash Screen**: Replaced startup icons with standard theme-adaptive Material vectors, providing beautiful staggered cubic and overshoot scaling animations.
    - **App-Wide Optimization & Lightweighting**: Integrated JavaScript extraction on the home screen into a single execution, resolved preferences flow cascading recompositions via `.distinctUntilChanged()`, and added stable list recycling keys for list performance.

### v3.6.1
- **Developer Name Update**:
    - Updated developer name representation to "稲穂".
    - Cleaned up mock data references to old user handle.

### v3.6.0
- **Portfolio & Report Features**:
    - Added Portfolio feature for unified management of study outcomes and records.
    - Added My Reports feature for convenient submission and report management.
    - Added Split View (Multi-Window) for in-app browser.
    - Added Grade Filter for syllabus search.
    - Added Index/Header navigation for syllabus details.
    - Optimized Tablet layouts for Grades page.
    - Revamped Home widgets layout and design.
    - Animation and UI bug fixes.

### v2.2.1
- **Maintenance**:
    - Updated Open Source License information.

### v2.2.0
- **Native Video Player**:
    - Replaced WebView playback with a custom ExoPlayer implementation.
    - Added full-screen immersive mode.
    - Added Japanese localization for all player controls.
    - Added toggle setting to enable/disable native player.
- **Tablet Optimizations**:
    - Fixed orientation issues on tablets (no longer forces landscape).
    - Adjusted sidebar layout to avoid camera cutouts.
- **Navigation**:
    - Improved navigation focus (Home tab stays active).
    - Added exit confirmation dialog when pressing Back during content execution.

### v2.1.0 & v2.0.0
- Tablet UI Support with Adaptive Navigation Rail.
- Custom Theme Editor implementation.
- Focus Mode.
- Initial release of feature-complete app.

## Build & Run

1. Open in Android Studio Giraffe or newer.
2. Sync Gradle.
3. Run on emulator or device (minSdk 26, targetSdk 35).

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Media**: Media3 (ExoPlayer)
- **Architecture**: MVVM
- **Data**: DataStore, Gson
