# VCardly UI architecture

The UI is a single-Activity Compose app with MVVM and one-way data flow. Paths below are under
`app/src/main/kotlin/com/yasin/vcardly/`.

## 1. Layers

```
MainActivity (single Activity, edge-to-edge)
 └─ VCardlyTheme  (Light / Dark / System, read from DataStore)
     └─ Lock gate (LockScreen overlays everything while locked)
         └─ AppRoot  (Scaffold + NavHost + bottom bar)
             └─ Screen ⇄ ViewModel ⇄ Repository / Manager ⇄ Room · DataStore · system APIs
```

| Layer | Package | Responsibility |
|---|---|---|
| Design system | `core/designsystem/{theme,component}` | Color, Type, Shape, Spacing, a WCAG `Contrast` helper, and shared components (Buttons, Cards, FormFields, States, TopBar) |
| Navigation | `presentation/navigation` | `Routes`, `TopLevelDestination`, and `AppRoot` |
| Features | `presentation/<feature>` | One folder per feature, each with a `*Screen.kt` and a `*ViewModel.kt` |
| Shared UI | `presentation/common` | Avatar, card image view, form field, ad banner, validation text |
| Domain | `domain/*` | Pure Kotlin models, repository interfaces and logic (parser, planner, report builder) |
| Data and core | `data/repository`, `core/*` | Room, DataStore, camera/OCR, notifications, billing, ads, backup |

## 2. Screen pattern

```
ViewModel (@HiltViewModel)
  private MutableStateFlow / repository Flows
  └─ combine(...) → StateFlow<XxxUiState>      (stateIn, WhileSubscribed)
  └─ fun onEvent / onXxxChanged(...)           (user actions)
  └─ one-shot effects via Channel / SharedFlow (snackbars, saved, share)

Screen composable
  val state by vm.uiState.collectAsStateWithLifecycle()
  stateless content composables ← state + lambdas
  navigation callbacks passed in from AppRoot
```

- **Screens don't know about the NavController.** `AppRoot` passes them lambdas such as
  `onOpenContact`, `onNavigateUp` and `onSaved`.
- **UI state is an immutable data class per screen.** For example, `ContactsUiState` holds
  `isLoading`, `filter`, `contacts`, `categories` and `tags`.
- **Searching is debounced in the ViewModel.** `ContactsViewModel` debounces the filter and uses
  `flatMapLatest` into `observeContacts`.
- **The database drives the UI.** Room returns Flows, so lists update live without manual refreshes.
- **Loading, empty and error states use shared components** from `States.kt`.

## 3. Navigation graph

Bottom bar (4 top-level tabs): Home, Contacts, Follow-ups, Settings. The bar is shown only on these
routes; tab switches use `navigateTopLevel` (save/restore state, single top).

```
onboarding ─► home
home ─► mycard (+ edit), reports, search, contacts, followups
contacts ─► contact/{id} ─► contact/edit/{id}?fromScan, qr/{id}, followup/edit/{id}?contactId
         ├─► scan (nested graph): scan/capture ─► scan/crop ─► contact/edit/0?fromScan=true
         └─► pro
followups ─► followup/edit/{id}
settings ─► organize, backup, transfer, privacy, pro
```

- **The scan flow is a nested graph.** Capture and crop share one `ScanSessionViewModel`, scoped to
  the graph's back-stack entry, so the image and crop state survive between the two screens. After
  saving, `popUpTo(SCAN_GRAPH)` removes the whole flow.
- **Reminder notification taps** arrive as `openContactId` and are routed to the contact details once
  onboarding is done.
- **The start destination** is chosen before the first frame (`onboardingCompleted`), so there is no
  flicker.

## 4. Cross-cutting UI concerns

- **Theme:** `VCardlyTheme` reads the Light/Dark/System setting from DataStore. Avatar text color is
  picked per background using `Contrast`, which has a unit test.
- **Strings:** all user-facing text comes from `stringResource`; no hardcoded strings. English only,
  though RTL support stays enabled.
- **Accessibility:** bottom-bar icons are decorative because the labels are always shown. Other icons
  carry content descriptions.
- **App lock:** a `LockScreen` sits above the nav host, driven by `AppLockManager` with a monotonic
  clock for auto-lock.
- **Ads and Pro:** `AdBanner` appears on Home only. It is gated by `EntitlementManager`, so it never
  shows for Pro users or while locked.
- **Permissions and system pickers:** camera, notifications and exact-alarm permission go through
  activity-result launchers. Exports, backups and imports use the system file picker (SAF).
- **Insets:** each screen owns its top bar and status-bar insets. `AppRoot` applies only the
  bottom-bar padding.

## 5. Where things live

| I want to change… | Look in |
|---|---|
| Colors, type, spacing | `core/designsystem/theme/` |
| A reusable button, card or form field | `core/designsystem/component/` |
| Routes or the tab bar | `presentation/navigation/` |
| One feature's screen or logic | `presentation/<feature>/` |
| What data a screen shows | `domain/repository/` and `data/repository/` |
