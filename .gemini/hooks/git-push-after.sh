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
        
        if [ "$STATUS" = "failure" ] || [ "$STATUS" = "error" ]; then
            LOG_FILE="/tmp/${PROVIDER}_${REPO}_failed.log"
            curl -s "https://dash.hackedyour.info/api/logs?provider=$PROVIDER&owner=$OWNER&repo=$REPO" | jq -r '.log' > "$LOG_FILE"
            LAST_LINES=$(tail -n 30 "$LOG_FILE")
            jq -n -c --arg result "CI workflow run failed! Log saved to $LOG_FILE. Last 30 lines:\n$LAST_LINES" \
              '{"decision": "allow", "hookSpecificOutput": {"additionalContext": $result}}'
        elif [ -n "$STATUS" ] && [ "$STATUS" != "null" ]; then
            jq -n -c --arg result "CI workflow run finished with status: $STATUS" \
              '{"decision": "allow", "hookSpecificOutput": {"additionalContext": $result}}'
        else
            jq -n -c '{"decision": "allow", "hookSpecificOutput": {"additionalContext": "Could not determine CI status from dashboard."}}'
        fi
        exit 0
    fi
fi

echo '{"decision": "allow"}'
