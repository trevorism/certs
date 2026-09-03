Feature: Security
  The certificate and rotation endpoints must reject unauthenticated callers.

  /api/rotation/sweep is deliberately not covered here: it rotates whichever live certificate is
  most urgent, so probing it out of band would spend a let's encrypt issuance and replace material
  on app engine every time the suite ran. That it declares @Secure is asserted by
  SecureAnnotationTest in the unit suite instead.

  The two write endpoints below are probed with input the controller rejects before it reaches acme,
  dns or the datastore: an unknown certificate id, and a registration with no fields. A regression
  in the security layer therefore cannot cost a quota or change any state here either.

  Background:
    Given the certs application is alive

  Scenario: Ping is publicly available
    When I GET "api/ping" anonymously
    Then the response body is "pong"

  Scenario: The context root is publicly available
    When I GET "api" anonymously
    Then the request is allowed

  Scenario: Listing managed certificates requires authentication
    When I GET "api/certificate" anonymously
    Then the request is rejected

  Scenario: Reading a managed certificate requires authentication
    When I GET "api/certificate/0" anonymously
    Then the request is rejected

  Scenario: Verifying a managed certificate requires authentication
    When I GET "api/certificate/0/verify" anonymously
    Then the request is rejected

  Scenario: Listing rotation runs requires authentication
    When I GET "api/rotation" anonymously
    Then the request is rejected

  Scenario: Reading a rotation run requires authentication
    When I GET "api/rotation/0" anonymously
    Then the request is rejected

  Scenario: Registering a certificate requires authentication
    When I anonymously POST a registration with no fields
    Then the request is rejected

  Scenario: Rotating a certificate requires authentication
    When I anonymously POST a rotation of an unknown certificate
    Then the request is rejected
