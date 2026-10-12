---
name: using-kanban
description: Strict workflow for managing Agentic Kanban tasks, including batching tickets, execution phases, proof gathering in docs/qa, and surviving the reality-checker QA gate.
---
# The CoSSH Kanban Workflow

This project heavily relies on an Agentic Kanban board. The Kanban project slug is `SSH`, meaning tickets follow the format `SSH-<sequence_id>` (e.g., `SSH-1` is Project SSH, Sequence ID 1).

## 1. Ticket Creation & Standardization
When creating or updating tickets, you MUST provide clear criteria for completion. If you do not, the `reality-checker` agent will invent its own criteria, which may be impossible to satisfy. All tickets MUST include the following structured sections in their description:

1. **User Story:** Explains the "who, what, and why".
2. **Verification Proof:** Explicitly lists the hard evidence required to consider the ticket done (e.g., screenshots, UI test passes, logcat traces).

**Standard Ticket Description Format:**
```markdown
**User Story:** *As a [role], I need [feature/fix] so that [benefit].*

**Verification Proof:**
To satisfy the `reality-checker`, the proof MUST be explicit and artifact-based. Use the following examples to guide your criteria:

* **Visual/UI Changes:** Screenshot artifact demonstrating the specific UI state (e.g., "Screenshot of the 'Key Management' screen showing a generated ED25519 key").
* **Logic/Backend Changes:** Logcat trace or output from a unit test confirming the behavior (e.g., "Logcat trace proving successful headless SSH connection" or "Unit test passing for `SecurityStorageManager`").
* **File/System Changes:** Standard output of a successful build or verification script (e.g., "Standard out of `./gradlew assembleDebug` exiting with code 0").
* **E2E/Integration:** Appium, Robolectric, or Espresso test logs passing for the specific user journey.

* [Required proof 1]
* [Required proof 2]
```

## 2. Gathering and Beginning Work
When you are instructed to "begin work", "start", or "launch", you must follow this sequence:
1. Search the Kanban board for relevant `Backlog` or `To Do` tickets using `search_tickets`.
2. Group related tasks logically into a sprint or cycle.
3. Use the `begin_work` tool to assign the tickets, place them in a cycle, and transition them to `In Progress`.
   - Example: `begin_work(ticket_ids=["SSH-1", "SSH-2", "SSH-3"], cycle_name="Initial Sprint")`

## 3. Execution and Agent Collaboration
- **Design:** Before coding, use specialist agents (`codebase_investigator`, `security-auditor`) to architect the solution securely.
- **Implement:** Execute the required work with absolute adherence to the project's security invariants.
- **Test:** Verify the work rigorously with local testing (`./gradlew test lint`) and CI validation.
- **QA:** After coding, consult Testing and QA agents to verify the implementation.

## 4. The QA Gate & Anti-Hallucination Protocol

The QA Gate exists specifically to **prevent hallucinations** and counter the **"agent frustration metric"** (the tendency of LLMs to make a partial tweak after receiving feedback and prematurely mark a ticket `Done` without completing the full verification loop).

The QA Gate enforces six non-negotiable minimum requirements before any ticket can reach `Done`:

1. **All Files Committed:** Zero remnants, zero uncommitted modifications, and a completely clean working tree (`git status --porcelain` is empty).
2. **All Commits Pushed:** All local commits must be pushed upstream to the remote tracking repository. No unpushed local tweaks.
3. **All Tests Complete & Passing:** Automated quality control and test pipelines must execute cleanly (zero failures, zero unhandled errors).
4. **`reality-checker` Evaluation:** The `reality-checker` agent personality (see `~/.gemini/agents/reality-checker.md`) runs as an independent, clean-room auditor. It is supplied with:
   - Automated test results and execution logs
   - Direct access to the code and git diff
   - The ticket description and its full comment thread
   - Hard proof artifacts (screenshots, traces, metrics)
   The `reality-checker` produces an evidence-based report that is posted directly as a comment on the Kanban ticket.
5. **Enforced Feedback Loop:** If the `reality-checker` returns `NEEDS WORK` (or worse), the agent MUST inspect the feedback, address every specific defect identified, and repeat the full validation loop. Premature closure is strictly blocked.
6. **Restricted Communication Channel:** The agent can communicate with `reality-checker` **ONLY** via comments on the Kanban ticket or through committed proof artifacts.
   - Kanban comments should be used sparingly for direct clarifications: e.g., *"See artifact in docs/qa/SSH-X.md"*, *"This case cannot occur because..."*, or *"Addressed feedback by adding bounds checking in commit Y"*.
   - Never attempt to self-declare `READY` in comments. Only the independent `reality-checker` report certifies production readiness.

---

## 5. Closure & Proof Citations

- **Trigger Closure:** Use the `complete_work` or `transition_ticket` tool to finalize the ticket.
- **Closure Comment:** Your comment on the ticket MUST explicitly summarize the work completed and cite the exact committed proof artifacts:
  - Example: `complete_work(ticket_id="SSH-1", comment="Feature is completed. Added strict bounds checking to the network parser. See proof artifacts in docs/qa/SSH-1.md.")`
- **Rejection Handling:** If rejected by `reality-checker`, treat the feedback as a continuing conversation. Fix the underlying issue, re-run tests, commit, push, update the ticket with new proof, and repeat the gate. Never abandon a rejected ticket.