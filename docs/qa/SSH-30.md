# Verification Proof for SSH-30 (Revised)

## Fix for UI Integration and Gesture Conflicts
The previous iteration of this ticket created the backend persistence logic (`sortOrder` & `moveProfile`) and the `DraggableConnectionList` component, but left them disconnected due to conflicts with the `GroupedConnectionList` folder sorting and the long-press-to-edit gesture introduced in SSH-29.

These issues have been fully resolved with the following UX and UI integration strategy:
1. **Explicit Reorder Mode:** Added a "Reorder Connections" action to the `ConnectionListTopBar` overflow menu.
2. **Dynamic UI Swapping:** When Reorder Mode is active, `ConnectionListContent` dynamically replaces the grouped, un-draggable `GroupedConnectionList` with a flat `DraggableConnectionList`.
3. **Gesture Conflict Resolution:** Inside the `DraggableConnectionList` instantiation during Reorder Mode, both `onConnect` (tap) and `onEditConnection` (long-press) are explicitly disabled. This allows `detectDragGesturesAfterLongPress` exclusive priority over the items, perfectly resolving the architectural conflict between drag-and-drop and editing connections. A "Done Reordering" checkmark replaces the overflow menu to exit the mode.

## Visual and Automated Proof
The `ConnectionListScreenScreenshotTest` was updated to explicitly pass the `isReorderingPreview = true` state to `ConnectionListContent`. This generated a Paparazzi snapshot demonstrating the integrated `DraggableConnectionList` UI in Reorder mode.

**Snapshot Artifact:**
![Reorder Mode](SSH-30-reorder-mode.png)

## Backend Persistency Validation
As documented previously, `loadProfiles()` in `ConnectionListViewModel.kt` explicitly sorts all profiles returned from the keystore (`storageManager.getAllProfiles().sortedBy { it.sortOrder }`) ensuring persistency across app restarts. The `ConnectionListViewModelDragDropTest` remains fully green and explicitly verifies that `viewModel.profiles.value` accurately reflects database-level `sortOrder` mutations.