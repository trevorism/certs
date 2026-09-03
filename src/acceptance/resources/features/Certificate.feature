Feature: Managed certificate inventory
  Every scenario in this file is read-only. Nothing here issues a certificate, writes a dns record
  or uploads material to app engine, so the suite can run any number of times without spending a
  let's encrypt issuance or a godaddy api call.

  Background:
    Given the certs application is alive

  Scenario: The service manages a certificate inventory
    When I list the managed certificates
    Then at least one certificate is managed
    And every certificate names a wildcard, a gcp project and an app engine certificate

  Scenario: A wildcard is derived from the category it covers
    When I list the managed certificates
    Then every wildcard is the wildcard for its category

  Scenario: A probe host is covered by the wildcard it is checked against
    When I list the managed certificates
    Then every probe host is a single label under its wildcard

  Scenario: The dns challenge record is derived from the category
    When I list the managed certificates
    Then every challenge fqdn is the acme challenge record for its category

  Scenario: A managed certificate can be read by id
    Given the first managed certificate
    When I read that certificate by id
    Then the certificate returned is the one I asked for

  Scenario: Verifying a certificate reports on the edge without changing anything
    Given the first managed certificate
    When I verify that certificate
    Then the verification is about that certificate
    And the verification explains what the edge is serving
