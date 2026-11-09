"""Retired Sentinel 0.9 demo capture entry point.

Production no longer has demo mode and uses FLAG_SECURE. The old capture workflow
is not compatible with the widget workspace. Do not run device commands, change
the viewport or bypass screenshot protection from this retired tool.
"""


def main():
    raise SystemExit(
        'Retired: Sentinel no longer has the 0.9 demo UI. Historical captures are '
        'not current store screenshots. Plan new captures from a non-personal '
        'test setup; see release/store/ASSETS.txt and docs/RELEASE-READINESS.md.'
    )


if __name__ == '__main__':
    main()
