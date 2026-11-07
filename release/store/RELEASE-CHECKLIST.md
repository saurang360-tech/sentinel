# Public release gate

Implemented for 0.9: generic runtime settings, memory-only samples, first-use
disclosure/terms acknowledgement, Android 17 local-network permission with
denial handling, offline legal text, local erasure, generic helper export/setup,
synthetic offline demo and separate production/debug application IDs.

Production ID: dev.titus.sentinel. Existing phone preview keeps
dev.titus.sentinel.preview, its saved connection and its existing SSH keys.
Production is a separate installation and must generate/pair its own keys.
No existing PC keys, services or networking are changed by this Android update.

Before submitting:

1. Fill publisher.json with the actual publisher, dedicated public support email
   and privacy-policy URL. Add the real contact to the bundled privacy policy and
   terms. Define handling of voluntary support email. Run
   `python tools/prepare_store_documents.py`, host the resulting final static HTML
   at a public stable URL, verify accessibility, and rebuild. Current HTML/text
   are pre-release drafts while contact is unassigned.
2. Choose/confirm this application ID before its first Play upload. A dedicated
   local upload key was generated for this project at
   release/private/sentinel-upload.jks with a new random password, using Studio's
   bundled keytool. No existing private keys were read or reused. Back up
   release/private and release-signing.properties securely before submission;
   both are excluded from version control and owner-readable only. A fresh
   checkout can instead use Studio's signed bundle wizard and the example
   configuration. Without
   that file the release bundle is unsigned and cannot be uploaded as a final
   signed submission. Never substitute the Android debug key. Enrol in Play App
   Signing as required for the new app.
3. Inspect the final release bundle and merged manifest, validate signing, and
   verify there are no unexpected permissions or SDKs. Recheck dependency licences
   when dependencies change. This release uses no new runtime library.
4. Run clean-install and upgrade checks, local-network permission grant/deny/
   revoke, missing/changed SSH identity, PC/network loss and supported hardware
   checks on independent devices/PCs. Verify actual phone erasure only in a
   disposable installation, never erase the owner's paired preview for testing.
5. Complete the Data safety worksheet, listing metadata/screenshots, content
   rating, ads/audience declarations, reviewer instructions and any applicable
   closed testing in the actual Play Console account. Check policy requirements
   again at submission; account eligibility/testing cannot be established from
   this repository. No Play submission has been performed.

Prepare a bundle through Android Studio/MCP using its bundled JDK:
`:app:bundleRelease`. The debug APK is for the existing phone, not Play upload.
The release build is non-debuggable and has no debug-signing fallback.

Keep the previous preview APK as a rollback artifact. Do not uninstall the paired
preview or clear its storage as part of upgrade. Source rollback does not restore
the deliberately removed old snapshot cache; live readings repopulate in memory.
