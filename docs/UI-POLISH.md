# Interface polish — Sentinel 0.6

The main screens now use a shared connection/refresh row, short local update times, sentence-case headings, consistent card spacing and accessible selected navigation tabs. Overview keeps the large circular power control above a focused workstation summary, as requested. The control is visibly disabled and labelled “Shutdown not configured.” Shutdown remains unimplemented and no power command is exposed.

Health retains real metrics, puts collected warnings first, uses readable B/s, KiB/s and MiB/s rates, and moves measurement explanations into an expandable section. Details keeps system/network/disk summaries visible and makes long sensor and failed-service lists expandable. Processes retains filtering/search/pagination while moving collection explanations into an expandable section. Settings keeps the everyday connection form visible and places host-key/pairing information in a Security & pairing section. Removed obsolete demo-data copy and repeated implementation explanations.

No monitoring protocol, SSH authorisation, key, backend helper, service or networking changes are included in this update. The existing Roman adaptive icon and monochrome styling are preserved. Builds use Android Studio MCP and its bundled JDK.

Verification: nine JVM tests and four physical Pixel live-connection tests passed. The latter cover health, process-owner filters, original status/session reuse and host-pin rejection. UI hierarchy and screenshots were reviewed on the unlocked Pixel with its existing text size. Final Android Studio build and lint passed after the Overview simplification; nine JVM tests passed. Four physical Pixel live-connection tests passed before that final presentation-only adjustment.

This remains the existing debug-signed personal installation. UI polish does not establish store-release readiness; public distribution would need a release-signing/distribution pass and broader device/network testing.

The owner requested restoring the large power button after the first visual cleanup. Version 0.6.1 restores its circular outline, glow and power glyph without enabling any shutdown operation.
