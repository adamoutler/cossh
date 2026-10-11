#!/usr/bin/env bash
# .agents/hooks/git-push-after.sh

input=$(cat)
command=$(echo "$input" | jq -r '.toolCall.args.CommandLine // .toolCall.args.command // .tool_input.command // empty')

if [[ "$command" =~ (^|[[:space:]\;\&\|])git[[:space:]]+push([[:space:]]|$) ]]; then
    OWNER="adamoutler"
    REPO="cossh"
    sleep 10

    # Use the wait endpoint to ensure we have waited for current build.
    curl -N -s "https://dash.hackedyour.info/api/wait?provider=github&owner=${OWNER}&repo=${REPO}" > /dev/null 2>&1
    
    # get the actual status.
    CI_STATUS=$(curl -s https://dash.hackedyour.info/api/status 2>/dev/null | jq -r '.[] | select(.provider == "github" and .owner == "adamoutler" and .repo == "cossh") | .status')

    if [ "$CI_STATUS" = "failed" ]; then
        CI_LOGS=$(curl -s "https://dash.hackedyour.info/api/logs?provider=github&owner=${OWNER}&repo=${REPO}" 2>/dev/null)
        LAST_LINES=$(echo "$CI_LOGS" | jq -r '.log // empty' | tail -n 30)
        jq -n -c --arg status "$CI_STATUS" --arg logs "$LAST_LINES" \
          '{"decision": "deny", "reason":"❌ CI/CD FAILURE", "hookSpecificOutput": {"hookEventName": "PostToolUse", "additionalContext": "GitHub Actions workflow failed!\nLast 30 lines of log:\n\($logs)\n❌ CI results: \($status)\nYou must not leave the build broken."}}'
    else
        jq -n -c --arg status "$CI_STATUS" \
          '{"decision": "allow", "hookSpecificOutput": {"hookEventName": "PostToolUse", "additionalContext": "CI results: \($status)"}}'
    fi
    exit 0
fi

# Proceed normally
echo '{}'
