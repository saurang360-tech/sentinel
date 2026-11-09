# Android Studio build workflow

All current app builds must use Android Studio/MCP, as requested by the owner.
Studio's bundled JDK supplies the Java compiler. Do not infer a valid APK from a
generic IDE module build; request the explicit Gradle tasks through its terminal.

## Current project workflow

Open this project in Android Studio. Enable Settings > Tools > MCP Server, keep
command confirmation enabled and configure the Codex Studio connection. The
transport helper reads the loopback endpoint from the current user's local
Codex configuration; no machine-specific endpoint or credential is committed.

```sh
python tools/studio_mcp.py get_project_modules
```

Only proceed once this reports the intended project/modules. Build these Gradle
tasks in Studio's terminal via MCP for release verification:

```text
:app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease
```

Use Studio's bundled JDK as JAVA_HOME for its terminal if necessary. The owner's
verified local installation is `/home/Amus/Applications/android-studio`; this is
an environment detail, not a required path on other developers' computers.
`tools/studio_mcp.py` currently uses the local Codex studio MCP entry; adapt local
configuration for another environment, not production app source.

If connection is refused, open Studio/project and verify its server settings.
If Studio waits for Allow command execution, approve that command in Studio;
do not disable confirmation or claim the command ran before its log starts.
A long build can run in Studio's terminal with output redirected to a temporary
log. Check for BUILD SUCCESSFUL and actual test/lint/artifact results.

On 9 November 2026, MCP project inspection and the explicit release verification
build succeeded. The [readiness audit](PLAY-READINESS-AUDIT-2026-11-09.md) records
that run. Installation/connection and test counts in older documents describe
historical passes and are not guarantees for a different checkout or device.

After building, run [the reusable offline checks](RELEASE-READINESS.md). No build
or local check alone establishes Play Console approval. Do not commit local
SDK paths, signing configurations, private keys or generated build output.

Official IDE integration reference:
https://www.jetbrains.com/help/idea/mcp-server.html
