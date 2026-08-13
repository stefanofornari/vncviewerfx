---
name: scope-definition-reviewer
description: Evaluates, refines, and structures the scope file for a new or modified application, feature, or user story. It guides the user through an iterative clarifying phase to establish solid boundaries before outputting a standardized system profile.
allowed-tools: read_file write_file modify_file list_dir
---

# Skill: Scope Definition Reviewer

## Goal
Systematically evaluate and structure a scope file for a new or modified application, feature, or user story. This skill establishes absolute implementation boundaries, clears up ambiguities, and builds a standardized system profile file.

---

## Execution Pipeline

### Step 1: Interrogative Clarification Phase
Before writing the final scope, analyze the initial input for gaps or ambiguities.
* **Iterative Discovery**: Ask targeted clarifying questions to collect missing details.
* **Pacing**: Present questions one by one, or in small, logically grouped clusters (maximum 3 questions at a time) to avoid overwhelming the user.
* **Target Information**: Focus questions on unresolved requirements, platform assumptions, or ambiguous architectural constraints.

### Step 2: Boundary Definition & Extraction
Partition all discussed items into two mutually exclusive, clear groups:
* **In-Scope Capabilities**: Features, platforms, and behaviors that *must* be delivered in the current implementation cycle.
* **Out-of-Scope / Future Enhancements**: Features explicitly deferred, nice-to-haves, or ideas that will cause scope creep if implemented now.

### Step 3: Scope Compilation & Persistence
Once clarifications are complete, generate a structured profile. Update or create the target scope file using the exact template defined below.

---

## Output Target Template

```markdown
# Project Name: [Insert Project Name]
**Version:** [e.g., 1.0.0, 0.1.0-alpha]

## 📝 Overall Description
[Provide a high-level breakdown of the system or feature organized by its main functional domains.]

## 🎯 In-Scope Capabilities
* **[Domain/Component Name]**:
  - [ ] [Capability description item 1]
  - [ ] [Capability description item 2]

## 🚫 Out-of-Scope / Future Enhancements
* [Definite boundary item 1]
* [Explicitly excluded feature 2]

## 🛠️ Technical Context & Infrastructure
* **Development Toolchain**: [e.g., Maven + JavaFX, Go Multiplatform, Node.js + TS, Cargo + Rust]
* **Target Systems/Platforms**: [e.g., Web, Spring Boot backend, Linux Desktop, iOS, Windows]
* **Project Reference Docs**:
  - Coding Standard: `[Path or Link to coding-standard.md]`
  - Development Framework: `[Path or Link to development-framework.md]`
```

---

## Additional Operational Instructions
* **No Speculation**: If any technical stack, platform, or project reference path is unknown, explicitly ask the user during the *Interrogative Phase* instead of assuming a default value.
* **Scope Guarding**: Treat anything not explicitly listed under *In-Scope Capabilities* as *Out-of-Scope* by default to prevent accidental feature creep.
