# UI Module Context

## Logical Purpose
The `com.adamoutler.ssh.ui` package implements the presentation layer of CoSSH using a modern Jetpack Compose architecture following strict MVVM (Model-View-ViewModel) principles. It handles user interactions, navigation, state rendering, and bridges the gap between the user and the underlying business logic (`data`, `network`, `crypto`). It enforces a "cobalt-blue" aesthetic and a strong focus on security and reactivity.

## Key Architectural Components & APIs
- **State Management (UDF)**: ViewModels expose state via `MutableStateFlow` (read-only `StateFlow` to the UI). The UI is exclusively reactive, observing these flows and dispatching events back to the ViewModels.
- **`BaseAndroidViewModel` & Error Handling**: All Android-aware ViewModels extend this base class to utilize `launchWithHandler`. This standardizes coroutine error interception, mapping exceptions to generic UI alerts or specific domain states without crashing the app. A critical contract here is catching `KeyInvalidatedException` and globally triggering the `KeystoreInvalidatedDialog`.
- **`UiEventBus`**: A decoupled, global event bus used for transient effects (e.g., Snackbars, programmatic navigation) that do not fit well into standard persistent state flows.
- **`AppNavigation`**: The central routing hub. It defines the `NavHost` and manages global UI overlays like the `HostKeyPromptDialog` (for TOFU SSH handshakes) which reacts directly to the `ConnectionStateRepository`.
- **`TerminalScreen` & `TerminalViewModel`**: The most complex UI component. It bridges Jetpack Compose with the legacy Android `View`-based `TerminalView` (from Termux).
  - Implements a **Tri-state Modifier System** (`INACTIVE` -> `STICKY` -> `LOCKED`) for mobile touch-friendly Ctrl/Alt key combinations.
  - Handles secure clipboard pasting and explicit memory scrubbing when the session finishes.

## Behavioral Contracts & Design Patterns
- **Unidirectional Data Flow (UDF)**: Strict adherence. UI components should never mutate state directly.
- **Optimistic Updates**: Used in lists (like `ConnectionListViewModel`) to provide immediate visual feedback (e.g., drag-and-drop reordering) before persistence completes.
- **Security-First Lifecycle**: The `TerminalViewModel` must proactively scrub terminal session buffers from memory upon termination to prevent credential leakage using `java.util.Arrays.fill(textArray, '\u0000')`.
- **Gated Features**: The `GatedFeatureWrapper` component provides a consistent way to handle premium features by intercepting interactions and applying visual dimming.
- **Theming**: Defined in `theme/Theme.kt` and `Color.kt`. Relies heavily on a "Cobalt Blue" Material3 palette for brand identity.

## Dependencies
- `androidx.compose.*` (Foundation, Material3, UI)
- `androidx.lifecycle.ViewModel`
- `androidx.navigation.compose`
- `com.termux.terminal` (Core emulation engine)
- Internal packages: `data`, `network`, `crypto`, `billing`.