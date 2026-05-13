# Billing Module Context

## Logical Purpose
The `com.adamoutler.ssh.billing` package is responsible for managing the application's monetization through Google Play Billing. It acts as the gatekeeper for premium features, specifically gating the Google Drive "Cloud Sync" feature behind a lifetime purchase product identified by the ID `lifetimecloudsync`.

## Key Components & APIs
- **`BillingManager`**: The single source of truth for the license state. It encapsulates the `BillingClient` and handles all its complexity (connection, queries, updates, and acknowledgments).
  - `isCloudSyncEnabled: StateFlow<Boolean>`: Exposes the license status reactively, allowing UI and background components to update when a license is acquired or restored.
  - `purchaseCloudSync(activity: Activity)`: Initiates the purchase flow.
  - `forceCloudSyncEnabledForTest`: A static bypass flag used to simplify testing and development of premium features without actual transactions.

## Behavioral Contracts & Design Patterns
- **Auto-Restoration**: On initialization, the manager automatically connects to the Play Store and queries for existing purchases, restoring the license if found.
- **Self-Finalizing**: It automatically acknowledges successful purchases to prevent them from being refunded by Google Play.
- **No Local Persistence**: The license state is derived from Google Play Services at runtime. It is not persisted in SharedPreferences or a local database; it is queried upon each app launch or worker execution.

## Dependencies & Integration Insights
- **UI Lifecycle**: In the UI, `BillingManager` is instantiated once in `AppNavigation` and passed down, ensuring a consistent state across screens (e.g., `SettingsViewModel`).
- **Worker Considerations**: The `SyncWorker` creates its own instance of `BillingManager`. Because the `BillingClient` connection and query are asynchronous, there is a small window where `isCloudSyncEnabled` might report `false` before the restoration query completes.
- **Google Play Billing Library**: Relies heavily on `com.android.billingclient`.

**Note:** Older documentation might refer to the product as `cloud_sync_unlock`, but the active codebase strictly uses `lifetimecloudsync`.