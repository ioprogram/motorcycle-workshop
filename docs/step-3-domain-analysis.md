# Step 3 — Domain Analysis

**Generated with:** SPDD domain-analysis prompt  
**Date:** 2026-05-25  
**Based on:** `requirements/user-story-1-repair-order-management.md` + `docs/step-2-clarification.md`  
**Target stack:** Java 21 / Spring Boot 3 / Spring Data JPA / PostgreSQL

---

## 1. Domain Entities

### 1.1 RepairOrder *(Aggregate Root)*

The central entity. All business operations revolve around it.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | PK |
| `status` | `OrderStatus` | Enum — see §1.8 |
| `problemDescription` | `String` | Mandatory at creation |
| `totalAmount` | `BigDecimal` | Computed from line items; stored for snapshot integrity |
| `tokenRenewalCount` | `int` | 0–3; never resets |
| `createdAt` | `Instant` | Set on insert |
| `updatedAt` | `Instant` | Updated on every state change |
| `version` | `Long` | Optimistic-lock counter (`@Version`) |
| `mechanic` | `→ User` | Assigned mechanic (FK) |
| `customer` | `→ Customer` | FK |
| `motorcycle` | `→ Motorcycle` | FK |

Relationships:
- `@OneToMany(cascade = ALL, orphanRemoval = true)` → `LineItem`
- `@OneToOne(cascade = ALL, orphanRemoval = true)` → `InspectionChecklist`
- `@OneToMany` → `QuoteToken` (one active at most)

---

### 1.2 Customer

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | PK |
| `fullName` | `String` | |
| `phone` | `String` | Primary contact channel |
| `email` | `String` | Optional; reserved for phase-2 notifications |

---

### 1.3 Motorcycle

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | PK |
| `licensePlate` | `String` | Unique index; uppercased |
| `make` | `String` | |
| `model` | `String` | |
| `year` | `int` | |
| `customer` | `→ Customer` | FK — owner; must match the RepairOrder.customer |

---

### 1.4 LineItem

Lives inside the RepairOrder aggregate boundary.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | PK |
| `repairOrder` | `→ RepairOrder` | FK |
| `type` | `LineItemType` | `LABOR` or `PART` |
| `description` | `String` | e.g. "Oil Filter", "Engine disassembly" |
| `quantity` | `BigDecimal` | Hours for `LABOR`; unit count for `PART` |
| `unitPrice` | `BigDecimal` | Price at time of entry; never updated retrospectively |
| `subtotal` | `BigDecimal` | Stored (`quantity × unitPrice`); not purely derived |

---

### 1.5 InspectionChecklist

One-to-one with RepairOrder; mandatory gate before quote generation.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | PK |
| `repairOrder` | `→ RepairOrder` | FK 1:1 |
| `visualInspection` | `boolean` | Step 1 |
| `engineAndFluidCheck` | `boolean` | Step 2 |
| `brakeAndSuspensionCheck` | `boolean` | Step 3 |
| `electricalSystemCheck` | `boolean` | Step 4 |
| `mileageAndWearAssessment` | `boolean` | Step 5 |
| `completedAt` | `Instant` | Set when all 5 become `true`; nullable until then |
| `completedBy` | `→ User` | FK — who marked the final step |

---

### 1.6 QuoteToken

One active token per order at any time; full history retained.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | PK |
| `repairOrder` | `→ RepairOrder` | FK |
| `tokenHash` | `String` | SHA-256 of the raw token; never store raw value |
| `createdAt` | `Instant` | |
| `expiresAt` | `Instant` | `createdAt + 48h` |
| `status` | `TokenStatus` | `ACTIVE`, `USED`, `EXPIRED`, `INVALIDATED` |
| `renewalSequence` | `int` | 1 for first issue, 2 for first renewal, 3 for second renewal |
| `usedAt` | `Instant` | Nullable; set on use |

Partial unique index: `UNIQUE (repair_order_id) WHERE status = 'ACTIVE'` — enforces one active token per order at DB level.

---

### 1.7 User

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | PK |
| `username` | `String` | Unique |
| `passwordHash` | `String` | BCrypt |
| `fullName` | `String` | |
| `role` | `UserRole` | `MECHANIC`, `SHOP_MANAGER` |

---

### 1.8 Enumerations

```
OrderStatus:   DRAFT | QUOTED | APPROVED | IN_PROGRESS | COMPLETED | REJECTED | CANCELLED
LineItemType:  LABOR | PART
TokenStatus:   ACTIVE | USED | EXPIRED | INVALIDATED
UserRole:      MECHANIC | SHOP_MANAGER
```

---

### 1.9 Entity Relationship Summary

```
Customer ──< Motorcycle
Customer ──< RepairOrder
Motorcycle ──< RepairOrder
User (Mechanic) ──< RepairOrder
RepairOrder ──< LineItem
RepairOrder ──1 InspectionChecklist
RepairOrder ──< QuoteToken
```

---

## 2. Business Rules

### 2.1 Explicit Rules

| ID | Rule |
|---|---|
| BR-01 | All 5 inspection items must be `true` before a quote can be generated. |
| BR-02 | Quote generation transitions the order from `DRAFT` → `QUOTED`. |
| BR-03 | A token is valid for exactly 48 hours from its creation time. |
| BR-04 | Maximum 3 token renewals per order (renewalSequence values: 1, 2, 3). |
| BR-05 | After the 3rd token expiration without use → order transitions to `CANCELLED`. |
| BR-06 | A token is single-use: after approval or rejection it moves to `USED` or `INVALIDATED`. |
| BR-07 | Shop Manager may modify line items on `QUOTED` orders. |
| BR-08 | Shop Manager may self-approve a quote they created (no four-eyes enforcement). |
| BR-09 | All status transitions are **unidirectional**; no rollback is permitted. |
| BR-10 | The customer approval link requires no authentication. |
| BR-11 | `RepairOrder.totalAmount` = Σ `lineItem.subtotal` for all line items. |

### 2.2 Implicit Rules

| ID | Rule | Source |
|---|---|---|
| IBR-01 | Only `DRAFT` orders accept new line items from Mechanic. | AC1 + role table |
| IBR-02 | An order must have ≥ 1 line item before quote generation. | AC2 implies non-zero total |
| IBR-03 | `Motorcycle.customer` must equal `RepairOrder.customer`. | AC1 "existing customer and motorcycle" |
| IBR-04 | `LineItem.unitPrice` is immutable after creation. | Snapshot integrity |
| IBR-05 | Only one `QuoteToken` with `status = ACTIVE` may exist per order. | Partial unique index |
| IBR-06 | `COMPLETED`, `CANCELLED`, and `REJECTED` are **terminal** states. | State machine diagram |
| IBR-07 | A Mechanic cannot modify line items on `QUOTED` orders. | Role table |
| IBR-08 | `tokenRenewalCount` is monotonically increasing and never resets. | Renewal logic |

---

## 3. Order Status Machine

DRAFT --[mechanic generates quote]--> QUOTED --[SM approves internally]--> APPROVED --[customer approves]--> IN_PROGRESS --[SM marks done]--> COMPLETED
                                                                               |                    |
                                                              [SM or customer rejects]    [3rd token expiry]
                                                                               v                    v
                                                                           REJECTED            CANCELLED

**Key decisions:**
- Token is generated when SM approves (APPROVED), not when mechanic generates quote (QUOTED)
- CANCELLED is reachable from APPROVED only (not from QUOTED)
- Token invalidation on line-item change does NOT consume a renewal slot
- Only SM can mark an order as COMPLETED

---

## 4. Strategic Direction

### 4.1 Architecture Pattern

Use **hexagonal architecture** (ports and adapters):

```
[HTTP REST adapters]          [Domain Core]             [Persistence adapters]
  OrderController       →   RepairOrderService       →   OrderRepository (JPA)
  QuoteTokenController  →   QuoteTokenService        →   TokenRepository (JPA)
  CustomerController    →   CustomerService          →   CustomerRepository (JPA)
                            InspectionService
                            OrderStatusGuard         (state machine enforcement)
```

Keep all business rules in the domain core. Controllers and repositories are thin.

---

### 4.2 State Machine Enforcement

Do **not** use Spring State Machine for this use case — it adds significant overhead for a simple linear machine.  
Instead, implement an `OrderStatusGuard`:

```java
// Enforces allowed transitions at service layer; throws DomainException on violation
public enum OrderStatus {
    DRAFT, QUOTED, APPROVED, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
        DRAFT,       Set.of(QUOTED),
        QUOTED,      Set.of(APPROVED, REJECTED, CANCELLED),
        APPROVED,    Set.of(IN_PROGRESS, CANCELLED),
        IN_PROGRESS, Set.of(COMPLETED)
    );

    public boolean canTransitionTo(OrderStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }
}
```

Validate before every `repairOrder.setStatus(next)`. Use `@PreUpdate` or a protected setter as a second line of defense.

---

### 4.3 Token Security

```java
// Generation: 256 bits of entropy, URL-safe Base64
String rawToken = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(SecureRandom.getInstanceStrong().generateSeed(32));

// Storage: only the hash goes to DB
String tokenHash = DigestUtils.sha256Hex(rawToken);
```

Return `rawToken` once in the response; store only `tokenHash`. On validation, hash the incoming value and compare.

---

### 4.4 Token Expiration Scheduling

Use a `@Scheduled` job to detect expired active tokens and auto-cancel orders that have exhausted all renewals:

```java
@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
public void expireTokensAndCancelOrders() {
    // SELECT tokens WHERE status = ACTIVE AND expires_at < NOW()
    // For each: mark EXPIRED; if order.tokenRenewalCount == 3 → set order.status = CANCELLED
}
```

On every token validation request, **also** check expiry inline — do not rely solely on the scheduled job.

---

### 4.5 Authorization Model

| Endpoint | Required Role |
|---|---|
| `POST /api/orders` | MECHANIC or SHOP_MANAGER |
| `POST /api/orders/{id}/line-items` | MECHANIC (DRAFT) or SHOP_MANAGER (DRAFT or QUOTED) |
| `POST /api/orders/{id}/checklist` | MECHANIC or SHOP_MANAGER |
| `POST /api/orders/{id}/generate-quote` | MECHANIC or SHOP_MANAGER |
| `POST /api/orders/{id}/approve` | SHOP_MANAGER |
| `GET /api/orders` | MECHANIC or SHOP_MANAGER |
| `POST /public/quotes/{token}/approve` | **No auth** — customer |
| `POST /public/quotes/{token}/reject` | **No auth** — customer |

Spring Security: use `@PreAuthorize("hasRole('SHOP_MANAGER')")` on service methods, not just controllers.

---

### 4.6 Database Indexes and Constraints

```sql
-- Guarantee single active token per order
CREATE UNIQUE INDEX uix_quote_tokens_active
    ON quote_tokens (repair_order_id)
    WHERE status = 'ACTIVE';

-- Fast token lookup by hash
CREATE INDEX ix_quote_tokens_hash ON quote_tokens (token_hash);

-- Fast expiry scan for scheduled job
CREATE INDEX ix_quote_tokens_expires ON quote_tokens (expires_at)
    WHERE status = 'ACTIVE';

-- Enforce renewal ceiling at DB level
ALTER TABLE repair_orders
    ADD CONSTRAINT chk_token_renewal_count
    CHECK (token_renewal_count BETWEEN 0 AND 3);

-- Motorcycle–customer consistency enforced in application layer (not just FK)
```

---

### 4.7 Concurrency

Apply `@Version` (optimistic locking) on `RepairOrder`. Any concurrent update (line-item add, status change, token renewal) will throw `OptimisticLockException` if another transaction committed first. Expose this to the client as HTTP 409 Conflict with a retry hint.

For the token single-use guarantee use an atomic CAS update:

```sql
UPDATE quote_tokens
SET status = 'USED', used_at = NOW()
WHERE token_hash = :hash
  AND status = 'ACTIVE'
  AND expires_at > NOW()
```

Check `rowsAffected == 1`; if 0, the token was already used or expired (return 409 or 410).

---

## 5. Risks and Edge Cases

| ID | Risk | Severity | Mitigation |
|---|---|---|---|
| R-01 | **Token race condition** — two concurrent requests submit the same token before either marks it `USED`. | High | Atomic CAS SQL update; check `rowsAffected`. |
| R-02 | **State machine bypass** — a JPA save sets `status` directly without going through the service. | High | Protected setter + `@PreUpdate` validation hook; never expose `setStatus` as public. |
| R-03 | **Line-item modification invalidates sent quote** — Shop Manager changes the total on a `QUOTED` order after the customer has already received the token link. The customer sees a stale amount. | High | **Open question OQ-04**: should line-item changes invalidate the active token? |
| R-04 | **Scheduled job window** — token expires at T, job runs at T+4m; the link appears valid during those 4 minutes. | Medium | Validate expiry inline on every token access; job is cleanup only. |
| R-05 | **Motorcycle–customer mismatch** — order creation payload references a motorcycle owned by a different customer. | Medium | Service-layer guard: `motorcycle.customer.id == order.customer.id`. |
| R-06 | **Empty quote total** — quote generated with zero line items produces a €0.00 order. Approval link is generated but semantically meaningless. | Medium | Require `lineItems.size() ≥ 1` before allowing `generate-quote`. |
| R-07 | **Concurrent checklist completion** — two users mark step 5 simultaneously; the quote-generation gate fires twice. | Low-Medium | Hold optimistic lock on `RepairOrder`; the second transaction will conflict. |
| R-08 | **Third-expiry race vs. manual renewal** — the scheduled job marks order `CANCELLED` at the same instant a user triggers a renewal. | Low-Medium | Optimistic lock + DB constraint `token_renewal_count ≤ 3`. |
| R-09 | **Action on terminal state** — an external client retries approval on a `CANCELLED` order (e.g., network retry). | Low | All service methods must check `status.isTerminal()` first and return `409 Conflict`. |
| R-10 | **Year-2038 problem on `expires_at`** — if stored as Unix int32. | Low | Use `TIMESTAMPTZ` in PostgreSQL and `Instant` in Java; never int32. |

---

## 6. Open Questions — Resolved

| ID | Question | Decision |
|---|---|---|
| OQ-01 | Two-phase vs. single approval | **Two-phase**: SM approves internally → APPROVED; customer approves → IN_PROGRESS |
| OQ-02 | Token generation point | **At APPROVED** — token generated when SM approves, not when mechanic generates quote |
| OQ-03 | Token renewal trigger | **Manual by SM** — customer requests renewal, SM performs it from the UI |
| OQ-04 | Line-item change after token issued | **Token invalidated** — new token generated; does NOT consume a renewal slot |
| OQ-05 | Mechanic notification | **Dashboard only** — order appears in "Ready to Work" list; no Notification entity needed |
| OQ-06 | Customer minimum data | **Inline creation** — mechanic creates customer on the fly if not found; required fields: fullName + phone |
| OQ-07 | Motorcycle minimum data | **Inline creation** — mechanic creates motorcycle on the fly if not found; required fields: licensePlate + make + model + year |
| OQ-08 | IN_PROGRESS → COMPLETED trigger | **SM only** — Shop Manager manually marks the order as COMPLETED |