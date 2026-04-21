# QA Proof: SSH-86 - Implement Folder Organization for Connections

**User Story:** *As a user, I want to organize my SSH connections into folders, so that I can easily manage and navigate a large number of servers.*

**Verification Proof:**
- [x] Grouped rendering: `GroupedConnectionList` correctly displays profiles grouped by folder with sticky headers (headers hidden if only one 'Uncategorized' group exists).
- [x] Swipe actions: `SwipeToDismissBox` implemented for "Move to Folder" (left-to-right) and "Delete" (right-to-left).
- [x] Folder management: `MoveToFolderBottomSheet` allows selecting an existing folder or creating a new one on the fly.
- [x] Integration: `UserJourneyIntegrationTest` passing with the new grouped list structure.

## Implementation Details
- Updated `ConnectionProfile` to include an optional `folderId` field.
- Updated `ConnectionListViewModel` to expose `groupedProfiles: Map<String?, List<ConnectionProfile>>` and implement `moveToFolder` and `deleteProfile` logic.
- Implemented `GroupedConnectionList`, `MoveToFolderBottomSheet`, and updated `ConnectionListContent` and `ConnectionListScreen`.
- Added `testTag` to `ConnectionItem` and `AddEditProfileScreen` input fields for robust automated testing.
- Fixed regressions in existing screenshot and integration tests by providing required parameters and updating node discovery logic.
