#!/usr/bin/env bash
# .agents/hooks/remind-comment.sh

input=$(cat)
tool_name=$(echo "$input" | jq -r '.toolCall.name // .tool_name // empty')
sub_tool=$(echo "$input" | jq -r '.toolCall.args.ToolName // empty')
comment_arg=$(echo "$input" | jq -r '.toolCall.args.Arguments.comment // .toolCall.args.comment // empty')

if [ -z "$comment_arg" ] && { [[ "$tool_name" == *"update_ticket"* || "$tool_name" == *"transition_ticket"* || "$sub_tool" == "update_ticket" || "$sub_tool" == "transition_ticket" ]]; }; then
    echo "REMINDER: If you just updated the ticket, do not forget to add a comment detailing your changes." >&2
fi
echo '{}'
