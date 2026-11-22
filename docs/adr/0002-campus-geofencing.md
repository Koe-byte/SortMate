# ADR-0002 — Home and campus geofences with a pure resolver

- **Status:** Accepted
- **Date:** 2026-11-22
- **Deciders:** SortMate group

## Context

The proposal requires automatic presence updates when a student crosses a **campus** boundary, not only the home boundary. Android's `GeofencingEvent` reports the transition (`ENTER`/`EXIT`) and the triggering geofence IDs, but it does not report which other fences the device is currently inside. Two overlapping or nearby fences can therefore produce ambiguous status.

## Decision

Register two circular geofences per household:

- `sortmate-home` — home coordinates, default radius 100 m.
- `sortmate-campus` — campus coordinates, default radius 100 m.

Both use one `PendingIntent` and `ACCESS_BACKGROUND_LOCATION`. The receiver maps the triggering IDs to a `GeofenceArea`, then calls a pure `PresenceResolver` with the current status, the entered set, and the exited set.

Resolution rules:

1. Entering home → `HOME`.
2. Otherwise, entering campus → `CAMPUS`.
3. Exiting home while `HOME` → `AWAY` (or `CAMPUS` if campus was entered in the same event).
4. Exiting campus while `CAMPUS` → `AWAY` (or `HOME` if home was entered in the same event).
5. No transition → keep the current status.

The resolver is pure Kotlin and covered by `PresenceResolverTest`.

## Consequences

- The receiver and demo buttons share the presence write; only real events exercise Play Services and the resolver.
- An exit from the *other* area preserves the current badge. The badge cannot encode simultaneous occupancy after separate overlapping events; field-test with distinct centers.
- If an ENTER event is missed entirely, the status can be stale until the next transition; the manual presence switcher and the demo buttons are the fallback.
- Radius is stored per boundary (database range 50–1000 m). The UI uses the saved radius; it has no radius slider.
- Android 10+ requires a separate "Allow all the time" grant; the UI links to app settings and the boot receiver re-registers both fences.

## Alternatives considered

1. **One geofence that switches coordinates** — rejected: cannot represent home and campus at the same time.
2. **Poll `FusedLocationProvider` in the background** — rejected: battery cost and background-location restrictions.
3. **Server-side geofencing from periodic location uploads** — rejected: privacy, battery, and free-tier limits.
4. **Separate receivers per fence** — rejected: duplicate notification logic and no shared state resolution.
