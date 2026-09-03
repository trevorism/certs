Feature: Context Root of this API
  In order to use the Certs API, it must be available

  Scenario: ContextRoot https
    Given the certs application is alive
    When I navigate to the context root
    Then the API returns a link to the help page

  Scenario: Ping https
    Given the certs application is alive
    When I navigate to /api/ping
    Then pong is returned, to indicate the service is alive
