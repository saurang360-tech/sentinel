package dev.titus.sentinel.monitor

/** Fixed SSH stdin scripts. Passwords are never interpolated into shell commands. */
internal object SshPowerCommands {
    const val READY="SENTINEL_POWER_READY_V1"
    const val CHALLENGE="SENTINEL_POWER_AUTH_REQUIRED_V1"
    const val PASSWORD_PROMPT="SENTINEL_PC_AUTH_V1:"
    const val EXEC_PROMPT="SENTINEL_PC_EXEC_AUTH_V1:"
    const val VERIFIED="SENTINEL_PC_AUTH_VERIFIED_V1"
    const val EXECUTE="SENTINEL_EXECUTE_POWEROFF_V1"
    const val REFUSED="SENTINEL_PC_REFUSED_V1"
    const val ACCEPTED="SENTINEL_POWEROFF_ACCEPTED_V1"
    private val QUERY="""r=${'$'}(busctl --system call org.freedesktop.login1 /org/freedesktop/login1 org.freedesktop.login1.Manager CanPowerOff 2>/dev/null) || exit 69; """
    val CHECK="""exec /bin/sh -c '${QUERY}case "${'$'}r" in "s \"yes\"") printf "%s\n" "$READY";; "s \"challenge\"") printf "%s\n" "$CHALLENGE"; exit 77;; *) exit 77;; esac'
"""
    private val AUTH="""sudo -k -S -p "$PASSWORD_PROMPT" -v || { printf "%s\n" "$REFUSED"; exit 77; }; printf "%s\n" "$VERIFIED"; """
    val AUTH_CHECK="""exec /bin/sh -c '${AUTH}exit 0'
"""
    val SHUTDOWN="""exec /bin/sh -c '${AUTH}IFS= read -r decision || exit 78; [ "${'$'}decision" = "$EXECUTE" ] || exit 78; sudo -k -S -p "$EXEC_PROMPT" -- /usr/bin/systemctl --check-inhibitors=yes poweroff || { printf "%s\n" "$REFUSED"; exit 77; }; printf "%s\n" "$ACCEPTED"'
"""
    val REBOOT=SHUTDOWN.replace("--check-inhibitors=yes poweroff", "--check-inhibitors=yes reboot")
}
