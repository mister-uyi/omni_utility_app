# Private AI Finance - Session Handoff Document

This document summarizes the current architecture, technical achievements, key design decisions, and state of the Private AI Finance workspace on the **get-it-done-now** branch.

---

## 1. Project Branch & Git State
* **Active Branch:** `get-it-done-now`
* **Remote Sync Status:** All previous changes are committed and pushed to the remote repository on `main`.

---

## 2. Key Architecture & Technical Implementations

### A. Automatic Keyboard Layout Resizing (Modern App Pattern)
* **Manifest Config:** `android:windowSoftInputMode="adjustResize"` is correctly configured on the `.MainActivity` tag.
* **Auto-Hiding Navigation Bar:** The bottom navigation bar dynamically hides when the keyboard slides up beyond `100.dp` in height, maximizing screen space for typing.
* **Manual Shifting (Compose Edge-to-Edge):** Because edge-to-edge drawing is enabled, standard Compose containers do not resize automatically. We explicitly applied `Modifier.imePadding()` to the **Analytics chat Column** and the **Vault settings LazyColumn** so they resize cleanly to sit flush on top of the keyboard.

### B. Simplified Financial Goals Form
* The Goal creation dialog now collects only two parameters:
  1. **Goal** (e.g. *Save ₦500k for rent*)
  2. **Extra Info (Optional)** (e.g. *By end of December, exclude salary*)
* **Advisor Context:** The "Extra Info" string is automatically appended to the goal description inside the ViewModel before fetching advice, giving the AI model complete context.
* **Dynamic Cards:** The goals dashboard cards hide `Target` and `Deadline` rows if they are not set (e.g. `0.0` or `0L`).

### C. Live AI Thinking Indicator
* Added an `isChatLoading: StateFlow<Boolean>` flow inside `FinanceDashboardViewModel.kt`.
* When Gemini is thinking, the text field shows *"Gemini is thinking..."*, the send button is disabled, and an elegant circular progress bubble showing *"Thinking..."* is drawn at the bottom of the scrollable list.

### D. Ground-Truth Dashboard Metrics
* To bypass LLM miscounting limitations, the ViewModel mathematically computes transaction counts, total income, total expenses, and ledger balances, injecting them directly under a `Verified Dashboard Metrics` block in the prompt context.

### E. Back-Gesture Navigation
* Handled via Compose `BackHandler`. If the user is on the **Analytics** or **Vault** tab, pressing back shifts them to the **Home** tab first. Pressing back on the **Home** tab exits the finance feature.

---

## 3. Reference Files & Artifacts
* **Main UI Screen:** [FinanceDashboardScreen.kt](file:///Users/uyioriaghan/Documents/data/my_stuff/my_utility_app/feature/finance/src/main/java/com/omniutility/feature/finance/ui/FinanceDashboardScreen.kt)
* **Business Logic / State VM:** [FinanceDashboardViewModel.kt](file:///Users/uyioriaghan/Documents/data/my_stuff/my_utility_app/feature/finance/src/main/java/com/omniutility/feature/finance/ui/FinanceDashboardViewModel.kt)
* **Offline AI Inference Engine:** [OfflineAIEngine.kt](file:///Users/uyioriaghan/Documents/data/my_stuff/my_utility_app/feature/finance/src/main/java/com/omniutility/feature/finance/data/ai/OfflineAIEngine.kt)
* **Manifest:** [AndroidManifest.xml](file:///Users/uyioriaghan/Documents/data/my_stuff/my_utility_app/app/src/main/AndroidManifest.xml)
