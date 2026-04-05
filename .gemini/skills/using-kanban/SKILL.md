---
name: using-kanban
description: Strict workflow for managing Agentic Kanban tasks, including batching tickets, execution phases, proof gathering in docs/qa, and surviving the reality-checker QA gate.
---
# The CoSSH Kanban Workflow

This project heavily relies on an Agentic Kanban board. The Kanban project slug is `SSH`, meaning tickets follow the format `SSH-<sequence_id>` (e.g., `SSH-1` is Project SSH, Sequence ID 1).

## 1. Ticket Creation & Standardization
When creating or updating tickets, you MUST provide clear criteria for completion. If you do not, the `@reality-checker` agent will invent its own criteria, which may be impossible to satisfy. All tickets MUST include the following structured sections in their description:

1. **User Story:** Explains the "who, what, and why".
2. **Verification Proof:** Explicitly lists the hard evidence required to consider the ticket done (e.g., screenshots, UI test passes, logcat traces).

**Standard Ticket Description Format:**
```markdown
**User Story:** *As a [role], I need [feature/fix] so that [benefit].*

**Verification Proof:**
To satisfy the `@reality-checker`, the proof MUST be explicit and artifact-based. Use the following examples to guide your criteria:

* **Visual/UI Changes:** Screenshot artifact demonstrating the specific UI state (e.g., "Screenshot of the 'Key Management' screen showing a generated ED25519 key").
* **Logic/Backend Changes:** Logcat trace or output from a unit test confirming the behavior (e.g., "Logcat trace proving successful headless SSH connection" or "Unit test passing for `SecurityStorageManager`").
* **File/System Changes:** Standard output of a successful build or verification script (e.g., "Standard out of `./gradlew assembleDebug` exiting with code 0").
* **E2E/Integration:** Appium, Robolectric, or Espresso test logs passing for the specific user journey.

* [Required proof 1]
* [Required proof 2]
```

## 2. Gathering and Beginning Work
When you are instructed to "begin work", "start", or "launch", you must follow this sequence:
1. Search the Kanban board for relevant `Backlog` or `To Do` tickets.
2. Group related tasks logically into a sprint or cycle.
3. Use the `mcp_kanban_begin_work` tool to assign the tickets, place them in a cycle, and transition them to `In Progress`.
   - Example: `mcp_kanban_begin_work(ticket_ids=["SSH-1", "SSH-2", "SSH-3"], cycle_name="Initial Sprint")`

## 3. Execution and Agent Collaboration
- **Design:** Before coding, use specialist agents (`codebase_investigator`, `security-auditor`) to architect the solution securely.
- **Implement:** Execute the required work with absolute adherence to the project's security invariants.
- **Test:** Verify the work rigorously with local testing (`./gradlew test lint`) and CI validation.
- **QA:** After coding, consult Testing and QA agents to verify the implementation.

## 4. Proof Gathering & Artifacts
The QA Gate on this project is strictly guarded by the `@reality-checker` agent. It performs a rigorous audit before allowing any ticket to transition to "Done".
- **Communication:** All communication, discussion, and status updates to the `@reality-checker` MUST be recorded as comments on the Kanban ticket itself using the `mcp_kanban_update_ticket` tool. Do not use the `docs/qa/` directory for conversation or notes to the QA gate.
- **Artifacts:** You MUST gather irrefutable proof of completion. Leverage agents like `evidence-collector` to assemble logs, test outputs, screenshots, or receipts.
- Record all hard proof artifacts in the `docs/qa/` folder (e.g., `docs/qa/SSH-1.md`) or in screenshot directories (`app/src/test/snapshots/images/`).
- Ensure all code and proof files are committed and pushed to trigger the CI pipeline.

## 5. Closure
- Use the `mcp_kanban_complete_work` tool to finalize the ticket.
- Your comment on the ticket MUST explicitly explain the work done and reference the proof artifacts gathered in the `docs/qa/` directory.
   - Example: `mcp_kanban_complete_work(ticket_id="SSH-1", comment="Feature is completed. Added strict bounds checking to the network parser. See proof artifacts in docs/qa/SSH-1.md.")`
- If the `reality-checker` rejects the completion, read the feedback, address the deficiencies, and repeat the QA loop. Never leave a broken or rejected ticket hanging.