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

## Step 3 — Domain Analysis
**Command:** SPDD domain-analysis prompt  
**Artifact:** `docs/step-3-domain-analysis.md`

**Why this step exists:**
Before touching a single Java class, the domain must be fully modeled.
This step translates business language into precise technical entities,
rules, and risks — so the implementation phase has no guesswork.

**What this step locked in:**
- 7 entities: RepairOrder (aggregate root), Customer, Motorcycle, LineItem, InspectionChecklist, QuoteToken, User
- 11 explicit + 8 implicit business rules
- State machine with `OrderStatus.canTransitionTo()` pattern
- Token security: store SHA-256 hash only; CAS atomic update for single-use guarantee
- Optimistic locking (`@Version`) on RepairOrder for concurrent modification safety
- Partial unique index on `quote_tokens` for single-active-token guarantee
- 10 risks identified (R-01 through R-10)
- 8 open questions surfaced (OQ-01 through OQ-08) — must be resolved before implementation