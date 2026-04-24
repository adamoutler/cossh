#!/bin/bash
SERVER="https://kanban.hackedyour.info"
PROJECT=ssh

for TICKET_ID in "SSH-92" "SSH-93"; do
  PREFIX="${TICKET_ID%%-*}"
  SEQ=$(echo "$TICKET_ID" | cut -d'-' -f2)
  PROJECT_ID=$(curl -s -X GET "${SERVER}/api/v1/workspaces/${PROJECT}/projects/" -H "x-api-key: $KANBAN_API_KEY" -H "Content-Type: application/json" | jq -r ".results[] | select(.identifier == \"$PREFIX\") | .id")
  TICKET_INFO=$(curl -s -X GET "${SERVER}/api/v1/workspaces/${PROJECT}/projects/${PROJECT_ID}/issues/?search=$TICKET_ID" -H "x-api-key: $KANBAN_API_KEY")
  WORK_ITEM_ID=$(echo "$TICKET_INFO" | jq -r ".results[] | select(.sequence_id == $SEQ) | .id")
  COMMENT_HTML="Commit hash: $(git rev-parse HEAD)"
  COMMENT_PAYLOAD=$(jq -n --arg html "$COMMENT_HTML" '{"comment_html": $html}')
  curl -s -X POST "${SERVER}/api/v1/workspaces/${PROJECT}/projects/$PROJECT_ID/issues/$WORK_ITEM_ID/comments/" -H "x-api-key: $KANBAN_API_KEY" -H "Content-Type: application/json" -d "$COMMENT_PAYLOAD"
  echo "Posted commit hash for $TICKET_ID"
done
