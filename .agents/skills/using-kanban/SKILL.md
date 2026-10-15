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

## 3. Multi-Agent Branch Orchestration (The Merge-Train Pattern)
- **Design:** Before coding, consult specialist subagents (`research`, `security-auditor`, `ux-architect`, `mobile-app-builder`) in parallel and record preflight recommendations on the ticket.
- **Isolated Worker Subagents (`Workspace: "share"`):** Spawn implementation subagents on isolated feature branches (`feat/SSH-XXX-...`) using `Workspace: "share"` (and `Model: "flash"` when requested for high-velocity execution).
- **Worker Invariants (NO PUSH / NO CLOSURE):** Worker subagents must implement code, unit/UI tests, `docs/qa/SSH-XXX.md`, and `user_stories/SSH-XXX.md`, run targeted local tests, and commit to their feature branch. **Worker subagents MUST NEVER run `git push`, `complete_work`, or `transition_ticket`**—pushing from multiple subagents causes $N \times \text{CI}$ queue delays and conflicts with the `git-push-after.sh` hook.
- **Lead Orchestrator Batch Merge & Single Push:** The Lead Orchestrator merges completed worker branches into `main`, runs consolidated local verification (`./gradlew test lint`), pushes once to `origin/main`, and waits for a single green CI run (`✅ Pass` on Dash) before closing the batch of tickets.

## 4. The QA Gate & Anti-Hallucination Protocol

The QA Gate exists specifically to **prevent hallucinations** and counter the **"agent frustration metric"** (the tendency of LLMs to make a partial tweak after receiving feedback and prematurely mark a ticket `Done` without completing the full verification loop).

The QA Gate enforces six non-negotiable minimum requirements before any ticket can reach `Done`:

1. **All Files Committed:** Zero remnants, zero uncommitted modifications, and a completely clean working tree (`git status --porcelain` is empty).
2. **All Commits Pushed:** All local commits must be pushed upstream to the remote tracking repository. No unpushed local tweaks.
3. **All Tests Complete & Passing:** Automated quality control and test pipelines must execute cleanly (zero failures, zero unhandled errors).
4. **`reality-checker` Evaluation:** The `reality-checker` agent personality (see `.agents/agents/reality-checker.md` and `~/.gemini/agents/reality-checker.md`) runs as an independent, clean-room auditor. It is supplied with:
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

## 5. Closure, MCP Schema & QA Gate Parsing Rules

- **Kanban MCP Parameter Names:**
  - `transition_ticket` requires `{"ticket_id": "SSH-XXX", "state_name": "Done"}` (or `"In Progress"`). Note that the parameter is `state_name`, **not** `state`.
  - `search_tickets` accepts `{"project_slug": "SSH"}` and optional `cursor` for pagination (`"50:1:0"`). It does **not** accept `state_name`.
- **Sequential Closure (POSIX `GateLock`):** Never invoke `transition_ticket` for multiple tickets in parallel within the same tool turn. `.agents/hooks/qa-gate.py` acquires a non-blocking POSIX lock (`/tmp/qa-gate.lock`) and will immediately deny concurrent calls. Rate limiting (`30s`) is tracked per-ticket (`/tmp/antigravity_qa_gate/rate_SSH-XXX.txt`), so different tickets can be transitioned sequentially one after another without a 30s wait between different ticket IDs.
- **Unbolded Verdict Status Formatting:** When invoking `reality-checker` subagents, instruct them to format the status lines in their report with plain unbolded text after the colon:
  - `**Production Readiness**: READY` (NOT `**Production Readiness**: **READY**`)
  - `**Status**: READY` (NOT `**Status**: **READY**`)
  Wrapping `READY` in `**` asterisks causes `qa-gate.py`'s regex `r'\*\*(?:Production Readiness|Status)\*\*:\s*([A-Za-z\s_-]+)'` to match only the leading space before `**READY**` and falsely reject the transition.
- **Rejection Handling & 3-Strike Circuit Breaker:** `qa-gate.py` enforces a 3-strike circuit breaker per ticket and git tree hash (`strikes_<TICKET>_<TREE_HASH>.json`). If a ticket fails QA twice on the same git tree, halt and fix the underlying code/artifacts (or report any gate parsing issue to the user) before attempting a 3rd transition.