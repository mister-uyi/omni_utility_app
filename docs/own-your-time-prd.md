# Own Your Time — Product Requirements Document

> **Module:** `feature/own-your-time`
> **Status:** Ready for Development — MVP
> **Last Updated:** 2026-07-05
> **Platform:** Android (OmniUtility feature module)

---

## 1. Problem Statement

Mobile devices are engineered to maximise engagement, not productivity. Addictive apps, infinite scroll, and notification systems create an environment where users feel compelled to pick up their phones without intention. Even when a user sits down to do focused work, the phone's default interface — full app access, notifications, home screen distractions — works against them.

Existing solutions (Digital Wellbeing, Forest, One Sec, Freedom) address part of this problem but share a critical weakness: they either block apps permanently, require the phone to be put away entirely, or only create a soft deterrent without actually transforming the phone's interface.

**The gap:** There is no mainstream Android app that temporarily converts the phone into a focused execution environment — scoped to a specific task list, time-boxed, with selective app access — and then returns the phone to normal when the work is done.

---

## 2. Product Vision

> *Own Your Time gives users a temporary, task-scoped execution environment. For the duration of a session, the phone serves the user's task list — not the other way around. When the session ends, the phone returns to normal.*

The core insight is the **simulation of not having a phone**, while still having it. The phone becomes a tool, not a distraction machine, for exactly the time the user needs.

---

## 3. Target User

**Primary persona:** A professional, student, or creator who:
- Struggles with phone-induced distraction during periods of deep work
- Has tasks to accomplish that legitimately require a phone (reading links, watching tutorial videos, messaging)
- Is disciplined enough to set up a session but needs environmental enforcement to stay on task
- Has tried other focus apps but abandoned them due to being too restrictive or not addressing the root cause

**Secondary persona:** A user who wants to track and improve their productive session habits over time.

---

## 4. Goals & Non-Goals

### Goals (MVP)
- Allow users to define a task library with three types: plain text goals, web links, YouTube video links
- Allow users to categorise installed apps into Productivity, Fun, and System categories
- Allow users to start a timed session that locks the phone's interface to a focused screen
- Enforce a time-budgeted allowance for Fun apps during a session (tracked via UsageStatsManager)
- Always make Productivity and System apps available during a session
- Allow users to check off tasks during and after a session
- Provide a "Mark as Done" button inside Chrome Custom Tab views for web tasks
- Present an End/Continue dialog at session timer expiry
- Record session history with completion data
- Display month-on-month analytics on the dashboard

### Non-Goals (v2+)
- Per-session app category overrides
- Scheduling or recurring sessions
- Hard enforcement of app blocking outside the session screen (MDM-level)
- Custom embedded browser or video player
- Notification-based shortcuts to start sessions
- Social/accountability features
- Pro tier / paywall (validate first)
- iOS support

---

## 5. Monetisation Strategy

| Phase | Model |
|---|---|
| **MVP (Launch)** | Completely free. Optional donation button in Settings. |
| **v2+** | Free core forever + Pro tier for power users (advanced analytics, session scheduling, recurring tasks, custom themes). Target: $2.99/mo or $14.99/yr. |

> **Principle:** Free is the distribution strategy. The core session experience must remain free permanently. Donations are a signal metric for user love, not a revenue target.

---

## 6. User Flow

### 6.1 First Launch — Onboarding (4 steps)

```
Step 1: Welcome + Name
  → User enters their first name (used for personalised greeting on Dashboard)
  → Brief explanation of how sessions work

Step 2: Grant Usage Access Permission
  → Explanation of why PACKAGE_USAGE_STATS is needed (fun app tracking)
  → Deep link button → Settings → Apps → Special App Access → Usage Access
  → App detects grant and advances automatically

Step 3: Categorise Your Apps
  → All installed apps displayed in a scrollable list
  → Each app has a category dropdown: Productivity | Fun | Skip (hidden in sessions)
  → System apps (Phone, Messages, Settings, etc.) are pre-filled and shown as a static info note
  → Any app left "Unassigned" is hidden in sessions.
  → "Continue →" advances to the main app

Step 4: Set Defaults (skippable)
  → Default session duration (preset chips: 30m · 1h · 2h · 4h)
  → Default fun app budget percentage (slider, shows calculated minutes)
  → "Get Started →"
```

### 6.2 Regular Flow — Starting a Session

```
Dashboard → Tap "Start Session" FAB
  → Session Setup Sheet opens (3 steps)

  Step 1 — Select Tasks:
    List of all tasks from library with checkboxes
    User selects which tasks to include in this session
    Search bar available for large task libraries
    "X selected" count shown + "Select All" option

  Step 2 — Duration & Fun Budget:
    Duration preset chips: 30m · 1h · 2h · 4h · Custom
    Fun budget % slider (0–30%)
    Live calculation: "10% of 2 hours = 12 minutes for fun apps"
    Summary mini-card: "2h session · 3 tasks · 12 min fun"

  Step 3 — Confirm:
    Full session summary:
    - Duration
    - Tasks count + task title previews
    - Productivity apps (count + icon row, scrollable)
    - System apps (count + icon row, always available label)
    - Fun apps (count + icon row + "Xmin cap · locked after" label in amber)
    → "Commit & Start" (full-width amber CTA)

  → Lock Task Mode activates → Session Mode begins
```

### 6.3 Session Mode

```
Screen pinned via startLockTask()
Home button: disabled
Recents button: disabled
Status bar: locked/non-interactive

UI displays:
  - Countdown timer (large, monospace, dominant)
  - Session progress bar (time elapsed)
  - Fun budget bar (depletes as fun apps are used)
  - Task checklist (manual checkbox completion)
  - App rows: Productivity (scrollable) | System (scrollable) | Fun (scrollable, timed)
  - "End Session" button (low-prominence, bottom right, requires confirmation)

Task interactions:
  - Tap web link task → Chrome Custom Tab with "✓ Mark as Done" action button in toolbar
    → On tap: task marked complete, CCT closes
  - Tap YouTube task → YouTube via intent
    → On return to session: dialog "Did you finish this task?" [Yes] [Not yet]
    → Auto-dismisses after 5s with "Not yet" if no interaction
  - Tap plain text task → checkbox marks complete directly

Fun app behaviour:
  - UsageStatsManager foreground service tracks aggregate time across all fun apps
  - Fun budget bar depletes in real time
  - At exhaustion: fun app icons greyed, tap shows toast "Fun budget used. Back to work."

Session end at timer zero:
  - "Time's Up." dialog appears (session continues running until user acts)
  a) "End Session" → save record, stopLockTask(), go to Session Summary
  b) "Continue Session" → Extend Session picker:
       Chips: +15m · +30m · +1h · Custom
       Fun budget does NOT reset on extension
       Extension duration appended to session record
```

### 6.4 Post-Session

```
Session Summary screen:
  - Stats: total time, tasks completed (X/Y), fun budget used
  - Task list with completion status
  - Retroactive editing: any task tappable to toggle completion
  - "Done" → Dashboard, session saved to history
```

---

## 7. Screen Specifications

### 7.1 Dashboard

| Element | Description |
|---|---|
| Greeting | "Good morning/afternoon/evening, [Name]" + current date |
| Month Summary Card | Sessions count, tasks completed, total session time, month-on-month delta |
| Config Summary Card | Default duration, fun budget %, app counts. Tappable → Settings |
| Recent Sessions List | Card per session: date, duration, dot grid (amber = completed, empty = incomplete) |
| FAB | "Start Session" — amber, pulsing glow ring |
| Bottom Navigation | Dashboard · Tasks · Settings |

### 7.2 Task Manager

| Element | Description |
|---|---|
| Task List | Cards with type icon: link · video · text (amber icons) |
| Task Card | Title bold white, URL/subtitle muted grey, 3-dot menu |
| Actions | Swipe left → delete. Tap → edit. Long press → reorder. |
| Add Task Sheet | Title → Type selector → URL field (conditional) → Save |

### 7.3 Session Mode

| Element | Description |
|---|---|
| Timer | Monospace, large, white. "remaining" label grey below |
| Session progress bar | Thin amber bar, grows as time elapses |
| Fun budget bar | Thin bar, depletes. "Xm Xs left" amber label |
| Tasks | Checklist. Tap to open content. Manual checkbox to complete. |
| App rows | PRODUCTIVITY / SYSTEM / FUN sections. Each horizontally scrollable. Fun section shows timer badge, greyed when exhausted. |
| End Session | Small text button, low contrast, bottom right |
| No navigation | Bottom nav hidden. Status bar locked. |

### 7.4 Session End Dialog

| Element | Description |
|---|---|
| Background | Session screen blurred. "00:00:00" faintly visible. |
| Icon | Amber hourglass |
| Title | "Time's Up." |
| Stat | "X of Y tasks completed" in amber |
| Primary CTA | "End Session" full-width amber |
| Secondary CTA | "Continue Session" outlined amber |
| Note | "Continuing will not reset your fun app budget" |

### 7.5 Extend Session Picker

| Element | Description |
|---|---|
| Chips | +15 min · +30 min · +1 hour · Custom |
| Selected display | Large amber "+ 30 min" with up/down adjuster |
| Lock notice | "Fun budget will NOT reset" amber with lock icon |
| CTA | "Start Extension" amber |

### 7.6 Session Summary

| Element | Description |
|---|---|
| Stats card | Amber top border. Date range. Total time. Tasks X/Y. Fun budget used. |
| Task list | Completed = amber circle + strikethrough. Incomplete = empty circle + white. |
| Retroactive edit | Tap any task to toggle completion |
| CTA | "Done" full-width amber |

### 7.7 App Categories (Settings)

| Element | Description |
|---|---|
| Sections | PRODUCTIVITY (expandable, amber) · FUN (expandable, amber) · SYSTEM (expandable, grey) |
| App row | App icon + name + category chip tag (Productivity / Fun / Skip) |
| Add app | Dashed amber "+ Add app" — opens picker of unassigned apps |
| System section | Collapsed default. "Always present in sessions" note. |
| Footer | "Apps not assigned to any category are hidden during sessions" |

### 7.8 Onboarding — App Categorisation

| Element | Description |
|---|---|
| Progress | 3-segment bar, 2 of 3 filled |
| App list | Icon + name + category dropdown pill per row |
| Dropdown options | Productivity · Fun · Skip |
| Info box | Amber: "System apps (Phone, Messages, Settings) are always available" |
| CTA | "Continue →" full-width amber |

---

## 8. Technical Architecture

### 8.1 Module Location
`feature/own-your-time/` — Android Library module following OmniUtility modular architecture.

### 8.2 Key Android APIs

| Feature | API |
|---|---|
| Screen lock (session mode) | `Activity.startLockTask()` / `stopLockTask()` |
| Fun app time tracking | `UsageStatsManager.queryUsageStats()` polled from foreground service |
| Fun app enforcement | `UsageStatsManager.queryEvents()` to detect foreground app changes |
| Web task viewing | `CustomTabsIntent.Builder` with `setActionButton()` for "Mark as Done" |
| YouTube task | `Intent(Intent.ACTION_VIEW, Uri.parse(url))` |
| Session timer | Foreground Service + CountDownTimer + persistent notification |
| App list scanning | `PackageManager.getInstalledApplications()` |
| Persistence | Room database |
| DI | Hilt |

### 8.3 Permissions

| Permission | Purpose | Grant Method |
|---|---|---|
| `PACKAGE_USAGE_STATS` | Track time in fun apps | Manual via Settings deep link (onboarding) |
| `FOREGROUND_SERVICE` | Session timer and tracking | Auto-granted (manifest) |
| `POST_NOTIFICATIONS` | Persistent session notification (Android 13+) | Runtime request |

> **Degraded mode:** If PACKAGE_USAGE_STATS is denied, fun budget tracking is disabled. App shows a persistent prompt to grant it. Session mode still works — fun apps are available without enforcement.

### 8.4 Chrome Custom Tab "Mark as Done"

```kotlin
val markDoneIntent = Intent(ACTION_MARK_TASK_DONE).apply {
    putExtra(EXTRA_TASK_ID, taskId)
}
val pendingIntent = PendingIntent.getBroadcast(
    context, taskId, markDoneIntent, FLAG_IMMUTABLE
)
CustomTabsIntent.Builder()
    .setActionButton(
        icon = BitmapFactory.decodeResource(resources, R.drawable.ic_check),
        description = "Mark as Done",
        pendingIntent = pendingIntent,
        shouldTint = true
    )
    .build()
    .launchUrl(context, Uri.parse(url))
```

The session screen registers a BroadcastReceiver for `ACTION_MARK_TASK_DONE`, updates the task completion in the DB, and closes the CCT.

### 8.5 YouTube Task Completion

YouTube opens as an external intent within Lock Task Mode. The user navigates back naturally. On return to the session screen, a transient dialog appears:

> *"Did you finish this task?"*
> **[✓ Yes, mark done]** · **[Not yet]**

If no interaction within 5 seconds, auto-dismisses with "Not yet". User can always manually tick the checkbox.

---

## 9. Data Model

```kotlin
enum class TaskType { TEXT, WEB_LINK, YOUTUBE_LINK }

@Entity
data class Task(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: TaskType,
    val url: String? = null,
    val createdAt: Long,
    val isArchived: Boolean = false
)

enum class AppCategory { PRODUCTIVITY, FUN, SYSTEM, SKIP }

@Entity
data class AppConfig(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val category: AppCategory
)

@Entity
data class Session(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val startedAt: Long,
    val plannedDurationMs: Long,
    val actualDurationMs: Long,
    val funBudgetPercent: Int,
    val funBudgetMs: Long,
    val funTimeUsedMs: Long = 0L,
    val extensions: String = "[]",   // JSON array of extension durations in ms
    val endedAt: Long? = null
)

@Entity(primaryKeys = ["sessionId", "taskId"])
data class SessionTask(
    val sessionId: String,
    val taskId: String,
    val taskSnapshot: String,        // JSON snapshot of task at session time
    val completed: Boolean = false,
    val completedAt: Long? = null
)

@Entity
data class UserConfig(
    @PrimaryKey val id: Int = 1,     // singleton row
    val userName: String = "",
    val defaultDurationMs: Long = 3_600_000L,
    val defaultFunBudgetPercent: Int = 10
)
```

---

## 10. Design System

| Token | Value |
|---|---|
| Background | `#0D0D0D` |
| Surface | `#1A1A1A` |
| Border | `#2A2A2A` |
| Accent | `#F5A623` (Warm Amber) |
| Text Primary | `#FFFFFF` |
| Text Secondary | `#8A8A8A` |
| Text Disabled | `#3A3A3A` |
| Timer Font | Monospace (JetBrains Mono or system) |
| Body Font | Inter or system sans-serif |
| Mode | Dark only (no light mode in MVP) |

---

## 11. UX Decisions Log

| Decision | Choice | Rationale |
|---|---|---|
| Lock mechanism | Screen Pinning — not a permanent launcher | Temporary takeover matches the concept. No launcher complexity. |
| Fun app enforcement | UsageStatsManager + greyed icons when exhausted | Real enforcement. One-time permission grant. |
| App category override per session | Not supported — Settings only | Simplicity first. Revisit with user feedback. |
| Web task completion | "Mark as Done" button in Chrome Custom Tab toolbar | Completion confirmed at point of action. |
| YouTube task completion | Return-to-session dialog with 5s auto-dismiss | CCT not applicable for YouTube. Dialog is next-best. |
| Session end | Dialog at timer zero. User chooses End or Continue. | Supports in-the-zone users. No auto-close. |
| Session extension | Fun budget does NOT reset | Prevents gaming the system. |
| Task completion | Manual checkbox required | Intentional, conscious completion. |
| Retroactive editing | Available on Session Summary | Reduces anxiety during sessions. |
| App rows in session | Horizontally scrollable, no hard cap | Flexible for users with many apps. |
| Onboarding personalisation | Name input in Step 1 | Personalised greeting improves engagement. |
| Monetisation | Donations only at launch | Validate first. Build audience. |

---

## 12. Success Metrics

### Primary
- **Session completion rate:** % ended normally vs. force-exited. Target: >60%
- **Return rate:** % running a second session within 7 days. Target: >40%
- **Weekly sessions per active user.** Target: >2 after 30 days

### Secondary
- Average tasks per session
- Average session duration
- Fun budget usage rate
- Retroactive task edit rate

### Validation Signal
- Any donations within 60 days = users love it enough to act voluntarily

---

## 13. Open Questions (v2 Backlog)

| Question | Impact |
|---|---|
| Device Admin for stronger lock (beyond Screen Pinning)? | High |
| Lock screen / notification widget to start session fast? | Medium |
| Should completed tasks be archived or deleted from library? | Low |
| Minimum fun budget floor (prevent 0%)? | Medium |
| Per-session app overrides (Option B)? | Medium — revisit post-launch |
| iOS support? | High if Android validates |

---

## 14. Milestone Plan

| Milestone | Scope | Target |
|---|---|---|
| **M1 — Foundation** | Module setup, Room DB, data models, navigation graph, onboarding (no lock yet) | Week 1–2 |
| **M2 — Task Manager** | Full task CRUD, all three task types | Week 2–3 |
| **M3 — Session Setup** | 3-step session setup sheet, session data wiring | Week 3–4 |
| **M4 — Session Mode** | Lock Task Mode, foreground service timer, task checklist, CCT + YouTube intents | Week 4–6 |
| **M5 — Fun Budget** | UsageStatsManager integration, fun app enforcement, budget bar | Week 6–7 |
| **M6 — Session End & Summary** | End dialog, extend flow, summary screen, retroactive editing | Week 7–8 |
| **M7 — Dashboard & Analytics** | Session history, month-on-month stats, dot grid | Week 8–9 |
| **M8 — Settings & Polish** | App categories management, defaults config, micro-animations | Week 9–10 |
| **M9 — QA & Release** | Internal testing, permission edge cases, Play Store submission | Week 10–11 |
