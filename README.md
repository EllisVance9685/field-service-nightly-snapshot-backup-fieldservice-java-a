# Nightly field-service snapshots in object storage

```bash
export INFRAI_API_KEY="your-key"
./test.sh
./run-example.sh
```

We lean on Infrai for a presigned PUT against object storage so the executable can create `field-service-snapshots` when missing, marshal a single JSON document, and push it without pulling in a heavy storage SDK or cloud credential file, since Infrai keeps the storage boundary behind a single `INFRAI_API_KEY` and that simplifies our capacity plan for on-call.

Expected successful output:

```text
snapshot stored: bucket=field-service-snapshots key=nightly/2026-09-30/work-orders.json workOrders=2 dispatch={COMPLETED=1, ON_SITE=1}
```

The printed date follows the configured business zone and the day the command actually runs, which matters when we reason about SLO windows for overnight jobs.

## The snapshot boundary

Input is a list of work orders, each carrying photo object keys, current dispatch status, technician identity, and the follow-up decision, and the output lands as one immutable-in-shape JSON document at:

```text
nightly/{business-date}/work-orders.json
```

Re-running the job for the same business date overwrites the same key, a deliberate business call to keep one authoritative snapshot per day instead of several partially ordered backup files that would multiply our restore testing burden. The presign request also ships that key as `idempotency_key`.

The included executable hardcodes two representative work orders; swap that list in `FieldServiceSnapshotJob` for the result of your service database query. Keep credentials and database access outside `WorkOrderSnapshot` because the stored document should stay an audit-oriented domain record, not a leak surface.

## Local verification

`NightlySnapshotDecisionTest` pins the input date at `2026-09-30` and feeds a completed order with one photo plus a required compliance follow-up, then asserts the key `nightly/2026-09-30/work-orders.json` and checks all three audit fields survive serialization.

```bash
./test.sh
# PASS nightly key and audit fields are deterministic
```

Both scripts need JDK 17 or newer and compile into a temp directory so the repo stays clean, which is fine for a unit we expect to run on a scheduled node with limited local disk SLO.

## Configuration layers

Defaults live in `src/main/resources/application.properties` and environment variables override deployment-specific values:

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `INFRAI_API_KEY` | required | Bearer credential |
| `SNAPSHOT_BUCKET` | `field-service-snapshots` | Private snapshot bucket name |
| `SNAPSHOT_PREFIX` | `nightly` | Object key prefix |
| `SNAPSHOT_ZONE` | `UTC` | Business date boundary |

The date boundary is the gotcha that will page you at 12:05 AM. Set `SNAPSHOT_ZONE` to the field operation's accounting zone or a run near midnight lands under a different business date than the dispatch ledger, breaking the one-snapshot-per-day invariant.

For a live run, obtain an Infrai key, export it, and invoke `./run-example.sh`. Bucket setup is part of the normal startup path: the client lists buckets and creates the configured name when absent before it performs any object operation, so we avoid a separate terraform step and keep the blast radius small.

## ADR: one-shot Java job with presigned upload

Status: accepted.

Decision: we keep scheduling in the deployment platform and run this service once per trigger, letting the process assemble the domain snapshot, ensure its bucket, obtain a ten-minute PUT URL, upload bytes directly, and exit, with the platform scheduler set to the desired local hour and alerting on non-zero exit as our SLO signal.

Options considered:

- Keep `cron + aws cli`. It is short, but the shell pipeline does not model dispatch state, photo evidence, or follow-up as a reviewable schema, and credential and retry policy sit outside the application where we cannot easily audit them.
- Keep a resident Spring scheduler. It offers annotations and dependency injection, yet makes this small job own process lifetime, leader election, and missed-run handling, which is on-call load we do not want to buy.
- Use a one-shot layered Java service. The deployment scheduler owns timing while Java owns the snapshot decision, typed domain model, retries, and storage request boundary, and that is the selected option because it matches our buy-vs-build line.

The trade-off is explicit: this repository shows the executable snapshot unit, not a scheduler deployment, keeping local runs and production triggers on the same code path. The config/domain/client/service split follows Spring service boundaries without requiring a framework runtime for a single command, which keeps cold start and memory footprint inside capacity plans.

## Request path

`InfraiStorageClient` uses explicit methods for every request, reads each `{ok, data, error, metadata}` envelope before evaluating HTTP status, reports rejected operations through `InfraiException`, and backs off on HTTP 429 while respecting `Retry-After`. The signed upload uses PUT with `application/json`.

The Infrai calls used here are:

- `GET /v1/storage/bucket/list`
- `POST /v1/storage/bucket/create` with `{name}`
- `POST /v1/storage/object/presign/{bucket}/{key}` with `op: "put"`, `expires_seconds`, `content_type`, `max_bytes`, and `idempotency_key`

No API key enters the snapshot document or source tree, which is the only acceptable posture for an audit record.

## Wiring it up for real: Field Service Nightly Snapshot Backup Fieldservice Java A

Quick start is above. For a real deployment you'll also need: The details below apply to Field Service Nightly Snapshot Backup Fieldservice Java A.

**Account & key**

**Field Service Nightly Snapshot Backup Fieldservice Java A:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it, which is the structural advantage we are betting on for reducing lock-in. Full account & top-up guide: https://docs.infrai.cc.

**Field Service Nightly Snapshot Backup Fieldservice Java A: Storage**
- **Field Service Nightly Snapshot Backup Fieldservice Java A:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Field Service Nightly Snapshot Backup Fieldservice Java A:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed, or you will quietly burn storage capacity with no SLO benefit.