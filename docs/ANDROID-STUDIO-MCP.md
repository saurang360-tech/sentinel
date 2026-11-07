# Android Studio MCP setup

App development must use Android Studio, per the owner's request. Verify IDE MCP connectivity before building.

## Verified locally

- IDE: /home/Amus/Applications/android-studio
- Build: AI-262.9437.185.2621.16467767
- Settings: /home/Amus/.config/Google/AndroidStudio2026.2.1
- Official JetBrains MCP Server plugin 262.9437.214 installed in /home/Amus/.local/share/Google/AndroidStudio2026.2.1/mcpserver
- Plugin declares compatibility with IDE builds 262.9437 through 262.*.
- Source: https://plugins.jetbrains.com/files/26071/1135009/mcpserver-262.9437.214.zip

## Pending activation

Android Studio was already running during installation. Restart Android Studio, then open Settings > Tools > MCP Server and enable the server. Use its Codex Auto-Configure action to write the actual connection configuration, then restart the Codex client as required by JetBrains documentation. Leave command confirmation enabled. IDE GUI automation is unavailable in the current chat, so these UI steps have not been performed.

Do not claim the integration is connected until a real MCP tools/list and project inspection succeed. Open /home/Amus/Android/PC_moniter_app in Android Studio and verify that this project is selected. Verify IDE analysis/build tools are available before app implementation.

## Rollback

Close Android Studio, remove only the newly installed mcpserver plugin directory above, and reopen it. If Codex configuration is subsequently added, back up its configuration before changing it and remove only that MCP entry when rolling back.

No app, workstation service, firewall, Tailscale policy, credentials, graphics driver or shutdown configuration was changed by this setup.

## References

- https://www.jetbrains.com/help/idea/mcp-server.html
- https://learn.chatgpt.com/docs/extend/mcp

## Connection verification

The IDE server is enabled and Codex has a `studio` entry targeting http://127.0.0.1:64342/stream. A live MCP initialize request succeeded, identifying Android Studio MCP Server, Rabbit 1 | 2026.2.1. tools/list returned the execute_tool router. A read-only get_project_modules request reached the IDE; it reported no open projects. The workspace was subsequently requested to open via the Android Studio launcher. Project access and builds remain to be verified once the IDE finishes opening it.

## Project access verified — 27 October 2026

get_project_modules successfully reported PC_moniter_app. open_file_in_editor successfully opened MainActivity.kt. The IDE initially imported this empty folder as a generic Java module; the Android Gradle scaffold was added afterward. The first generic IDE build returned success but did not constitute an APK build. APK assembly and lint were therefore launched explicitly in Android Studio’s integrated terminal through MCP. The system runtime has no javac; the build uses the compiler bundled with Android Studio. IDE Gradle project linking/sync is still required for full Compose previews and inspections if it has not been accepted in the IDE.

## Gradle linking

After the IDE had stopped, a new project-local .idea/gradle.xml linked the Gradle project. Local .gradle/config.properties selects Android Studio’s bundled JDK through #GRADLE_LOCAL_JAVA_HOME. Both files are ignored by version control. On reopening, the IDE log confirmed a TRIGGER_PROJECT_REOPEN Gradle sync using that JDK. No global Java or IDE JVM settings were changed.
