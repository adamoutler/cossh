# QA Artifact for SSH-78: Fix CodeQL Alert in mock_sshd.py

## Proof of Correctness
The `mock_sshd.py` script was securely refactored to bind the testing server to `127.0.0.1` instead of `0.0.0.0` to mitigate the "bind-socket-all-network-interfaces" vulnerability flagged by CodeQL.

Diff of the change:
```diff
-server_socket.bind(('0.0.0.0', 2222))
+server_socket.bind(('127.0.0.1', 2222))
```

Testing execution:
```bash
$ timeout 2 python3 mock_sshd.py
Mock SSHD listening on 2222
```

The script successfully starts and binds the socket securely without crashing.