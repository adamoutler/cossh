# Data Module

This module defines the core data models and state definitions for CoSSH.

## Functionality
- Defines the `ConnectionProfile` class which represents a saved SSH connection configuration.
- Contains Kotlin serialization annotations to ensure data can be saved, restored, and transmitted as needed.
- Acts as the single source of truth for the structure of connection data.

## Dependencies
- **Kotlinx Serialization:** For structured data parsing and generation.

## Dependents
- **backup:** Reads and writes `ConnectionProfile` objects during export and import.
- **crypto:** Secures the data models using encryption.
- **network:** Consumes the connection data to establish SSH sessions.
- **ui:** Displays the connection data in the user interface.

## Testing Standards
- **Real User Journeys:** Testing must reflect real user journeys and ViewModel integration, rather than fully isolated mock component testing.
- **Serialization Flows:** Data models must be tested in conjunction with their serialization and deserialization processes in realistic flows.
