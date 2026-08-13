---
name: user-story-generator
description: Analyzes a scope.md file to break down its functional domains into actionable, INVEST-compliant user stories. Generates structured output files featuring BDD/Gherkin acceptance criteria mapped to user personas.
allowed-tools: read_file write_file modify_file list_dir
---

# Skill: Generate User Stories from Scope

## Goal
Analyze a `scope.md` file and systematically break down its functional domains into actionable, **INVEST-compliant user stories** complete with **BDD (Given/When/Then) acceptance criteria**.

---

## 🛑 Execution Rules & Standards

1. **INVEST Principle**: Every user story must be Independent, Negotiable, Valuable, Estimable, Small (executable within 1–3 days of work), and Testable. Split complex features into thin vertical slices rather than horizontal architectural layers (e.g., do not create standalone "database" or "API framework" stories).
2. **Strict Scope Compliance**: Do **NOT** generate user stories for any items, systems, or features explicitly flagged under the "Out-of-Scope" or "Future Enhancements" sections of `scope.md`.
3. **Actor Matching**: Resolve the user roles in the `As a [User Role]` segment by reading and matching actors defined inside `users.md`. Do not invent unmapped user roles.
4. **Identifier Formatting**: Assign a unique identifier to each story using the precise pattern: `[DOMAIN]/US-[NUMBER]` where `[NUMBER]` is a zero-padded, **6-digit integer** (e.g., `AUTH/US-000001`).

---

## ⚙️ Execution Pipeline

### Step 1: Parse & Map Domains
* **Ingest Assets**: Read and parse `scope.md` and `users.md`.
* **Map Vectors**: Extract distinct functional domains, platform environments, constraints, and valid user roles.

### Step 2: Story Decomposition
* **Slice Vertically**: Evaluate every in-scope functional domain. Slice capabilities into the smallest independent blocks that provide direct business value.
* **Format Structure**: Frame statements tightly around the standard format:
  `AS A [role], I WANT [capability], SO THAT [value/benefit]`

### Step 3: Target File Generation & Persistence
For every single user story generated, create a dedicated directory structure and markdown file. The target file path must exactly follow:
`[DOMAIN]/[US-ID]/[US-ID].md` (e.g., `billing/US-000042/US-000042.md`).

---

## 📄 Output File Template (`[DOMAIN]/[US-ID]/[US-ID].md`)

```markdown
# User Stories for Domain: [Domain Name]

## [US-000001] [Story Title]
**As a** [User Role from users.md],
**I want** [Specific Action/Capability],
**So that** [Business Value / Reason].

### Technical Context & Constraints
* **Target System**: [e.g., Spring Boot backend, Web UI, Mobile app]
* **Toolchain / Rules**: [Refers to coding standards and frameworks imported from scope.md]

### Acceptance Criteria
```gherkin
Scenario: [Happy path scenario title]
  Given [Initial context or system state]
  When [Action taken by the actor]
  Then [Expected deterministic outcome]

Scenario: [Edge case or error handling title]
  Given [Initial context or system state]
  When [Invalid or boundary edge case action taken]
  Then [Expected error presentation, safety block, or state rollback]
```
```

---

## Additional Operational Instructions
* **Directory Creation**: Ensure parent directories for the domain and the `US-ID` are generated recursively before writing the file.
* **No Merged Files**: Do not lump multiple user stories together into a single file or a generic folder root. Every story demands its own folder and dedicated markdown document.
