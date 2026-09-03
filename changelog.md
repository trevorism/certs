## 0.3.7

Acceptance coverage for the certificate inventory, the rotation audit trail and the security posture.
The glue moved to the package root the acceptance plugin actually scans, so the suite runs instead of
silently reporting success with zero scenarios. Every scenario is read-only: nothing issues a
certificate, writes dns or uploads material, so the suite spends no acme or godaddy quota however
often it runs.

## 0.3.6

Distinguish a failed edge check from a stale edge, so a probe that cannot connect no longer alerts as
drift or renders as "never rotated". Record the issued serial when a rotation fails at or after upload,
retry a rotation whose acme authorization is still valid, validate certificate registrations, and reject
an unrecognized acmeServer instead of registering a second acme account against it.

## 0.3.5

Fix auth bug.

## 0.3.4

Certificate status page with expiry, edge state, and per-cert rotation.

## 0.3.3

Alert directly on rotation failure, since the daily error digest only reports a count.

## 0.3.2

Sweep endpoint that rotates one due cert at a time.

## 0.3.1

Verify dns on write to stop a round trip.

## 0.3.0

Fix a bug after bumping the dependencies to the latest versions.

## 0.2.0

Endpoints and UI for managing certs.

## 0.1.0

Initial release