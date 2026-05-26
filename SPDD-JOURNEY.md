# SPDD Journey — Motorcycle Repair Shop

This file tracks the SPDD workflow step by step.
Each entry explains what was produced, why, and what decision it locked in.

---

## Step 1 — User Story
**Command:** `/spdd-story`  
**Artifact:** `requirements/user-story-1-repair-order-management.md`

**Why this step exists:**
Before writing any code or design, we need to agree on what the system
should do in business terms. The user story forces us to define scope
boundaries and verifiable acceptance criteria — so ambiguity is resolved
here, not during code review.

**What this step locked in:**
- Order lifecycle: `DRAFT → QUOTED → APPROVED → IN_PROGRESS → COMPLETED`
- The customer approves via a public link — no login required
- Payments, inventory, and email are explicitly out of scope for iteration 1

## Step 2 — Requirements Clarification
**Command:** none — pure human reasoning  
**Artifact:** `docs/step-2-clarification.md`

**Why this step exists:**
No AI involved. The developer reads the user story and resolves
ambiguities before delegating anything to the model. Gaps filled
here are cheap — gaps found during code review are expensive.

**What this step locked in:**
- 5-step mandatory inspection checklist before quote generation
- 3 roles: Mechanic, Shop Manager, Customer
- Shop Manager can self-approve
- Token validity: 48h, max 3 renewals, then CANCELLED
- Full order status machine including REJECTED and CANCELLED

## Step 4 — REASONS Canvas
**Command:** `/spdd-analysis` (REASONS canvas prompt)  
**Artifact:** `docs/step-4-reasons-canvas.md`

**Why this step exists:**
Transforms the domain model into a precise implementation blueprint across 7 dimensions.
The canvas is the single document a developer needs to start writing code: what to build,
every entity, every endpoint, and every constraint — no ambiguity left.

**What this step locked in:**
- Full Definition of Done (AC1–AC5 + non-functional)
- All JPA entities with enumerations and state-machine enum
- 18 operations (O-01 through O-18) with auth, steps, and HTTP responses
- Package structure under `com.workshop` (hexagonal architecture)
- Norms: naming, validation, logging, error-handling contract, test standards
- 9 non-negotiable safeguards (SG-01 through SG-09)

---

## Step 3 — Domain Analysis
**Command:** `/spdd-analysis`  
**Artifact:** `docs/step-3-domain-analysis.md`

**Why this step exists:**
The AI reads the structured documents from steps 1 and 2 and produces
a formal domain model — entities, business rules, risks, and open
questions. Because the input is clean and unambiguous, the output is
precise and reviewable. We then resolve the open questions as humans
before moving forward.

**What this step locked in:**
- 7 domain entities with fields and relationships
- 19 business rules (11 explicit + 8 implicit)
- Token generated at APPROVED state (not QUOTED)
- CANCELLED reachable from APPROVED only
- Token invalidated on line-item change (no renewal slot consumed)
- Inline customer/motorcycle creation allowed
- SM is the only actor who can COMPLETE an order
- Hexagonal architecture + enum state machine (no Spring State Machine)
- CAS atomic update for token single-use guarantee

## Step 4 — REASONS Canvas
**Command:** `/spdd-reasons-canvas`  
**Artifact:** `docs/step-4-reasons-canvas.md`

**Why this step exists:**
The canvas translates all previous documents into an executable blueprint.
Each REASONS dimension locks in a different type of decision: what to build,
what entities exist, which patterns to use, where code lives, how each
operation works step by step, what standards apply, and what can never
be violated. The AI generates this from clean input; the human reviews
and corrects it before any code is written.

**What this step locked in:**
- 18 operations with full pseudocode (O-01 to O-18)
- Hexagonal architecture: domain core + ports + adapters
- SHA-256 token hashing; raw token returned once, never stored
- CAS atomic SQL update for token single-use guarantee
- 9 non-negotiable safeguards (SG-01 to SG-09)
- Redundant guard removed from O-11 (isTerminal() is sufficient)
- State machine diagram corrected: CANCELLED reachable from APPROVED only