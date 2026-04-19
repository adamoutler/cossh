#!/usr/bin/env python3
import sys, json, os, urllib.request, time, subprocess

def allow():
    print('{"decision": "allow"}')
    sys.exit(0)

def deny(reason):
    print(json.dumps({"decision": "deny", "reason": reason}))
    sys.exit(0)

try:
    payload = json.loads(sys.stdin.read())
except:
    allow()

tool_name = payload.get("tool_name", "")
tool_input = payload.get("tool_input", {})

ticket_id = tool_input.get("ticket_id")
state_name = tool_input.get("state_name")

is_completing = False
if tool_name in ["mcp_kanban_complete_work", "complete_work"] and ticket_id:
    is_completing = True
elif tool_name in ["mcp_kanban_transition_ticket", "transition_ticket"] and ticket_id and state_name in ["Done", "Completed"]:
    is_completing = True

if not is_completing:
    allow()

# Removed TOCTOU rate limit

# Bypass Flags (FOR TESTING ONLY)
BYPASS_UNCOMMITTED = False
BYPASS_PUSHED = False
BYPASS_CI = False

# Pre-flight checks
if not BYPASS_UNCOMMITTED:
    status_out = subprocess.run(["git", "status", "--porcelain"], capture_output=True, text=True).stdout.strip()
    if status_out:
        deny("please commit all project files and delete non-project files - if there are any uncommitted files.")

if not BYPASS_PUSHED:
    status_sb = subprocess.run(["git", "status", "-sb"], capture_output=True, text=True).stdout
    if "ahead" in status_sb:
        deny("Git repository has unpushed commits. Please push changes before QA to ensure we match the main repo.")

# Check CI
repo_url = subprocess.run(["git", "config", "--get", "remote.origin.url"], capture_output=True, text=True).stdout.strip()
provider, owner, repo = "github", "adamoutler", "cossh"
if "github.com" in repo_url:
    parts = repo_url.split("github.com")[1].strip(":/").replace(".git", "").split("/")
    if len(parts) >= 2:
        owner, repo = parts[0], parts[1]
elif "git.adamoutler.com" in repo_url:
    provider = "forgejo"
    parts = repo_url.split("git.adamoutler.com")[1].strip(":/").replace(".git", "").split("/")
    if len(parts) >= 2:
        owner, repo = parts[0], parts[1]

if not BYPASS_CI:
    req = urllib.request.Request("https://dash.hackedyour.info/api/status")
    try:
        with urllib.request.urlopen(req) as response:
            statuses = json.loads(response.read().decode())
        repo_status = next((s for s in statuses if s["owner"] == owner and s["repo"] == repo), None)
        if not repo_status:
            deny(f"No CI run found on dashboard for {owner}/{repo}. Please push your changes and wait for checks.")
        if repo_status and repo_status.get("status") != "success":
            deny(f"CI run did not succeed (status: {repo_status.get('status')}). Please fix the build before transitioning to Done.")
    except Exception as e:
        print(f"WARNING: Failed to fetch or parse CI status: {e}. Bypassing CI check.", file=sys.stderr)

# Fetch logs
try:
    req_logs = urllib.request.Request(f"https://dash.hackedyour.info/api/logs?provider={provider}&owner={owner}&repo={repo}")
    with urllib.request.urlopen(req_logs) as response:
        logs_data = json.loads(response.read().decode())
        ci_receipt = logs_data.get("log", "")
except Exception as e:
    ci_receipt = str(e)

current_commit = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()

# Plane API Fetch
api_key = os.environ.get("KANBAN_API_KEY", "")
headers = {"x-api-key": api_key, "Content-Type": "application/json"}

# 1. Get Project ID
try:
    req_proj = urllib.request.Request("https://kanban.hackedyour.info/api/v1/workspaces/ssh/projects/", headers=headers)
    with urllib.request.urlopen(req_proj) as response:
        projects = json.loads(response.read().decode()).get("results", [])
    project_id = next((p["id"] for p in projects if p["identifier"] == "SSH"), None)
    if not project_id:
        deny("Could not find Project 'SSH' in workspace 'ssh'.")
except Exception as e:
    deny(f"Failed to fetch projects: {e}")

# 2. Get Issue ID
seq_id = ticket_id.split("-")[-1]
try:
    req_issues = urllib.request.Request(f"https://kanban.hackedyour.info/api/v1/workspaces/ssh/projects/{project_id}/issues/?sequence_id={seq_id}", headers=headers)
    with urllib.request.urlopen(req_issues) as response:
        issues = json.loads(response.read().decode()).get("results", [])
    issue = next((i for i in issues if str(i["sequence_id"]) == seq_id), None)
    if not issue:
        deny(f"Could not find issue {ticket_id}.")
    issue_id = issue["id"]
    ticket_name = issue.get("name", "Unknown Ticket")
except Exception as e:
    deny(f"Failed to fetch issue {ticket_id}: {e}")

# 3. Get Comments
try:
    req_comments = urllib.request.Request(f"https://kanban.hackedyour.info/api/v1/workspaces/ssh/projects/{project_id}/issues/{issue_id}/comments/", headers=headers)
    with urllib.request.urlopen(req_comments) as response:
        comments_data = json.loads(response.read().decode()).get("results", [])
    
    comments_text = ""
    for c in comments_data:
        comments_text += f"User Id: {c.get('created_by')}\n"
        comments_text += f"Last Updated: {c.get('updated_at') or c.get('created_at')}\n"
        comments_text += f"{c.get('comment_html')}\n"
        comments_text += f"Attachments: {json.dumps(c.get('attachments', []))}\n---\n"
except Exception as e:
    deny(f"Failed to fetch comments: {e}")

# Create Ticket File
ticket_file = f"/tmp/ticket_{issue_id}.md"
with open(ticket_file, "w") as f:
    f.write(f"---\nname: {ticket_name}\ndescription: The kanban ticket to be closed. This should be evaluated as the reference source for ticket completion and the criteria for evaluation.\n---\n")
    f.write(json.dumps(issue, indent=2) + "\n\n")
    f.write(f"---\nname: Kanban Ticket Comments\ndescription: The discussion and history on the ticket including any attachments.\n---\n{comments_text}\n\n")
    f.write(f"---\nname: CI Dashboard Build Receipt\ndescription: The build results from the CI Dashboard for commit {current_commit}\n---\n{ci_receipt}\n")

# Run Gemini
import fcntl

lock_file_path = "/tmp/qa-gate.lock"
if not os.path.exists(lock_file_path):
    open(lock_file_path, "a").close()

lock_fd = open(lock_file_path, "r+")
try:
    fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
    lock_fd.seek(0)
    lock_fd.truncate()
    lock_fd.write(str(time.time()))
    lock_fd.flush()
except BlockingIOError:
    lock_fd.seek(0)
    try:
        lock_time = float(lock_fd.read().strip())
        try_again_time = time.strftime('%H:%M:%S', time.localtime(lock_time + 300))
    except:
        try_again_time = time.strftime('%H:%M:%S', time.localtime(time.time() + 300))
    deny(f"Another QA assessment is currently in progress. Please try again at {try_again_time}.")

print("allowing time to settle before reality checker", file=sys.stderr)
time.sleep(20)

result = subprocess.run(["gemini", "-p", f" @reality-checker Please verify if the work item {ticket_id} is completed. Read the comments thoroughly. If the evidence is satisfactory, respond with READY. Otherwise, respond with a report, including keyword NEEDS WORK and what is expected to complete the ticket."], stdin=open(ticket_file, "r"), capture_output=True, text=True)

try:
    fcntl.flock(lock_fd, fcntl.LOCK_UN)
    lock_fd.close()
except:
    pass

output_text = (result.stdout or "") + "\n" + (result.stderr or "")
if "429" in output_text and "too many requests" in output_text.lower():
    deny(f"Detected problem with gemini cli: 429 Too Many Requests. Please implement an exponential backoff using sleep and try again. Output: {output_text}")
elif result.returncode != 0:
    deny(f"No quality control available. Gemini command exited with {result.returncode}. This appears to be a mundane error, please try again. Output: {output_text}")

# Post comment
try:
    payload = {
        "comment_html": f"### ═══ REALITY CHECKER ═══\n\n{result.stdout}",
        "external_id": current_commit,
        "external_source": "github"
    }
    post_data = json.dumps(payload).encode("utf-8")
    req_post = urllib.request.Request(f"https://kanban.hackedyour.info/api/v1/workspaces/ssh/projects/{project_id}/work-items/{issue_id}/comments/", data=post_data, headers=headers, method="POST")
    with urllib.request.urlopen(req_post) as response:
        pass
except Exception as e:
    print(f"WARNING: Failed to post reality-checker comment to ticket: {e}", file=sys.stderr)
    if hasattr(e, "read"):
        print(f"Response: {e.read().decode()}", file=sys.stderr)

if "NEEDS WORK" in result.stdout:
    deny("Reality checker blocked the transition to Done. Please review the ticket comments for details.")

allow()
