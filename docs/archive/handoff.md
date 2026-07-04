# OmniUtility App - Architectural Handoff Document

This document outlines the high-level architecture of the **OmniUtility** application, explaining how the multi-functional app is structured, followed by details of the active Private AI Finance feature and instructions on how to introduce a new feature.

---

## 1. High-Level Modular Architecture

OmniUtility is a multi-functional utility app built using a **modularized Android project structure**. Rather than a single monolithic project, features are separated into independent Gradle modules under the `feature/` directory to promote clean separation of concerns and fast build times.

### Directory Structure
```
├── app/                  # Main application entry point (binds all features and navigation)
├── core/                 # Core shared modules (e.g., :core:ui theme, common widgets)
├── feature/              # Independent, feature-specific modules
│   ├── finance/          # Private AI Finance feature
│   └── soft-power/       # Soft Power settings feature
└── docs/                 # Project documentation and handoff files
```

### A. Main App Module (`app`)
* **Purpose:** Handles application startup, initializes Hilt dependency injection, defines edge-to-edge configurations, and routes between features.
* **MainActivity:** [MainActivity.kt](file:///Users/uyioriaghan/Documents/data/my_stuff/my_utility_app/app/src/main/java/com/omniutility/MainActivity.kt) initializes Hilt and runs edge-to-edge configuration.
* **App Routing:** Managed via Compose Navigation 3 in [Navigation.kt](file:///Users/uyioriaghan/Documents/data/my_stuff/my_utility_app/app/src/main/java/com/omniutility/Navigation.kt). It uses type-safe serialization keys to define screen entries:
  ```kotlin
  @Serializable object Main
  @Serializable object SoftPower
  @Serializable object Finance
  ```

---

## 2. How to Add a New Feature

When starting a new feature (unrelated to Finance or Soft Power), follow these standard modularization steps:

1. **Create a New Feature Module:**
   * Create a new folder under `feature/` (e.g., `feature/new-tool`).
   * Include the standard `build.gradle.kts` configuration for an Android library module.
2. **Register the Module:**
   * In [settings.gradle.kts](file:///Users/uyioriaghan/Documents/data/my_stuff/my_utility_app/settings.gradle.kts), register the module:
     ```kotlin
     include(":feature:new-tool")
     ```
3. **Declare Dependency in App:**
   * In `app/build.gradle.kts`, add the dependency:
     ```kotlin
     implementation(project(":feature:new-tool"))
     ```
4. **Define Navigation Keys:**
   * In [Navigation.kt](file:///Users/uyioriaghan/Documents/data/my_stuff/my_utility_app/app/src/main/java/com/omniutility/Navigation.kt), declare a new navigation destination key:
     ```kotlin
     @Serializable object NewTool
     ```
   * Add the screen entry inside the `MainNavigation()` entry provider switch.
5. **Hook up Dashboard Access:**
   * Register the new feature and its icon/launch action in the central dashboard navigation menu (usually located inside the [MainScreen.kt](file:///Users/uyioriaghan/Documents/data/my_stuff/my_utility_app/app/src/main/java/com/omniutility/ui/main/MainScreen.kt) home menu).

---

## 3. Active Branch State
* **Active Branch:** `get-it-done-now`
* **Remote Sync Status:** All previous changes are committed and pushed to the remote repository on `main`.

---

## 4. Current Work: Private AI Finance Summary (Reference)

This section serves as a technical reference for the completed **Private AI Finance** feature:
* **Keyboard Insets (`adjustResize`):** Configured via AndroidManifest.xml. The bottom navigation bar automatically hides when the keyboard is open beyond `100.dp`. Compose screens resize manually using `Modifier.imePadding()` to sit flush above the keyboard.
* **Simplified Goals Form:** Simplified to two input fields (Goal and Extra Info). Target/deadline fields are dynamically hidden on goal cards if not set.
* **Pulsing Loading Indicator:** A StateFlow `isChatLoading` in the ViewModel disables text fields and displays a pulsing "Thinking..." progress bubble while Gemini computes replies.
* **Deterministic Calculations:** Computes balances and transaction counts mathematically in Kotlin and injects them under `Verified Dashboard Metrics` in prompt templates to prevent LLM miscounting.

---

## 5. Main Reference Files
* **App Navigation Router:** [Navigation.kt](file:///Users/uyioriaghan/Documents/data/my_stuff/my_utility_app/app/src/main/java/com/omniutility/Navigation.kt)
* **Finance UI Screen:** [FinanceDashboardScreen.kt](file:///Users/uyioriaghan/Documents/data/my_stuff/my_utility_app/feature/finance/src/main/java/com/omniutility/feature/finance/ui/FinanceDashboardScreen.kt)
* **Finance VM Logic:** [FinanceDashboardViewModel.kt](file:///Users/uyioriaghan/Documents/data/my_stuff/my_utility_app/feature/finance/src/main/java/com/omniutility/feature/finance/ui/FinanceDashboardViewModel.kt)
* **AI engine:** [OfflineAIEngine.kt](file:///Users/uyioriaghan/Documents/data/my_stuff/my_utility_app/feature/finance/src/main/java/com/omniutility/feature/finance/data/ai/OfflineAIEngine.kt)
