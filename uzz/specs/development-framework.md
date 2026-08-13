# Development Framework

## Purpose
This document defines the development framework and workflow for the Quick Question project.

## Technology Stack
- Java
- JavaFX
- CommonsFX (`org.commonsfx`)
- AtlantaFX
- Maven
- JUnit 5
- TestFX
- AssertJ

## JavaFX
- use fxml descriptors whenever possible

## Testing Strategy
- Follow a TDD workflow.
  1. Write failing tests before implementation and check they fail
  2. Write the minimal production code to pass the test
  3. Run the test and refactor the code until it passes
  4. Check the scope is satisfied and if not reiterate from 1
- Use JUnit 5 for unit and integration tests.
- Use TestFX for JavaFX UI tests.
- Support headless execution for automated test runs.
- Use AssertJ BDD assertions with `then()`.
- Use WireMock to stub external HTTP endpoints; tests must not depend on real network services or live sites.

## Build and Execution
- Use Maven as the build tool.
- Keep the project build reproducible and automation-friendly.
- Ensure tests can run in CI without a graphical desktop session.
