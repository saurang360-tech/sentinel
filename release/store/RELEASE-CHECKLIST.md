# Public release checklist — 9 November 2026

**Not ready for public submission.** Current behavior, reusable commands and open
issues are maintained in [docs/RELEASE-READINESS.md](../../docs/RELEASE-READINESS.md).
The [dated audit](../../docs/PLAY-READINESS-AUDIT-2026-11-09.md) records evidence;
it is not Play approval. No repository script uploads an app or submits forms.

Production ID: `dev.titus.sentinel`; paired preview: `dev.titus.sentinel.preview`.
Production generates separate keys and needs its own reviewed authorisation.
Monitoring needs the bundled Linux helper, Python 3, SSH and a reachable LAN/VPN
route. Power actions add no PC software/permission changes and require existing
sudo rights. Secure phone lock protects settings/terminal; fresh PC sudo password
protects each held/armed shutdown or reboot. There is no production demo mode.

Before submitting:

- [ ] Fill publisher.json with actual publisher, public support contact and hosted
  privacy URL. Define support-message handling; put contact in bundled policy.
  Run `python tools/prepare_store_documents.py`, host the final HTML and rebuild.
- [ ] Confirm application ID and unused versionCode. Securely back up the ignored
  upload keystore and signing properties and enroll in Play App Signing. A fresh
  checkout needs its own local SDK/signing configuration. The release build has
  no debug-signing fallback. Never commit signing secrets or reuse phone SSH keys.
- [ ] Build and run unit tests/release lint through Android Studio/MCP; run
  `python tools/check_store_readiness.py`. Review all blockers and manual checks.
  Verify real APK/AAB signatures and 16 KB compatibility separately.
- [ ] Review new dependencies, licences, permissions and exported components.
  The current seven Android permissions include opt-in notifications and a
  connectedDevice foreground service. CHANGE_NETWORK_STATE satisfies its Android
  prerequisite; Sentinel does not alter networking. Play eligibility is separate.
- [ ] Provide final connected-device FGS use case, defer/interruption impact and
  video. Complete Data safety, ads, audience, content rating and eligibility forms.
- [ ] Supply current accurate workspace screenshots and practical English reviewer
  access/setup instructions for isolated SSH resources. Never expose the owner's PC.
- [ ] Fix/assess open runtime UX findings in the audit and partial phone report,
  including secure-screen-lock setup guidance. Verify clean install/upgrade,
  permission grant/deny/revoke, lost/changed SSH identity, network loss, large text,
  unrelated PCs and locked/idle/overnight monitoring. No untested case is a pass.
- [ ] Complete any account-specific testing/production-access requirements in the
  actual Console. Confirm policy requirements again at submission.

Keep previous preview packages as local rollback artifacts. Do not uninstall or
clear the paired owner preview as a release check. Rebuild after any source/legal
changes; old named packages under release/ are not automatically refreshed.
