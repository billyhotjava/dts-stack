#!/usr/bin/env sh
set -eu

sanitize_wrapped_quotes() {
    value="${1-}"
    value="$(printf '%s' "$value" | sed 's/^[[:space:]\"]*//; s/[[:space:]\"]*$//')"
    printf '%s' "$value"
}

raw_java_tool_options="${JAVA_TOOL_OPTIONS:-}"
clean_java_tool_options="$(sanitize_wrapped_quotes "$raw_java_tool_options")"

if [ "$clean_java_tool_options" != "$raw_java_tool_options" ]; then
    echo "[entrypoint] sanitized wrapping quotes in JAVA_TOOL_OPTIONS"
fi

export JAVA_TOOL_OPTIONS="$clean_java_tool_options"

exec java ${JAVA_OPTS:-} -jar /app/app.jar
