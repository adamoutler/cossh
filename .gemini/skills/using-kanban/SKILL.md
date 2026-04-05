---
name: using-kanban
description: Strict workflow for managing Agentic Kanban tasks, including batching tickets, execution phases, proof gathering in docs/qa, and surviving the reality-checker QA gate.
---
# The CoSSH Kanban Workflow

This project heavily relies on an Agentic Kanban board. The Kanban project slug is `SSH`, meaning tickets follow the format `SSH-<sequence_id>` (e.g., `SSH-1` is Project SSH, Sequence ID 1).

## 1. Gathering and Beginning Work
When you are instructed to "begin work", "start", or "launch", you must follow this sequence:
1. Search the Kanban board for relevant `Backlog` or `To Do` tickets.
2. Group related tasks logically into a sprint or cycle.
3. Use the `mcp_kanban_begin_work` tool to assign the tickets, place them in a cycle, and transition them to `In Progress`.
   - Example: `mcp_kanban_begin_work(ticket_ids=["SSH-1", "SSH-2", "SSH-3"], cycle_name="Initial Sprint")`

## 2. Execution and Agent Collaboration
- **Design:** Before coding, use specialist agents (`codebase_investigator`, `security-auditor`) to architect the solution securely.
- **Implement:** Execute the required work with absolute adherence to the project's security invariants.
- **Test:** Verify the work rigorously with local testing (`./gradlew test lint`) and CI validation.
- **QA:** After coding, consult Testing and QA agents to verify the implementation.

## 3. Proof Gathering & Artifacts
The QA Gate on this project is strictly guarded by the `@reality-checker` agent. It performs a rigorous audit before allowing any ticket to transition to "Done".
- **Communication:** All communication, discussion, and status updates MUST be recorded as comments on the Kanban ticket itself. Do not use the `docs/qa/` directory for conversation.
- **Artifacts:** You MUST gather irrefutable proof of completion. Leverage agents like `evidence-collector` to assemble logs, test outputs, screenshots, or receipts.
- Record all hard proof artifacts in the `docs/qa/` folder (e.g., `docs/qa/SSH-1.md`).
- Ensure all code and proof files are committed and pushed to trigger the CI pipeline.

## 4. Closure
- Use the `mcp_kanban_complete_work` tool to finalize the ticket.
- Your comment on the ticket MUST explicitly explain the work done and reference the proof artifacts gathered in the `docs/qa/` directory.
   - Example: `mcp_kanban_complete_work(ticket_id="SSH-1", comment="Feature is completed. Added strict bounds checking to the network parser. See proof artifacts in docs/qa/SSH-1.md.")`
- If the `reality-checker` rejects the completion, read the feedback, address the deficiencies, and repeat the QA loop. Never leave a broken or rejected ticket hanging.
