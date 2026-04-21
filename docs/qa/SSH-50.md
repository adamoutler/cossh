# SSH-50 Verification Proof

## Enhanced E2E Testing: 50kb ASCII Integrity Verification

An end-to-end integration test (`FiftyKbIntegrityTest.kt`) connects to the local `mock_sshd.py`, generates 50kb of pseudo-random ASCII data, outputs it through the SSH stream, reads the echoed data back via the terminal pipeline, and securely matches the local SHA-256 hash against the received data's hash to ensure zero data loss.

**Local Output (`docs/qa/SSH-50.log`):**
```text
    Starting mock_sshd from: /home/adamoutler/git/ssh/app/../mock_sshd.py
    Generated 50kb ASCII data. Local SHA256: a428dddbd802caac4912820b50a95dfd1f48d195f23ba5d45911a1315c19aa40
    Data transmitted through terminal.
    Data received and written to /home/adamoutler/git/ssh/docs/qa/SSH-50-output.txt. Remote SHA256: a428dddbd802caac4912820b50a95dfd1f48d195f23ba5d45911a1315c19aa40
⏱️ TEST-METRIC: com.adamoutler.ssh.network.FiftyKbIntegrityTest.test50KbDataIntegrity took 7377ms
```

**Verification Details:**
1. Connected successfully to `mock_sshd.py`.
2. Written and echoed back ~50kb of data accurately.
3. Both hashes successfully match (`a428dddbd802caac4912820b50a95dfd1f48d195f23ba5d45911a1315c19aa40`).
4. Proof artifact `SSH-50-output.txt` successfully written and documented.