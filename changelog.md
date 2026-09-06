## 0.5.0

Move authentication onto micronaut-ui-auth and @trevorism/ui-auth, so the app receives
its own login on its own host instead of reading cookies the login app set.

## 0.4.0

Modify the urgent constant to be 15 days, grace period for checking cert propagation to 25 hours.

## 0.3.7

Acceptance coverage for the certificate inventory, the rotation audit trail and the security posture.

## 0.3.6

Distinguish a failed edge check from a stale edge

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