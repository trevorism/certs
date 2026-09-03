Feature: Rotation audit trail
  Every scenario in this file is read-only. It reads the audit trail that past rotations left
  behind and never invokes /api/rotation/sweep or /api/certificate/{id}/rotate, so it cannot start
  a rotation or spend an acme quota however many times it runs.

  Background:
    Given the certs application is alive

  Scenario: Rotations leave an audit trail
    When I list the rotation runs
    Then at least one rotation run is recorded
    And every run names a wildcard, a gcp project and an outcome

  Scenario: A rotation run can be read by id
    Given the first rotation run
    When I read that run by id
    Then the run returned is the one I asked for

  Scenario: A rotation run records its state changes in the order they happened
    Given the first rotation run
    When I read that run by id
    Then every recorded state carries a timestamp
    And the recorded states are in chronological order

  Scenario: A completed rotation records the certificate it issued
    When I list the rotation runs
    Then every completed run records the serial and expiry it issued

  Scenario: A failed rotation records why it failed
    When I list the rotation runs
    Then every failed run records a failure detail

  Scenario: The serial a certificate reports is the serial its last rotation issued
    When I list the managed certificates
    And I list the rotation runs
    Then every certificate that names a last rotation agrees with that run
