# Home and campus presence

Save the **home** boundary while at home and the **campus** boundary on campus. Allow precise location and, on Android 10+, background location. Coordinates are shared household settings; registration happens on each phone. A second phone can tap **Refresh** on each saved card. **Remove** unregisters that fence locally; the saved center remains available for refresh or boot restoration.

`PresenceResolver` receives the current badge and the areas named by Play Services. A campus entry sets *On campus*. A delayed home exit then leaves *On campus* intact. If both areas enter in one event, home wins. One badge does not remember two simultaneously occupied areas: when walking out of an overlap it cannot infer a boundary that did not report an event. Use distinct centers for a field trial.

**Household → Demo the pipeline → Home / Campus / Away** exercises the same presence write and notification as the receiver. Those buttons do not demonstrate GPS latency. [[EVALUATION]] times a physical crossing to the first badge draw. See [[0002-campus-geofencing|ADR-0002]] for the state rules.
