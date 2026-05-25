# [User Story 1] Repair Order Management with Online Quote Approval

**Generated with:** `/spdd-story`  
**Date:** 2026-05-25  
**Iteration:** 1

---

## Background

A motorcycle repair shop receives bikes for repair every day.
The current process is chaotic: quotes written on paper, approvals
via WhatsApp, no traceability. We need a web system that digitalizes
the repair lifecycle from order creation to customer quote approval.

---

## Business Value

1. **Full traceability** — every repair has a clear history of
   work items, parts, and hours
2. **Digital approval** — the customer approves the quote online,
   no phone calls needed
3. **Mechanic control** — only works on what has been authorized
4. **Owner visibility** — dashboard showing the status of all
   open orders

---

## Scope In

- Create a repair order (customer, motorcycle, problem description)
- Add line items to the quote: labor (hours) and spare parts
- Automatic total calculation
- Generate a unique, secure approval link for the customer
- Customer can approve or reject the quote via browser (no login)
- Order status transitions:
  `DRAFT → QUOTED → APPROVED → IN_PROGRESS → COMPLETED`
- Mechanic is notified when the customer approves

---

## Scope Out

- Payments and invoicing
- Spare parts inventory management
- Mobile app
- Customer repair history
- Automatic email sending (phase 2)

---

## Acceptance Criteria

### AC1 — Create repair order
**Given** an authenticated mechanic with an existing customer
and motorcycle in the system  
**When** the mechanic creates a new repair order with plate,
customer, and problem description  
**Then** the order is saved with status `DRAFT`, creation date,
and assigned mechanic, and appears in the mechanic's order list

### AC2 — Generate quote
**Given** a `DRAFT` order with 2 labor hours (€50/h) and
one part "Oil Filter" at €25  
**When** the mechanic generates the quote  
**Then** the total is €125 (€100 labor + €25 part), the status
becomes `QUOTED`, and a unique approval token is generated

### AC3 — Customer approval
**Given** a customer accessing the quote approval link for €125  
**When** the customer clicks "Approve Quote"  
**Then** the order status becomes `APPROVED`, the mechanic sees
the order in the "Ready to Work" list, and the approval link
is no longer usable