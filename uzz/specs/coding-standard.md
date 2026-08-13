# Coding Standard

## Purpose
This document defines the coding conventions for the Quick Question project.

## General Principles
- Prefer clear, readable, and maintainable code.
- Keep components small and focused.
- Favor composition over duplication.
- Write code that is easy to test.
- Keep UI logic separated from configuration and navigation logic where practical.

## Java Conventions
- Follow standard Java naming conventions for classes, methods, fields, and packages.
- Use descriptive names for public APIs.
- Keep methods short and single-purpose.
- Prefer immutable data where practical.
- Use explicit types when it improves readability.
- Use final variables by default
- Write class and method javadocs
- Use modern fieldName() and fieldName(value) instead of getFieldName() and setFieldName()
  for getters and setters of fields

## JavaFX Conventions
- Keep UI construction separate from business logic when possible.
- Prefer reusable components for embeddable UI parts.
- Avoid hard-coding provider-specific behavior into the core component.
- Ensure UI state changes are predictable and testable.

## Testing Conventions
- Use JUnit 5 for tests.
- Use AssertJ with BDD style assertions via `then()` rather than `assertThat()`.
- Use TestFX for JavaFX UI testing.
- Support headless test execution.
- Test names must use snake_case format.
- Prefer behavior-focused test names.
- Use snake_case naming only for test method names.

## Documentation Conventions
- Keep scope and framework documents aligned with implementation decisions.
- Document public component behavior and configuration expectations.
