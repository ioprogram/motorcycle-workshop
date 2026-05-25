# Step 2 — Requirements Clarification

**Date:** 2026-05-25  
**Based on:** requirements/user-story-1-repair-order-management.md

---

## Core Logic

### Quote generation
The mechanic adds labor and parts line items to the order.
Before generating the quote, the mechanic must complete
a mandatory 5-step inspection checklist:

1. Visual inspection of the motorcycle
2. Engine and fluid check
3. Brake and suspension check
4. Electrical system check
5. Mileage and wear assessment

Only after all 5 checks are marked complete can the quote
be generated. At that point the order transitions to `QUOTED`.

### Approval token
- Validity: 48 hours from generation
- Maximum renewals: 3 (each renewal generates a new 48h token)
- After the 3rd expiration without approval: order moves to `CANCELLED`
- Token is single-use: after approval or rejection it becomes invalid

---

## Roles

| Role | Permissions |
|------|-------------|
| **Mechanic** | Create order, add line items, complete checklist, generate quote |
| **Shop Manager** | Everything the mechanic can do + modify line items on `QUOTED` orders + approve quote + self-approve own quotes |
| **Customer** | View quote via public link, approve or reject (no login required) |

### Self-approval rule
A Shop Manager who created the order can approve it without
a second reviewer. The system does not enforce four-eyes principle
for this role.

---

## Order Status Machine

```
DRAFT ──→ QUOTED ──→ APPROVED ──→ IN_PROGRESS ──→ COMPLETED
                  ↘ REJECTED
QUOTED ──→ CANCELLED (after 3rd token expiration)
```

Transitions are **unidirectional** — no rollback allowed.

---

## Scope Boundaries

**The system does:**
- Enforce the 5-step checklist before quote generation
- Track token renewals and auto-cancel after the 3rd expiration
- Allow Shop Manager to modify line items before approval

**The system does NOT:**
- Send emails or push notifications (phase 2)
- Manage spare parts inventory
- Handle payments or invoicing
- Enforce four-eyes principle for Shop Manager

---

## Definition of Done

| AC | Verification |
|----|-------------|
| AC1 | POST /api/orders returns 201 with status `DRAFT` |
| AC2 | All 5 checks marked → POST generate-quote → status `QUOTED`, token created with 48h expiry |
| AC3 | Shop Manager approves → status `APPROVED`, token generated for customer link |
| AC4 | Customer clicks approve → status `IN_PROGRESS`, token invalidated |
| AC5 | Token expires 3 times → status `CANCELLED` |