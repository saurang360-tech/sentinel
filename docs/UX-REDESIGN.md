# Sentinel visual and UX redesign

The approved nine-point redesign keeps the native Compose architecture and SSH
security boundaries. No monitoring helper, SSH key policy, runtime permission,
networking configuration or shutdown protocol was changed.

## Changes

- Revised to a black-and-white utility design after owner feedback: 40sp bold
  page headings, 52sp primary metric values, 18sp body text and 16sp secondary
  text. Square bordered sections, a white system-summary panel, and inverted
  selected navigation replace colored accents and rounded cards. Page taglines
  were removed. Buttons have explicit 4dp corners; the circular power control
  is retained.
- Native horizontal paging with labelled icon navigation, a dismissible first-use
  swipe hint, and independent page scroll positions. Settings remains separate;
  Android Back closes Settings. Navigating away revokes terminal access.
- Overview puts fresh health, uptime and CPU/RAM readings above a smaller circular
  shutdown control. It now requests the same health sample as Health/Details
  while visible; it does not run simultaneous monitoring loops.
- Health cards foreground numeric readings with labelled progress bars. Missing
  sensors remain unavailable. Metric names and values use separate lines; enlarged process text moves CPU/RAM below the process name.
- Connection feedback includes relative sample age, loading indicators and
  contextual routes to settings/pairing/fingerprint review. Old readings remain
  labelled as last known; silence is never treated as proof of power-off.
- Connection setup has details, pairing and save/verification stages. Validation
  appears beside each field. Advancing stages brings their start into view.
  Tests are disabled until the draft matches a valid saved connection.
- Process controls and headings stay fixed while a lazy list scrolls. Sorting,
  account selection, search and process details are preserved. Rows show their
  expansion state, and large RAM values use GiB to reduce wrapping.
- Terminal output flexes around input and shortcut controls. Masked input is
  explicit, output following can be paused, and help is in a dialog. The main
  terminal layout compacts its header and hides bottom navigation with the IME
  open. Session lifecycle and non-persistence remain unchanged.
- Material controls supply accessible touch targets; custom rows expose expanded
  state and headings. Text accompanies status colors. Synthetic UI checks include
  1.6x text, keyboard layout, setup validation and process scrolling.

## Verification approach

`DemoGalleryActivity` exists only in debug sources and is not exported. It uses
synthetic readings and inert terminal callbacks, never constructing monitoring
ViewModels, SSH keys or connections. Its gallery covers real composables for
navigation, setup, processes and terminal. The production manifest retains
screenshot protection and its existing permissions.

UI verification is run in a temporary copy with application ID
`dev.titus.sentinel.uxreview`, separate from both paired preview and production.
Do not run the full live-SSH instrumentation suite as part of visual validation.
Run only `dev.titus.sentinel.monitor.RedesignNavigationTest` for these checks.

Build checks: debug APK, debug test APK, release APK, JVM tests and debug lint.
Validation on the connected Pixel 9a in the isolated review package passed all
four `RedesignNavigationTest` tests. They cover swiping and tab navigation,
process expansion, fixed process controls during scrolling, setup validation and
save/test gating, keyboard resizing, and 1.6x text. Screenshots were inspected;
this caught and corrected a setup scroll-position issue and Android keyboard
panning before the final passing run. These checks do not exercise live SSH,
biometric authentication, or real shutdown.

The existing 18 JVM tests pass. Debug and release APK builds and debug lint pass;
lint retains existing dependency/resource/style warnings without errors. The
release merged manifest excludes the debug gallery and retains the original
three source-declared permissions plus AndroidX’s existing generated internal
receiver permission. The paired app was not replaced or cleared.

Reviewed synthetic screenshots are in `design/redesign/`, including Overview,
Health, Details, processes sorted by RAM, setup, verification, terminal with the
keyboard open, and enlarged text.

## Design references

- [Compose pager](https://developer.android.com/develop/ui/compose/layouts/pager)
- [Android accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Visibility of system status](https://www.nngroup.com/articles/visibility-system-status/)
- [Progressive disclosure](https://www.nngroup.com/articles/progressive-disclosure/)

## Monochrome revision — 7 November 2026

The owner requested larger text and a more modern, utilitarian black-and-white
interface. The revision removes the mint/slate palette and promotional page
subtitles, uses white-filled and outlined sections for hierarchy, strengthens
font weight and scale, and replaces pill navigation with square inverse tabs.
At enlarged font sizes, the navigation row scrolls horizontally and brings the
selected tab into view, keeping tab names on one line. The SSH, authentication
and shutdown behavior is unchanged.
