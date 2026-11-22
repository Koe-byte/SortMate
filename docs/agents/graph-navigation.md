# Graph-first navigation

Run from the repo root. The graph is generated locally; the Markdown vault remains usable on a fresh checkout. Code-only extraction uses no model API. On Windows set `$env:PYTHONUTF8='1'` if the console cannot print Unicode.

```powershell
graphify extract . --code-only --max-workers 2
graphify cluster-only . --no-label
graphify export obsidian --graph graphify-out/graph.json --dir docs/graph
graphify benchmark graphify-out/graph.json
```

Open `docs/` as the vault; `graph/graph.canvas` is its generated graph. `graphify-out/graph.html` is the standalone browser view. Query before opening whole files; if a keyword is ambiguous, seed with the class or file path. These three checks located the following entry points:

```powershell
graphify query "Which repository sends a bill from the outbox?" --seed BillRepository --budget 420
graphify query "Where do geofence transitions become presence?" --seed PresenceResolver --budget 420
graphify query "Who receives remote notifications?" --seed SortMateMessagingService --budget 420
```

- Bill writes: `BillRepository` references `BillDao` and `RemoteDataSource`. `graphify path BillRepository SupabaseDataSource --undirected` shows the adapter two edges away via that interface.
- Boundary events: the receiver and `GeofenceArea` lead to `PresenceResolver.resolve` and `PresenceStatus`. Inspect the resolver for priority rules.
- Android push: `SortMateMessagingService` inherits `FirebaseMessagingService` and implements `onMessageReceived` and `onNewToken`. A graph edge is not evidence of device delivery.

**Benchmark (22 Nov 2026, graphifyy 0.9.92):** the initial code-only graph had 892 nodes, 2,160 edges and 39 communities. Graphify estimated **59,466 tokens** for a naive full-corpus read versus **1,166 per benchmark query** (51.0×). This is its built-in estimate, not measured conversation billing. Small complete edge lists may exceed the requested budget. This installation's SQL grammar was absent; consult the migrations directly for grants/triggers. Docs, PDFs and images were deliberately excluded from semantic extraction.
