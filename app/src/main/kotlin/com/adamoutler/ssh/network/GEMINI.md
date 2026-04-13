# Network Module

This module provides the SSH protocol implementation and manages the connection lifecycle.

## Functionality
- Manages SSH session lifecycles including connecting, maintaining, and disconnecting sessions.
- Runs the `SshService` to keep connections alive in the background when necessary.
- Provisions SSH sessions and ensures network timeouts and limits are strictly adhered to.

## Dependencies
- **data:** Uses `ConnectionProfile` to determine connection targets and parameters.
- **crypto:** Uses credentials and generated keys from the crypto module for authentication.

## Dependents
- **ui:** The Terminal screen and Connection List screen depend on the network module to display live output, accept input, and show connection statuses.
