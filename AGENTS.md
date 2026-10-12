# Project Continuity: CoSSH (Cobalt SSH) — Antigravity Rules

## Project Context
- **Application:** CoSSH is a native, highly secure Android SSH terminal client designed to replace legacy applications. It features a cobalt-blue aesthetic, one-touch connectivity, local Keystore encryption, and Google Drive cloud sync (gated by Play Billing/Promo codes).
- **Git Remotes & Source Control:**
  - **Primary Upstream (`origin`):** `ssh://git@192.168.1.100:2223/aoutler/CoSSH.git` (Forgejo). All active development, branches, and CI executions occur here.
  - **Source Code Mirror (`github`):** `git@github.com:adamoutler/cossh.git`. GitHub is strictly relegated to a secondary source code sync/mirror.
  - **GitHub Actions Runners:** Permanently DISABLED on GitHub due to pricing/per-minute billing restrictions.
  - **Local CI/CD Execution:** Handled locally via Forgejo `act_runner` at `https://git.adamoutler.com`.
  - **Dash Monitoring:** Tracked on Dash as `🍵 aoutler/CoSSH` (Forgejo provider).
- **Workspace Constraints:** Tickets follow the standard Kanban format defined by the project slug.
- **Initialization Mandate:** At the start of every new session or before interacting with any Kanban tasks, you MUST query Kanban (`call_mcp_tool(ServerName="kanban", ToolName="search_tickets", Arguments={"project_slug": "help"})`) to refresh your context on the available projects and slugs.

---

## Operational Rules & Tool Governance

### 1. The .gemini/ and .agents/ Fortress
- The `.gemini/` directory is **STRICTLY LOCKED** and preserved for legacy continuity. DO NOT attempt to modify, disable, or bypass these files.
- The `.agents/` directory houses the Antigravity configuration (hooks, skills, agents). Do not dismantle the QA infrastructure or bypass validation to force a ticket closed.

### 1.5. The Engineering Standard
- **Zero-Tolerance Quality:** No warnings of ANY type are accepted in this project. Zero warnings, no unhandled exceptions, strictly safe typing.
- **Shift-Left Absolute Mandate:** Security and quality must be proven before execution. Run strict local validations (`./gradlew test lint`) before committing.
- **Testing & Coverage:** All code must be rigorously tested. Maintain a minimum of 80% test coverage.

### 2. Information Gathering: The Codebase Investigator
- **DO NOT** blindly use `grep`, `cat`, or mass `read_file` loops to understand the codebase.
- **ALWAYS** default to the `codebase_investigator` agent for exploration as your primary reconnaissance tool.

### 3. Agentic Delegation
- You have over 60 specialized subagents at your disposal via `invoke_subagent`.
- Use `security-auditor` or `security-engineer` for threat modeling and crypto reviews.
- Use `generalist` for batch refactoring or high-volume tasks.
- Use `evidence-collector` to gather proof for QA certification.

### 4. The CI/CD & Kanban Pipeline
- **Pre-commit:** Run local validations (`./gradlew test lint`).
- **Push:** Execute `git push`. The `.agents/hooks/git-push-after.sh` hook will monitor the Dash API.
- **Monitor:** Ensure CI receives a `PASS ✅` status.
- **Completion:** Attempt to transition the ticket to `Done` using Kanban (`complete_work` or `transition_ticket`). The `reality-checker` QA Gate will audit the ticket comments and proof artifacts. If rejected, address deficiencies and re-verify.

### 5. Automated User Story QA
- User stories exist in the `user_stories/` directory. Proactively create or update user stories as new features are developed or bugs resolved.

### 6. Professionalism & QA Interactions
- **Maintain Composure:** Keep all ticket comments strictly professional.
- **No Impersonation:** Do not attempt to declare `READY` yourself. The QA gate evaluates factual evidence.
- **Facilitate Verification:** Explicitly cite the exact locations of proof in your ticket comments (e.g., `docs/qa/my-file.png` or `docs/qa/SSH-22.md`).
- **Syntax Rule:** The subagent `reality-checker` SHALL NOT be prefixed by an `@` symbol. Always refer to it simply as `reality-checker`.

### 7. SonarCloud Analysis
- **Project Key:** `adamoutler_cossh` | **Organization Key:** `adamoutler`
- Ensure `./gradlew testDebugUnitTest jacocoTestReport` completes cleanly with XML test reports generated and uploaded.
