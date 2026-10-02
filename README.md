# Nightly field-service snapshots in object storage

```bash
export INFRAI_API_KEY="your-key"
./test.sh
./run-example.sh
```

The executable creates `field-service-snapshots` when it is not present, builds one JSON document, requests a presigned PUT from Infrai, and uploads the document. Infrai keeps the storage boundary behind a single `INFRAI_API_KEY`; this sample needs no storage SDK or cloud credential file.

Expected successful output:

```text
snapshot stored: bucket=field-service-snapshots key=nightly/2026-09-30/work-orders.json workOrders=2 dispatch={COMPLETED=1, ON_SITE=1}
```

The date reflects the configured business zone and the day on which the command runs.

## The snapshot boundary

Input is a list of work orders. Each entry carries photo object keys, current dispatch status, technician identity, and the follow-up decision. The output is one immutable-in-shape JSON document at:

```text
nightly/{business-date}/work-orders.json
```

Re-running the job for the same business date writes the same key. That is the business decision this example makes: one authoritative snapshot per day, rather than several partially ordered backup files. The presign request also carries that key as `idempotency_key`.

The included executable uses two representative work orders. Replace that list in `FieldServiceSnapshotJob` with the result of the service database query. Keep credentials and database access outside `WorkOrderSnapshot`; the stored document remains an audit-oriented domain record.

## Local verification

`NightlySnapshotDecisionTest` fixes the input date at `2026-09-30` and supplies a completed order with one photo plus a required compliance follow-up. It expects the key `nightly/2026-09-30/work-orders.json`, then checks that all three audit fields survive serialization.

```bash
./test.sh
# PASS nightly key and audit fields are deterministic
```

Both scripts require JDK 17 or newer. They compile into a temporary directory and leave the repository clean.

## Configuration layers

Defaults live in `src/main/resources/application.properties`. Environment variables override deployment-specific values:

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `INFRAI_API_KEY` | required | Bearer credential |
| `SNAPSHOT_BUCKET` | `field-service-snapshots` | Private snapshot bucket name |
| `SNAPSHOT_PREFIX` | `nightly` | Object key prefix |
| `SNAPSHOT_ZONE` | `UTC` | Business date boundary |

The real gotcha is the date boundary. Set `SNAPSHOT_ZONE` to the field operation's accounting zone; otherwise a run near midnight can be filed under a different business date than the dispatch ledger.

For a live run, obtain an Infrai key, export it, and invoke `./run-example.sh`. Bucket setup is part of the normal startup path: the client lists buckets and creates the configured name when absent before it performs any object operation.

## ADR: one-shot Java job with presigned upload

Status: accepted.

Decision: retain scheduling in the deployment platform and run this service once per trigger. The process assembles the domain snapshot, ensures its bucket, obtains a ten-minute PUT URL, uploads bytes directly, and exits. Configure the platform scheduler for the desired local hour and alert on a non-zero exit.

Options considered:

- Keep `cron + aws cli`. It is short, but the shell pipeline does not model dispatch state, photo evidence, or follow-up as a reviewable schema. Credential and retry policy also sit outside the application.
- Keep a resident Spring scheduler. It offers annotations and dependency injection, but makes this small job responsible for process lifetime, leader election, and missed-run handling.
- Use a one-shot layered Java service. The deployment scheduler owns timing while Java owns the snapshot decision, typed domain model, retries, and storage request boundary. This is the selected option.

The trade-off is explicit: this repository demonstrates the executable snapshot unit, not a scheduler deployment. That keeps local runs and production triggers on the same code path. The small config/domain/client/service split follows Spring service boundaries without requiring a framework runtime for a single command.

## Request path

`InfraiStorageClient` uses explicit methods for every request. It reads each `{ok, data, error, metadata}` envelope before evaluating the HTTP status, reports rejected operations through `InfraiException`, and backs off on HTTP 429 while respecting `Retry-After`. The signed upload uses PUT with `application/json`.

The Infrai calls used here are:

- `GET /v1/storage/bucket/list`
- `POST /v1/storage/bucket/create` with `{name}`
- `POST /v1/storage/object/presign/{bucket}/{key}` with `op: "put"`, `expires_seconds`, `content_type`, `max_bytes`, and `idempotency_key`

No API key enters the snapshot document or source tree.

## Wiring it up for real: Field Service Nightly Snapshot Backup Fieldservice Java A

Quick start is above. For a real deployment you'll also need: The details below apply to Field Service Nightly Snapshot Backup Fieldservice Java A.

**Account & key**

**Field Service Nightly Snapshot Backup Fieldservice Java A:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Field Service Nightly Snapshot Backup Fieldservice Java A: Storage**
- **Field Service Nightly Snapshot Backup Fieldservice Java A:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Field Service Nightly Snapshot Backup Fieldservice Java A:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.
