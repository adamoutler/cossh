# SSH-30 QA Report: Drag-and-Drop Reordering

## Implementation Details
The `ConnectionListScreen` now supports drag-and-drop using a completely native Compose gesture implementation, specifically `detectDragGesturesAfterLongPress` via `Modifier.pointerInput`. 
No external unmaintained dependencies (e.g. `burnoutcrew:reorderable`) were used to keep the security perimeter strictly defined and rely solely on `androidx.compose`.

## Data Model Updates
- Added `sortOrder: Int` to `ConnectionProfile.kt`
- Updated serialization to correctly store `sortOrder` via `SecurityStorageManager.kt`

## Verification
- **Automated Validation:** Ran local test `ConnectionListViewModelDragDropTest` verifying correct item swaps and local storage persistence via the `moveProfile()` interface.
- **Visual Proof:** Paparazzi tests updated/captured, logging UI stability when dragging/reordering logic is applied. Snapshots saved to permanent visual directory (`docs/qa/` and `app/src/test/snapshots/images/`).
- **Logs:** Build and test logs attached as `docs/qa/SSH-30.log`.

**All tests PASS.** Feature behaves exactly as specified in the Kanban user story.