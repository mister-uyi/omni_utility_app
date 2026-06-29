# Omni Utility

Omni Utility is a modern Android application built to provide quick access to essential device tools. It features a stunning deep dark aesthetic based on the **Orix Design System**.

## Features

- **Soft Power Button (Floating)**
  An accessible, floating on-screen button that can lock the device screen without using the physical power hardware key. It utilizes the modern Android AccessibilityService API to perform system-level actions securely.
  - **Dynamic Opacity**: Adjust the transparency of the floating button in the settings.
  - **Draggable**: Smoothly drag and snap the button to the left or right edges of your screen.
  - **Animated Edge Snapping**: The button smoothly morphs from a circle to a flat-edged semicircle when resting against the screen bezel.

## Design System (Orix)

The app is built entirely with Jetpack Compose using a custom Material 3 theme called **Orix**:
- **Backgrounds**: Deep Indigo/Purple (`#48426D`)
- **Surfaces**: Dark Navy (`#312C51`) for an elegant inverted-elevation aesthetic.
- **Accents**: Soft Peach/Orange (`#F0C38E`) used for primary buttons, switches, and the floating button itself.
- **Typography**: Clean, geometric `Lato` font (SIL Open Font License) serving as a premium modern sans-serif.

## Architecture

- **100% Jetpack Compose**: All UI is built declaratively.
- **Modular Design**: The project follows a multi-module architecture separating core UI components from distinct utility features (e.g., `:feature:soft-power`, `:core:ui`).
- **DataStore**: Modern asynchronous preferences management for saving floating button state (opacity, X/Y position).

## Requirements

- Android 8.0 (API 26) or higher.
- Accessibility Services permission is required for the Soft Power floating button to lock the screen.

## Setup

1. Clone this repository.
2. Open the project in Android Studio.
3. Sync Gradle and run on a physical device or emulator.
