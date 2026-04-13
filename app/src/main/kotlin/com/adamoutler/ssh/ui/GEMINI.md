# UI Module

This module contains the Jetpack Compose visual layer of CoSSH.

## Functionality
- Renders the "cobalt-blue" aesthetic and standard user screens (ConnectionList, Terminal, Keys, Add/Edit Profile).
- Handles user interactions, navigation, and visual state management.
- Presents dialogs, menus, and bottom sheets for the application.

## Dependencies
- **data:** Displays and edits connection profiles and keys.
- **network:** Sends user input to active SSH sessions and displays terminal output.
- **crypto:** Calls storage and key generation methods.
- **backup:** Triggers backup export and import flows.

## Dependents
- **Application Entry Point (`MainActivity`):** The main activity depends on the UI module to host the navigation graph and root composable screens.
