# UI Module (`com.adamoutler.ssh.ui`)

This module contains the Jetpack Compose visual layer and presentation logic of CoSSH.

## Package Responsibility
The UI package renders the application's "cobalt-blue" aesthetic and manages user interactions following the MVVM pattern. It is responsible for navigation, dialog presentation, and bridging user actions to the underlying business logic (network, crypto, backup).

## Core Structure
- **`navigation/AppNavigation`**: Defines the entry point and routes for all composable screens.
- **`screens/`**: Contains the primary Views and ViewModels (e.g., `ConnectionListViewModel`, `AddEditProfileViewModel`, `TerminalScreen`).
- **`events/UiEventBus`**: Implements a decoupled event system for handling one-time UI actions (snackbars, navigation triggers) asynchronously.
- **`theme/` & `components/`**: Manages visual styling, colors, and reusable UI widgets.

## Dependencies
- **`com.adamoutler.ssh.data`**: Consumes models to display and edit profiles and keys.
- **`com.adamoutler.ssh.network`**: Reads connection states and terminal output; sends user keystrokes.
- **`com.adamoutler.ssh.crypto`**: Triggers storage saving and key generation.
- **`com.adamoutler.ssh.backup`**: Initiates export and import flows.

## Dependents
- **`com.adamoutler.ssh` (Root)**: The `MainActivity` hosts the navigation graph defined in this package.

## Testing Context
Testing must reflect real user journeys rather than fully isolated mock component testing. Every UI test must map directly to a defined User Story. Tests must involve the ViewModel, verify integration with UI components, and actively handle and assert state changes.
