# Environment Variables

The **Environment Variables** section allows you to pass custom variables to the remote server upon connection.

## Usage
SSH allows passing specific environment variables (like `LC_TIME` or custom app flags) during the initial handshake, provided the SSH server is configured to accept them (`AcceptEnv` in `sshd_config`).

When defining environment variables here, they will be sent to the remote shell and available to your initial session.

**Format:**
Variables should be defined as key-value pairs.