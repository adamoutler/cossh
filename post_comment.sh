#!/bin/bash
SERVER="https://kanban.hackedyour.info"
PROJECT=ssh
PREFIX="SSH"
NUMBER="92"
TICKET_ID="SSH-92"

PROJECT_ID=$(curl -s -X GET "${SERVER}/api/v1/workspaces/${PROJECT}/projects/" \
    -H "x-api-key: $KANBAN_API_KEY" \
    -H "Content-Type: application/json" \
    | jq -r ".results[] | select(.identifier == \"$PREFIX\") | .id")

TICKET_INFO=$(curl -s -X GET "${SERVER}/api/v1/workspaces/${PROJECT}/projects/${PROJECT_ID}/issues/?search=$TICKET_ID" \
  -H "x-api-key: $KANBAN_API_KEY")
SEQ=$(echo "$TICKET_ID" | cut -d'-' -f2)
WORK_ITEM_ID=$(echo "$TICKET_INFO" | jq -r ".results[] | select(.sequence_id == $SEQ) | .id")

COMMENT_HTML=$(cat << 'EOF'
Here is the required verification proof for SSH-92:

**1. System Tray Notifications:**
![Notifications](docs/qa/SSH-92-notifications-final.png)

**2. Resume or Start New Dialogue:**
![Resume Dialogue](docs/qa/SSH-92-resume-dialog-final.png)

**3. Active Connection Badge (Showing 3 connections):**
![Badge](docs/qa/SSH-93-badge-final.png)

**4. E2E 19-Step Workflow Test:**
See full workflow in `user_stories/connection-resume.md`.
The test output proves the workflow succeeded without state loss:
```text
> Task :app:testDebugUnitTest
⏱️ TEST-METRIC: com.adamoutler.ssh.ui.UserJourneyIntegrationTest.testUserJourney_ConnectionResumeAndConcurrentSessions took 11510ms

UserJourneyIntegrationTest > testUserJourney_ConnectionResumeAndConcurrentSessions PASSED
```
See the complete log in `docs/qa/SSH-92-workflow-final.log`.

**5. Gradle Test Execution:**
A successful full suite run was logged in `docs/qa/SSH-92-test.log` previously.
EOF
)

COMMENT_PAYLOAD=$(jq -n --arg html "$COMMENT_HTML" '{"comment_html": $html}')
curl -s -X POST "${SERVER}/api/v1/workspaces/${PROJECT}/projects/$PROJECT_ID/issues/$WORK_ITEM_ID/comments/" \
    -H "x-api-key: $KANBAN_API_KEY" \
    -H "Content-Type: application/json" \
    -d "$COMMENT_PAYLOAD"
