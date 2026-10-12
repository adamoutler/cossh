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
        echo "❌ CI/CD FAILURE: GitHub Actions workflow failed!" >&2
        echo "Last 30 lines of log:" >&2
        echo "$LAST_LINES" >&2
        echo "❌ CI results: $CI_STATUS" >&2
        echo "You must not leave the build broken." >&2
    else
        echo "CI results: $CI_STATUS" >&2
    fi
    echo '{}'
    exit 0
fi

# Proceed normally
echo '{}'
