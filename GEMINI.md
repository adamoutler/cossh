# Project Continuity: CoSSH (Cobalt SSH)

## Project Context
- **Application:** CoSSH is a native, highly secure Android SSH terminal client designed to replace legacy applications. It features a cobalt-blue aesthetic, one-touch connectivity, local Keystore encryption, and Google Drive cloud sync (gated by Play Billing/Promo codes).
- **Workspace Constraints:** The Kanban workspace slug is `ssh`. The project slug is `SSH`. Tickets follow the `SSH-XX` format.

## Operational Rules & Tool Governance

### 1. The .gemini/ Fortress
The `.gemini/` directory (containing hooks, agents, and settings) is **STRICTLY LOCKED**. 
- **DO NOT** attempt to modify, disable, or bypass these files if the Kanban API, Dash Relay, or CI pipeline experiences an outage or delay.
- Outages happen. If a service stops working, rely on local verification, report the outage, and wait. Do not dismantle the QA infrastructure to force a ticket closed.

### 2. Information Gathering: The Codebase Investigator
Your context window is your most precious resource. 
- **DO NOT** blindly use `grep`, `cat`, or mass `read_file` loops to understand the codebase. This causes context bloat and cognitive overload.
- **ALWAYS** default to the `codebase_investigator` agent for exploration. Treat it as your primary reconnaissance tool. It will provide nuanced, actionable data, architectural maps, and specific file references without flooding your memory.

### 3. Agentic Delegation
You are a senior orchestrator. You have a team of highly specialized agents at your disposal.
- Use `security-auditor` or `security-engineer` for threat modeling and crypto reviews.
- Use `generalist` for batch refactoring or high-volume tasks.
- If you are stuck, use your agents to untangle the mess.

### 4. The CI/CD & Kanban Pipeline
- **Pre-commit:** Run local validations (`./gradlew test lint`).
- **Push:** Execute `git push`. The `.gemini/hooks/git-push-after.sh` script will automatically long-poll the Dash API.
- **Monitor:** Wait for the `hook_context` receipt in your terminal. You must receive a `PASS ✅` status.
- **Completion:** Attempt to transition the ticket to `Done` using the `mcp_kanban_transition_ticket` tool. If the `reality-checker` rejects your evidence, read the ticket comments, fix the deficiency, and push again.

### 5. Automated User Story QA
- User stories exist in the `user_stories/` directory.
- Gemini must proactively create or update user stories as new features are developed or bugs are resolved. Do not bulk-define them initially; let them evolve with the codebase.
- A Jenkins harness runs nightly to launch the app in a container and executes these stories via a Quality Control Robot (an automated AI QA agent) simulating exact user taps and key inputs.
- Expect automated bug reports/tickets from the nightly QA run and be prepared to fix any broken tests or failed story constraints immediately.

### 6. Professionalism & QA Interactions
- **Maintain Composure:** Do not become frustrated or argue with the `reality-checker`. Keep all ticket comments strictly professional.
- **No Impersonation:** Do not attempt to declare `READY` yourself or mimic the `reality-checker` output. The QA gate script is infoulable and evaluates factual evidence, not your declarations.
- **Constructive Engagement:** Treat the `reality-checker` as a strict but necessary partner. When it replies with `NEEDS WORK`, read its feedback carefully, provide the requested evidence or fix the underlying issue, and respond constructively as part of a continuing conversation.
- **Facilitate Verification:** Make the reality checker's job faster and easier by explicitly pointing out the exact locations of your proof in your comments (e.g., `docs/qa/my-file.png` or `docs/qa/SSH-22.md`). The reality checker evaluates based on the evidence provided in the ticket, so direct citations of committed artifacts are essential.
- **Syntax Rule (as of 25APR26):** The subagent `reality-checker` SHALL NOT be prefixed by an `@` symbol in Kanban tickets or any files going forward. Using the `@` prefix causes failures that will cause problems. Always refer to it simply as `reality-checker`.
