#!/usr/bin/env bash
# .agents/hooks/prevent-curl-bypass.sh
# Fast-path sub-10ms CLI bypass shield

PAYLOAD=$(cat -)
COMMAND=$(echo "$PAYLOAD" | jq -r '.toolCall.args.CommandLine // .toolCall.args.command // .tool_input.command // empty')

if [ -n "$COMMAND" ]; then
    if echo "$COMMAND" | grep -Ei "(kanban\.hackedyour\.info|/api/v1/workspaces|/api/v1/projects|/api/v1/issues)" > /dev/null 2>&1; then
        echo '{"decision": "deny", "reason": "Bypass detected: Direct CLI requests to Kanban endpoints are strictly forbidden."}'
        exit 0
    fi

    if echo "$COMMAND" | grep -Ei "(curl|wget|http|httpie|plane|gh|python|python3|node|perl|ruby).*(Done|Completed|ae56a905-81b7-4f9a-a2e5-7a842d66b8f4|complete_work|state_id|status=done)" > /dev/null 2>&1; then
        echo '{"decision": "deny", "reason": "Bypass detected: Direct CLI ticket closure is strictly forbidden. Must pass through the automated QA Gate."}'
        exit 0
    fi

    if echo "$COMMAND" | grep -Ei "(echo|printf|cat|env|printenv|grep|tail|head|less|more).*(KANBAN_API_KEY|mcp_config\.json)" > /dev/null 2>&1; then
        echo '{"decision": "deny", "reason": "Security violation: Exfiltration of KANBAN_API_KEY or MCP credentials is prohibited."}'
        exit 0
    fi
fi

echo '{"decision": "allow"}'
