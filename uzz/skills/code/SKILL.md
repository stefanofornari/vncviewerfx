---
name: implement-user-story
description: Safely and systematically evaluates, plans, and implements a vertical user story using TDD while updating an active notes.md file.
allowed-tools: read_file write_file modify_file list_dir run_command
---

# Skill: Implement User Story

## Goal
Safely and systematically implement a user story from specification to tested code, adhering to project coding standards and architectural rules.

## Input Requirements
- A target User Story (with title, description, and BDD Acceptance Criteria).
- Project context files: `scope.md`, `coding-standard.md`, `development-framework.md`.

---

## Execution Pipeline

### Step 1: Story Size & Complexity Assessment
Before writing any code, evaluate the user story against the **INVEST** principles:
- **Sizing Check:** Is this story too large for a single implementation cycle (estimated > 2-3 days of work)?
- **Domain Check:** Does it modify multiple unrelated system domains simultaneously?
- **Action:** If **YES**, halt implementation. Propose a breakdown into 2+ smaller, independent vertical user stories and ask the user for confirmation before proceeding. If **NO**, proceed to Step 2.

### Step 2: Context & Rule Gathering
- Inspect the target codebase and existing domain specifications.
- Read `coding-standard.md` and `development-framework.md` to ensure design alignment.
- Identify all affected files, modules, and dependencies.

### Step 3: Implementation Strategy (Technical Plan)
Draft a concise execution plan covering:
1. **Target Files:** List files to be created, modified, or refactored.
2. **Data / State Changes:** Any schema updates, state updates, or API contract updates.
3. **Edge Case Handling:** Plan for potential failure points defined in acceptance criteria.

### Step 4: Test-Driven Implementation
1. **Write Unit/Integration Tests First:** Create failing tests based directly on the story's `Given / When / Then` acceptance criteria.
2. **Implement Feature Code:** Write the minimal code necessary to make the tests pass.
3. **Refactor:** Clean up the implementation to strictly match rules in `coding-standard.md` without breaking passing tests.

### Step 5: Acceptance & Scope Verification
- Run the full test suite to ensure no regressions occurred.
- Perform a line-by-line self-check against the user story's Acceptance Criteria.
- Confirm no "Out-of-Scope" features accidentally leaked into the implementation.
- Output a summary of modified files and test results for developer review.

## Additional Instructions
- Track comments, technical decisions, design choices, trade-offs, etc., that shall be persisted in a `notes.md` file alongside the user story file.
- Do not add to `notes.md` redundant information (e.g., information that is already in `coding-standard.md`, `development-framework.md`, or any other generic description).