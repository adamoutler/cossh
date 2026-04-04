#!/bin/bash
# Read input from Gemini CLI
INPUT=$(cat)

tool_name=$(echo "$INPUT" | jq -r '.tool_name')

if [[ "$tool_name" =~ run_shell_command|Bash|shell ]]; then
    command=$(echo "$INPUT" | jq -r '.tool_input.command')

    if [[ "$command" =~ git[[:space:]]+push ]]; then
        echo "Waiting 5 seconds for CI to register the build..." >&2
        sleep 5
        
        REPO_URL=$(git config --get remote.origin.url)
        PROVIDER="github"
        OWNER="adamoutler"
        REPO="cossh"
        if [[ "$REPO_URL" =~ github\.com[:/]([^/]+)/([^/.]+)(\.git)? ]]; then
            OWNER="${BASH_REMATCH[1]}"
            REPO="${BASH_REMATCH[2]}"
        elif [[ "$REPO_URL" =~ git\.adamoutler\.com[:/]([^/]+)/([^/.]+)(\.git)? ]]; then
            PROVIDER="forgejo"
            OWNER="${BASH_REMATCH[1]}"
            REPO="${BASH_REMATCH[2]}"
        fi

        echo "Watching for CI completion via Dashboard API for $OWNER/$REPO on $PROVIDER..." >&2
        
        FINAL_STATUS_JSON=$(curl -N -s "https://dash.hackedyour.info/api/wait?provider=$PROVIDER&owner=$OWNER&repo=$REPO" | grep '{' | tail -n 1)
        STATUS=$(echo "$FINAL_STATUS_JSON" | jq -r '.status // empty')
        ACTION_URL=$(echo "$FINAL_STATUS_JSON" | jq -r '.url // empty')
        
        LOG_URL="https://dash.hackedyour.info/api/logs?provider=$PROVIDER&owner=$OWNER&repo=$REPO"
        
        if [ "$STATUS" = "failure" ] || [ "$STATUS" = "error" ]; then
            LOG_FILE="/tmp/${PROVIDER}_${REPO}_failed.log"
            curl -s "$LOG_URL" | jq -r '.log' > "$LOG_FILE"
            LOG_SIZE=$(wc -c < "$LOG_FILE" 2>/dev/null || echo 0)
            LAST_LINES=$(tail -n 30 "$LOG_FILE" 2>/dev/null || echo "No logs found.")
            
            FULL_MESSAGE="CI PIPELINE STATUS: FAIL ❌
View Logs: $ACTION_URL
API Logs Endpoint: $LOG_URL
WARNING: Log size is $LOG_SIZE bytes. Consuming the full log will heavily impact your AI context window!

Last 30 lines:
$LAST_LINES"

            jq -n -c --arg result "$FULL_MESSAGE" \
              '{"decision": "allow", "systemMessage": $result, "hookSpecificOutput": {"additionalContext": $result}}'
            
        elif [ -n "$STATUS" ] && [ "$STATUS" != "null" ]; then
            LOG_FILE="/tmp/${PROVIDER}_${REPO}_success.log"
            curl -s "$LOG_URL" | jq -r '.log' > "$LOG_FILE"
            LOG_SIZE=$(wc -c < "$LOG_FILE" 2>/dev/null || echo 0)
            
            FULL_MESSAGE="CI PIPELINE STATUS: PASS ✅
View Logs: $ACTION_URL
API Logs Endpoint: $LOG_URL
WARNING: Log size is $LOG_SIZE bytes. Consuming the full log will heavily impact your AI context window!"

            jq -n -c --arg result "$FULL_MESSAGE" \
              '{"decision": "allow", "systemMessage": $result, "hookSpecificOutput": {"additionalContext": $result}}'
        else
            MSG="CI PIPELINE STATUS: UNKNOWN ❓\nCould not determine CI status from dashboard."
            jq -n -c --arg result "$MSG" \
              '{"decision": "allow", "systemMessage": $result, "hookSpecificOutput": {"additionalContext": $result}}'
        fi
        exit 0
    fi
fi

echo '{"decision": "allow"}'
