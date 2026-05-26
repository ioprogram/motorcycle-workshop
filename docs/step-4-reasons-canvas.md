# Step 4 — REASONS Canvas

**Generated with:** `/spdd-analysis` (REASONS canvas prompt)  
**Date:** 2026-05-25  
**Based on:** `requirements/user-story-1-repair-order-management.md` + `docs/step-2-clarification.md` + `docs/step-3-domain-analysis.md`  
**Target stack:** Java 21 / Spring Boot 3.3 / Spring Data JPA / PostgreSQL / React (minimal, iteration 1)

---

## Locked Design Decisions (from Step 3 review)

These ambiguities were resolved during the Step 3 review and are authoritative for this canvas.

| # | Decision |
|---|---|
| D-01 | Token is generated when the **Shop Manager approves** the order (QUOTED → APPROVED). |
| D-02 | **CANCELLED is reachable from APPROVED only** (not QUOTED). A token must exist before it can expire 3 times. |
| D-03 | When SM modifies line items on an **APPROVED** order, the active token is **INVALIDATED** without consuming a renewal slot. SM must then explicitly call `/renew-token` to issue a new one (which does consume a slot). |
| D-04 | **Inline customer and motorcycle creation is allowed** via dedicated endpoints; order creation references their IDs. |
| D-05 | **Only SHOP_MANAGER can transition an order to COMPLETED** (resolves OQ-08). |
| D-06 | **No Notification entity**; mechanic notification is fulfilled by a filtered "Ready to Work" dashboard list (resolves OQ-05). |
| D-07 | Token renewal is a **manual action by SHOP_MANAGER** only; each renewal is logged with actor + timestamp. |

---

## R — Requirements

### Definition of Done Checklist

#### AC1 — Create Repair Order
- [ ] `POST /api/orders` returns `201` with `{ id, status: "DRAFT", createdAt, mechanic, customer, motorcycle }`
- [ ] `mechanic` is auto-assigned from the authenticated JWT principal
- [ ] Returns `404` if `customerId` or `motorcycleId` does not exist
- [ ] Returns `422` if `motorcycle.customer.id ≠ order.customer.id`
- [ ] An empty `InspectionChecklist` (all 5 booleans = false) is created and attached to the order

#### AC2 — Generate Quote
- [ ] All 5 inspection checks must be `true` before `generate-quote` is accepted; else `422 "Checklist incomplete"`
- [ ] Order must have ≥ 1 line item; else `422 "No line items"`
- [ ] `POST /api/orders/{id}/generate-quote` transitions status `DRAFT → QUOTED`
- [ ] `totalAmount` is computed as Σ(quantity × unitPrice) and stored on the order
- [ ] Response includes `status: "QUOTED"` and computed `totalAmount`

#### AC3 — Shop Manager Approves Quote
- [ ] `POST /api/orders/{id}/approve` is accessible only to `SHOP_MANAGER`
- [ ] Requires `order.status == QUOTED`; else `409`
- [ ] Transitions status `QUOTED → APPROVED`
- [ ] A `QuoteToken` is created: `status=ACTIVE`, `expiresAt=now+48h`, `renewalSequence=1`
- [ ] `rawToken` is returned **once** in the response body and never stored (only `tokenHash` persists)
- [ ] Partial unique DB index guarantees at most one `ACTIVE` token per order at any time

#### AC4 — Customer Approves Quote
- [ ] `POST /public/quotes/{token}/approve` requires **no authentication**
- [ ] Token is validated inline: exists + `status=ACTIVE` + `expiresAt > now`
- [ ] Token use is an **atomic CAS** update: `UPDATE … WHERE status='ACTIVE' AND expires_at > NOW()`
- [ ] On success: token → `USED`, order → `IN_PROGRESS`
- [ ] Second attempt returns `409` (token already used)
- [ ] Returns `200` with a confirmation message

#### AC5 — Token Expiry and Auto-Cancel
- [ ] Scheduled job runs every 5 minutes; marks expired tokens and auto-cancels after 3rd expiration
- [ ] Every token access performs an **inline expiry check** independent of the job
- [ ] Token expiry when `tokenRenewalCount < 3` → token `EXPIRED`; order stays `APPROVED`
- [ ] Token expiry when `tokenRenewalCount == 3` → token `EXPIRED`; order → `CANCELLED`
- [ ] SHOP_MANAGER can manually renew up to `renewalSequence = 3` (total 3 issuances)
- [ ] After `CANCELLED`, all service methods return `409` immediately

#### Non-Functional
- [ ] `@Version` on `RepairOrder` prevents concurrent writes; `OptimisticLockException` → HTTP `409`
- [ ] All passwords stored as BCrypt hashes; raw tokens never stored
- [ ] All `/api/**` endpoints require a valid JWT; `/public/**` endpoints require no auth
- [ ] All status transitions validated by `OrderStatus.canTransitionTo()` before any persistence write
- [ ] Unit tests cover every business rule in domain services
- [ ] Integration tests cover every endpoint for both happy path and primary error paths
- [ ] Concurrent approval race-condition test proves only one request succeeds

---

## E — Entities

JPA pseudocode — annotation style abbreviated for readability.

```java
// ── AppUser ────────────────────────────────────────────────────────────────
@Entity @Table(name = "app_users")
class AppUser {
    @Id UUID id;
    @Column(unique=true, nullable=false) String username;
    @Column(nullable=false)              String passwordHash;   // BCrypt
    @Column(nullable=false)              String fullName;
    @Enumerated(STRING) @Column(nullable=false) UserRole role;  // MECHANIC | SHOP_MANAGER
    @Version Long version;
}

// ── Customer ───────────────────────────────────────────────────────────────
@Entity @Table(name = "customers")
class Customer {
    @Id UUID id;
    @Column(nullable=false) String fullName;
    @Column(nullable=false) String phone;
    @Column                 String email;   // nullable; reserved for phase-2 notifications
    @Version Long version;
}

// ── Motorcycle ─────────────────────────────────────────────────────────────
@Entity @Table(name = "motorcycles")
class Motorcycle {
    @Id UUID id;
    @Column(unique=true, nullable=false) String licensePlate;  // uppercased before save
    @Column(nullable=false)              String make;
    @Column(nullable=false)              String model;
    @Column(nullable=false)              int    year;
    @ManyToOne(fetch=LAZY) @JoinColumn(nullable=false) Customer customer;
    @Version Long version;
}

// ── RepairOrder (Aggregate Root) ───────────────────────────────────────────
@Entity @Table(name = "repair_orders")
class RepairOrder {
    @Id UUID id;
    @Enumerated(STRING) @Column(nullable=false) OrderStatus status;  // default DRAFT
    @Column(nullable=false)           String     problemDescription;
    @Column(precision=10, scale=2)    BigDecimal totalAmount;        // 0.00 until computed
    @Column(nullable=false)           int        tokenRenewalCount;  // 0–3, monotonic
    @Column(nullable=false)           Instant    createdAt;
    @Column(nullable=false)           Instant    updatedAt;
    @Version Long version;

    @ManyToOne(fetch=LAZY) @JoinColumn(nullable=false) AppUser    mechanic;
    @ManyToOne(fetch=LAZY) @JoinColumn(nullable=false) Customer   customer;
    @ManyToOne(fetch=LAZY) @JoinColumn(nullable=false) Motorcycle motorcycle;

    @OneToMany(mappedBy="repairOrder", cascade=ALL, orphanRemoval=true)
    List<LineItem> lineItems;

    @OneToOne(mappedBy="repairOrder", cascade=ALL, orphanRemoval=true)
    InspectionChecklist inspectionChecklist;

    @OneToMany(mappedBy="repairOrder")
    List<QuoteToken> quoteTokens;

    // ── Protected state-machine gate ──────────────────────────────────────
    // Direct setStatus() is package-private; all callers go through this method.
    void transitionTo(OrderStatus next) {
        if (!this.status.canTransitionTo(next))
            throw new IllegalTransitionException(this.status, next);
        this.status    = next;
        this.updatedAt = Instant.now();
    }
}

// ── LineItem ───────────────────────────────────────────────────────────────
@Entity @Table(name = "line_items")
class LineItem {
    @Id UUID id;
    @ManyToOne(fetch=LAZY) @JoinColumn(nullable=false) RepairOrder repairOrder;
    @Enumerated(STRING) @Column(nullable=false) LineItemType type;   // LABOR | PART
    @Column(nullable=false)              String     description;
    @Column(nullable=false, precision=10, scale=2) BigDecimal quantity;
    @Column(nullable=false, precision=10, scale=2) BigDecimal unitPrice;  // immutable after creation
    @Column(nullable=false, precision=10, scale=2) BigDecimal subtotal;   // stored; quantity × unitPrice
    @Version Long version;
}

// ── InspectionChecklist ────────────────────────────────────────────────────
@Entity @Table(name = "inspection_checklists")
class InspectionChecklist {
    @Id UUID id;
    @OneToOne(fetch=LAZY) @JoinColumn(nullable=false) RepairOrder repairOrder;
    boolean visualInspection;         // step 1
    boolean engineAndFluidCheck;      // step 2
    boolean brakeAndSuspensionCheck;  // step 3
    boolean electricalSystemCheck;    // step 4
    boolean mileageAndWearAssessment; // step 5
    Instant completedAt;              // set when all 5 become true; nullable until then
    @ManyToOne(fetch=LAZY) AppUser completedBy;  // nullable until all 5 true
    @Version Long version;

    boolean isComplete() {
        return visualInspection && engineAndFluidCheck
            && brakeAndSuspensionCheck && electricalSystemCheck
            && mileageAndWearAssessment;
    }
}

// ── QuoteToken ─────────────────────────────────────────────────────────────
// Partial unique index must be added via Flyway migration (JPA cannot express it):
//   CREATE UNIQUE INDEX uix_qt_active ON quote_tokens(repair_order_id) WHERE status='ACTIVE';
@Entity @Table(name = "quote_tokens",
    indexes = @Index(name="ix_qt_hash",    columnList="token_hash"),
              @Index(name="ix_qt_expires", columnList="expires_at")
)
class QuoteToken {
    @Id UUID id;
    @ManyToOne(fetch=LAZY) @JoinColumn(nullable=false) RepairOrder repairOrder;
    @Column(nullable=false) String      tokenHash;       // SHA-256; raw token never stored
    @Column(nullable=false) Instant     createdAt;
    @Column(nullable=false) Instant     expiresAt;       // createdAt + 48h
    @Enumerated(STRING) @Column(nullable=false) TokenStatus status; // ACTIVE|USED|EXPIRED|INVALIDATED
    @Column(nullable=false) int         renewalSequence; // 1 = first issue, 2 = first renewal, 3 = second renewal
    Instant usedAt;                                      // nullable; set on use or invalidation
    @ManyToOne(fetch=LAZY) AppUser issuedBy;             // SM who approved or renewed
    @Version Long version;
}
```

### Enumerations

```java
enum OrderStatus {
    DRAFT, QUOTED, APPROVED, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
        DRAFT,       Set.of(QUOTED),
        QUOTED,      Set.of(APPROVED, REJECTED),
        APPROVED,    Set.of(IN_PROGRESS, REJECTED, CANCELLED),
        IN_PROGRESS, Set.of(COMPLETED)
        // COMPLETED, REJECTED, CANCELLED → terminal; empty set by default
    );

    public boolean canTransitionTo(OrderStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED;
    }
}

enum LineItemType { LABOR, PART }
enum TokenStatus  { ACTIVE, USED, EXPIRED, INVALIDATED }
enum UserRole     { MECHANIC, SHOP_MANAGER }
```

### State Machine Diagram

```
                                                      [customer approve]
DRAFT ──[generate-quote]──► QUOTED ──[SM approve]──► APPROVED ─────────────► IN_PROGRESS ──[SM complete]──► COMPLETED
                               │                         │                         │
                               │ [SM reject]             │ [customer reject]       │ (no transitions out
                               ▼                         ▼                         │  other than COMPLETED)
                            REJECTED ◄──────────────── REJECTED
                                                         │
                                              [3rd token expiry]
                                                         ▼
                                                     CANCELLED
```

> Terminal states: **COMPLETED**, **REJECTED**, **CANCELLED** — no further transitions.

---

## A — Approach

### Hexagonal Architecture (Ports & Adapters)

**Chosen because:** isolates all business rules in domain services that are testable without loading the Spring context. Controllers and repositories are thin translation layers.

```
[HTTP adapters]              [Domain Core]                [Persistence adapters]
OrderController         →   RepairOrderService        →   JpaOrderRepository
PublicQuoteController   →   QuoteTokenService         →   JpaTokenRepository
CustomerController      →   CustomerService           →   JpaCustomerRepository
MotorcycleController    →   MotorcycleService         →   JpaMotorcycleRepository
                            InspectionService
```

**Rule:** controllers call port/in interfaces; services call port/out interfaces; no business logic in controllers or JPA repositories.

---

### State Machine: `OrderStatus.canTransitionTo()` + `RepairOrder.transitionTo()`

**Chosen because:** Spring State Machine adds significant overhead for a 7-state linear machine. A `Map<OrderStatus, Set<OrderStatus>>` is 15 lines, fully testable, enforced at the aggregate boundary. The `@PreUpdate` JPA hook provides a second validation layer in case a code path bypasses the service.

---

### Token Security: SecureRandom → SHA-256

```java
// Generation
String rawToken = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(SecureRandom.getInstanceStrong().generateSeed(32)); // 256 bits

// Storage: only the hash
String tokenHash = DigestUtils.sha256Hex(rawToken);
```

rawToken is returned exactly once in the API response and never persisted.  
On validation: hash the incoming value, compare against `token_hash` column.

---

### Single-Use Atomicity: CAS SQL Update

```sql
UPDATE quote_tokens
SET    status = 'USED', used_at = NOW()
WHERE  token_hash = :hash
  AND  status = 'ACTIVE'
  AND  expires_at > NOW()
```

Check `rowsAffected == 1`. If 0 → return 409/410 without loading the order. Prevents double-approval under concurrent requests.

---

### Optimistic Locking: `@Version` on `RepairOrder`

**Chosen because:** low contention per order (one mechanic per bike). Avoids DB-level row locks. Any concurrent update (line-item add, status change, token renewal) throws `OptimisticLockException` → HTTP 409 with a retry hint.

---

### Token Expiry: `@Scheduled` Job + Inline Check

The scheduled job is **cleanup only** — it is not the sole guard. Every token access validates expiry inline first. This prevents a 5-minute window where an expired link still appears valid.

---

### Authentication: JWT (Spring Security + jjwt)

Stateless REST authentication. `SecurityConfig` permits `/public/**` unconditionally and requires `Bearer` token on all `/api/**` paths. Role-level authorization via `@PreAuthorize` on service methods, not just controllers.

---

### Frontend (Iteration 1): React + useState + fetch

No Redux/Zustand — premature for this scope. Fetch wrappers in `workshopApi.js`. Two route groups: `/app/*` (authenticated, mechanic/manager views) and `/quote/:token` (public approval page).

---

## S — Structure

```
com.workshop
│
├── WorkshopApplication.java
│
├── domain
│   ├── model
│   │   ├── AppUser.java
│   │   ├── Customer.java
│   │   ├── InspectionChecklist.java
│   │   ├── LineItem.java
│   │   ├── Motorcycle.java
│   │   ├── QuoteToken.java
│   │   └── RepairOrder.java
│   ├── enums
│   │   ├── LineItemType.java
│   │   ├── OrderStatus.java              ← canTransitionTo(), isTerminal()
│   │   ├── TokenStatus.java
│   │   └── UserRole.java
│   └── exception
│       ├── DomainException.java           ← base unchecked exception
│       ├── IllegalTransitionException.java
│       ├── ResourceNotFoundException.java
│       └── TokenException.java            ← invalid / expired / already-used token
│
├── port
│   ├── in                                 ← use-case interfaces (controllers call these)
│   │   ├── AddLineItemUseCase.java
│   │   ├── ApproveQuoteUseCase.java       ← SM approval; issues customer token
│   │   ├── CompleteChecklistUseCase.java
│   │   ├── CompleteOrderUseCase.java
│   │   ├── CreateCustomerUseCase.java
│   │   ├── CreateOrderUseCase.java
│   │   ├── CustomerApprovalUseCase.java   ← public token endpoints
│   │   ├── GenerateQuoteUseCase.java
│   │   ├── RegisterMotorcycleUseCase.java
│   │   ├── RejectQuoteUseCase.java
│   │   ├── RemoveLineItemUseCase.java
│   │   ├── RenewTokenUseCase.java
│   │   └── UpdateLineItemUseCase.java
│   └── out                                ← persistence interfaces (services call these)
│       ├── CustomerRepository.java
│       ├── MotorcycleRepository.java
│       ├── OrderRepository.java
│       ├── TokenRepository.java
│       └── UserRepository.java
│
├── domain
│   └── service                            ← domain services; implement port/in interfaces
│       ├── CustomerService.java
│       ├── InspectionService.java
│       ├── MotorcycleService.java
│       ├── QuoteTokenService.java
│       └── RepairOrderService.java
│
├── adapter
│   ├── in
│   │   └── web
│   │       ├── CustomerController.java          ← /api/customers
│   │       ├── MotorcycleController.java        ← /api/motorcycles
│   │       ├── OrderController.java             ← /api/orders/**
│   │       ├── PublicQuoteController.java       ← /public/quotes/**
│   │       ├── GlobalExceptionHandler.java      ← @RestControllerAdvice
│   │       └── dto
│   │           ├── request
│   │           │   ├── AddLineItemRequest.java
│   │           │   ├── ChecklistUpdateRequest.java
│   │           │   ├── CreateCustomerRequest.java
│   │           │   ├── CreateOrderRequest.java
│   │           │   ├── RegisterMotorcycleRequest.java
│   │           │   └── UpdateLineItemRequest.java
│   │           └── response
│   │               ├── CustomerResponse.java
│   │               ├── LineItemResponse.java
│   │               ├── MotorcycleResponse.java
│   │               ├── OrderDetailResponse.java
│   │               ├── OrderSummaryResponse.java
│   │               ├── QuoteTokenResponse.java    ← includes rawToken (one-time only)
│   │               └── QuoteViewResponse.java     ← public quote view (no rawToken)
│   └── out
│       └── persistence
│           ├── JpaCustomerRepository.java
│           ├── JpaMotorcycleRepository.java
│           ├── JpaOrderRepository.java
│           ├── JpaTokenRepository.java
│           ├── JpaUserRepository.java
│           └── mapper
│               ├── CustomerMapper.java
│               ├── LineItemMapper.java
│               ├── MotorcycleMapper.java
│               ├── OrderMapper.java
│               └── TokenMapper.java
│
└── infrastructure
    ├── security
    │   ├── JwtAuthFilter.java
    │   ├── JwtService.java
    │   ├── SecurityConfig.java
    │   └── UserDetailsServiceImpl.java
    └── scheduling
        └── TokenExpiryJob.java

src/main/resources/
├── application.yml
├── application-dev.yml
└── db/migration/
    ├── V1__create_schema.sql
    ├── V2__unique_index_quote_token_active.sql    ← partial unique index (JPA cannot express)
    └── V3__check_constraint_renewal_count.sql     ← CHECK (token_renewal_count BETWEEN 0 AND 3)
```

**Frontend (separate `frontend/` directory or `src/main/resources/static`):**
```
src/
├── api/
│   └── workshopApi.js          ← fetch wrappers; injects Bearer token
├── components/
│   ├── orders/
│   │   ├── OrderList.jsx
│   │   ├── OrderDetail.jsx
│   │   ├── CreateOrderForm.jsx
│   │   ├── LineItemForm.jsx
│   │   └── ChecklistForm.jsx
│   └── public/
│       └── QuoteApprovalPage.jsx   ← route: /quote/:token
└── App.jsx
```

---

## O — Operations

> **Conventions:**
> - `[AUTH]` = Bearer JWT required; role listed explicitly
> - `[PUBLIC]` = no authentication required
> - Steps are pseudocode inside the service method (after controller validation)
> - All responses are JSON
> - `→ NNN` in steps means "return HTTP NNN immediately"

---

### O-01 — Create Customer

```
POST /api/customers
Auth: [AUTH] MECHANIC | SHOP_MANAGER

Request body:
  { "fullName": "string (required)", "phone": "string (required)", "email": "string (optional)" }

Steps:
  1. Validate: fullName not blank, phone not blank → 400 on failure
  2. Create Customer { id=UUID.randomUUID(), fullName, phone, email }
  3. customerRepository.save(customer)
  4. Return CustomerResponse

Response:  201 Created
Body:      { "id": UUID, "fullName": string, "phone": string, "email": string|null }
Errors:    400 validation failure
```

---

### O-02 — Register Motorcycle

```
POST /api/motorcycles
Auth: [AUTH] MECHANIC | SHOP_MANAGER

Request body:
  { "licensePlate": "string", "make": "string", "model": "string", "year": int, "customerId": UUID }

Steps:
  1. Validate: all fields not blank, year between 1900 and currentYear+1 → 400 on failure
  2. Load customer = customerRepository.findById(customerId) → 404 if absent
  3. plateUpper = licensePlate.toUpperCase()
  4. Check motorcycleRepository.existsByLicensePlate(plateUpper) → 409 if already registered
  5. Create Motorcycle { id=UUID.randomUUID(), licensePlate=plateUpper, make, model, year, customer }
  6. motorcycleRepository.save(motorcycle)
  7. Return MotorcycleResponse

Response:  201 Created
Body:      { "id": UUID, "licensePlate": string, "make": string, "model": string, "year": int, "customerId": UUID }
Errors:    400 | 404 customer not found | 409 plate already registered
```

---

### O-03 — Create Repair Order

```
POST /api/orders
Auth: [AUTH] MECHANIC | SHOP_MANAGER

Request body:
  { "customerId": UUID, "motorcycleId": UUID, "problemDescription": "string" }

Steps:
  1. Validate: problemDescription not blank → 400 on failure
  2. Load customer   = customerRepository.findById(customerId)   → 404 if absent
  3. Load motorcycle = motorcycleRepository.findById(motorcycleId) → 404 if absent
  4. Guard: motorcycle.customer.id == customerId → 422 "Motorcycle does not belong to this customer"
  5. mechanic = authenticated AppUser (from JWT principal)
  6. Create RepairOrder {
       id = UUID.randomUUID(),
       status = DRAFT,
       problemDescription,
       totalAmount = 0.00,
       tokenRenewalCount = 0,
       createdAt = Instant.now(),
       updatedAt = Instant.now(),
       mechanic, customer, motorcycle
     }
  7. Create InspectionChecklist {
       id = UUID.randomUUID(),
       repairOrder = order,
       all 5 boolean fields = false,
       completedAt = null,
       completedBy = null
     }
  8. Attach checklist to order (cascade will persist both)
  9. orderRepository.save(order)
  10. Return OrderDetailResponse

Response:  201 Created
Body:      { "id", "status": "DRAFT", "createdAt", "mechanic": {...}, "customer": {...},
             "motorcycle": {...}, "lineItems": [], "checklist": { all booleans false } }
Errors:    400 | 404 customer or motorcycle not found | 422 motorcycle–customer mismatch
```

---

### O-04 — List Repair Orders

```
GET /api/orders?status=&page=0&size=20
Auth: [AUTH] MECHANIC | SHOP_MANAGER

Query params (all optional):
  status: OrderStatus filter
  page, size: pagination (default page=0, size=20)

Steps:
  1. If role == MECHANIC:  filter orders WHERE mechanic = principal
     If role == SHOP_MANAGER: return all orders
  2. Apply status filter if provided
  3. Sort by updatedAt DESC
  4. Page results
  5. Return list of OrderSummaryResponse

Response:  200 OK
Body:      {
             "content": [ { "id", "status", "customer": { "fullName" },
                            "motorcycle": { "licensePlate", "make", "model" },
                            "totalAmount", "updatedAt" } ],
             "page": int, "size": int, "totalElements": long
           }
```

---

### O-05 — Get Repair Order Detail

```
GET /api/orders/{id}
Auth: [AUTH] MECHANIC | SHOP_MANAGER

Steps:
  1. Load order (with line items, checklist, mechanic, customer, motorcycle) → 404 if absent
  2. If role == MECHANIC and order.mechanic.id ≠ principal.id → 403
  3. Return OrderDetailResponse

Response:  200 OK
Body:      { "id", "status", "problemDescription", "totalAmount", "tokenRenewalCount",
             "mechanic": {...}, "customer": {...}, "motorcycle": {...},
             "lineItems": [...], "checklist": {...},
             "createdAt", "updatedAt" }
Errors:    404 | 403
```

---

### O-06 — Add Line Item

```
POST /api/orders/{id}/line-items
Auth: [AUTH] MECHANIC | SHOP_MANAGER

Request body:
  { "type": "LABOR|PART", "description": "string", "quantity": decimal, "unitPrice": decimal }

Steps:
  1. Load order → 404 if absent
  2. If order.status.isTerminal() → 409
  3. Authorization × status guard:
       MECHANIC:      order.status == DRAFT           → else 422
       SHOP_MANAGER:  order.status in {DRAFT, QUOTED, APPROVED} → else 422
  4. Validate: description not blank, quantity > 0, unitPrice >= 0 → 400 on failure
  5. subtotal = quantity.multiply(unitPrice).setScale(2, HALF_UP)
  6. Create LineItem { id=UUID.randomUUID(), repairOrder=order, type, description,
                       quantity, unitPrice, subtotal }
  7. order.lineItems.add(lineItem)
  8. Recompute order.totalAmount = sum of all lineItem.subtotal
  9. If order.status == APPROVED and an ACTIVE QuoteToken exists:
       activeToken.status = INVALIDATED
       activeToken.usedAt = Instant.now()
       tokenRepository.save(activeToken)
       // tokenRenewalCount is NOT incremented (D-03)
  10. orderRepository.save(order)  // cascades lineItem
  11. Return LineItemResponse

Response:  201 Created
Body:      { "id", "type", "description", "quantity", "unitPrice", "subtotal" }
Errors:    400 | 404 | 403 | 409 terminal state | 422 wrong status
```

---

### O-07 — Remove Line Item

```
DELETE /api/orders/{id}/line-items/{itemId}
Auth: [AUTH] SHOP_MANAGER only

Steps:
  1. Load order → 404
  2. If order.status.isTerminal() → 409
  3. Guard: order.status in {DRAFT, QUOTED, APPROVED} → else 422
  4. Find lineItem in order.lineItems where lineItem.id == itemId → 404 if absent
  5. order.lineItems.remove(lineItem)
  6. Recompute order.totalAmount = sum of all remaining lineItem.subtotal
  7. If order.status == APPROVED and an ACTIVE QuoteToken exists:
       activeToken.status = INVALIDATED
       activeToken.usedAt = Instant.now()
       tokenRepository.save(activeToken)
       // tokenRenewalCount is NOT incremented (D-03)
  8. orderRepository.save(order)

Response:  204 No Content
Errors:    404 | 409 terminal state | 422 wrong status | 403 MECHANIC cannot call
```

---

### O-08 — Update Line Item

```
PUT /api/orders/{id}/line-items/{itemId}
Auth: [AUTH] SHOP_MANAGER only

Request body (all fields optional; only present fields are updated):
  { "description": "string", "quantity": decimal, "unitPrice": decimal }

Steps:
  1. Load order → 404
  2. If order.status.isTerminal() → 409
  3. Guard: order.status in {DRAFT, QUOTED, APPROVED} → else 422
  4. Find lineItem in order.lineItems where lineItem.id == itemId → 404
  5. Apply non-null fields from request to lineItem
  6. Recompute lineItem.subtotal = lineItem.quantity.multiply(lineItem.unitPrice).setScale(2, HALF_UP)
  7. Recompute order.totalAmount = sum of all lineItem.subtotal
  8. If order.status == APPROVED and an ACTIVE QuoteToken exists:
       activeToken.status = INVALIDATED
       activeToken.usedAt = Instant.now()
       tokenRepository.save(activeToken)
       // tokenRenewalCount is NOT incremented (D-03)
  9. orderRepository.save(order)
  10. Return LineItemResponse

Response:  200 OK
Body:      { "id", "type", "description", "quantity", "unitPrice", "subtotal" }
Errors:    400 | 404 | 409 terminal | 422 wrong status | 403
```

---

### O-09 — Update Inspection Checklist

```
PUT /api/orders/{id}/checklist
Auth: [AUTH] MECHANIC | SHOP_MANAGER

Request body (all fields optional; only present fields are updated):
  {
    "visualInspection":         boolean,
    "engineAndFluidCheck":      boolean,
    "brakeAndSuspensionCheck":  boolean,
    "electricalSystemCheck":    boolean,
    "mileageAndWearAssessment": boolean
  }

Steps:
  1. Load order → 404
  2. Guard: order.status == DRAFT → else 422 "Checklist is locked once order is QUOTED"
  3. checklist = order.inspectionChecklist
  4. Apply non-null boolean values from request
  5. If checklist.isComplete() and checklist.completedAt == null:
       checklist.completedAt = Instant.now()
       checklist.completedBy = principal
  6. checklistRepository.save(checklist)
  7. Return updated checklist fields

Response:  200 OK
Body:      { "visualInspection": bool, "engineAndFluidCheck": bool, "brakeAndSuspensionCheck": bool,
             "electricalSystemCheck": bool, "mileageAndWearAssessment": bool,
             "completedAt": instant|null }
Errors:    404 | 422 wrong status
```

---

### O-10 — Generate Quote

```
POST /api/orders/{id}/generate-quote
Auth: [AUTH] MECHANIC | SHOP_MANAGER

Steps:
  1. Load order (pessimistic read for optimistic lock version) → 404
  2. Guard: order.status == DRAFT → else 409
  3. Guard: order.inspectionChecklist.isComplete() == true → else 422 "Checklist incomplete"
  4. Guard: order.lineItems.size() >= 1 → else 422 "Order has no line items"
  5. Recompute and store order.totalAmount = sum of all lineItem.subtotal
  6. order.transitionTo(QUOTED)
  7. orderRepository.save(order)
  8. Return OrderDetailResponse

Response:  200 OK
Body:      { "id", "status": "QUOTED", "totalAmount", ... }
Errors:    404 | 409 wrong status | 422 checklist incomplete | 422 no line items
```

---

### O-11 — Shop Manager Approves Quote (Issues Customer Token)

```
POST /api/orders/{id}/approve
Auth: [AUTH] SHOP_MANAGER

Steps:
  1. Load order (with optimistic lock) → 404
  2. Guard: order.status == QUOTED → else 409
  3. Guard: order.tokenRenewalCount < 3 → else 422 "All token slots exhausted"
  4. Generate token:
       rawToken  = Base64.getUrlEncoder().withoutPadding()
                       .encodeToString(SecureRandom.getInstanceStrong().generateSeed(32))
       tokenHash = DigestUtils.sha256Hex(rawToken)
  5. renewalSequence = order.tokenRenewalCount + 1
  6. Create QuoteToken {
       id = UUID.randomUUID(),
       repairOrder     = order,
       tokenHash,
       createdAt       = Instant.now(),
       expiresAt       = Instant.now().plus(48, HOURS),
       status          = ACTIVE,
       renewalSequence,
       issuedBy        = principal
     }
  7. order.tokenRenewalCount++
  8. order.transitionTo(APPROVED)
  9. tokenRepository.save(token)
  10. orderRepository.save(order)
  11. Return QuoteTokenResponse including rawToken

Response:  200 OK
Body:      { "orderId": UUID, "rawToken": string, "expiresAt": instant, "renewalSequence": int }
           ⚠ rawToken appears ONLY in this response; it is never stored and never returned again.
Errors:    404 | 409 wrong status | 409 OptimisticLockException | 422 slots exhausted
```

---

### O-12 — Shop Manager Rejects Quote

```
POST /api/orders/{id}/reject
Auth: [AUTH] SHOP_MANAGER

Steps:
  1. Load order (with optimistic lock) → 404
  2. Guard: order.status == QUOTED → else 409
  3. order.transitionTo(REJECTED)
  4. orderRepository.save(order)

Response:  200 OK
Body:      { "id": UUID, "status": "REJECTED" }
Errors:    404 | 409 wrong status
```

---

### O-13 — Renew Quote Token

```
POST /api/orders/{id}/renew-token
Auth: [AUTH] SHOP_MANAGER

Steps:
  1. Load order (with optimistic lock) → 404
  2. Guard: order.status == APPROVED → else 409
  3. Guard: order.tokenRenewalCount < 3 → else 422 "Maximum renewals reached (3/3)"
  4. Find existing token where repairOrder=order and status=ACTIVE
     If found: mark it INVALIDATED, set usedAt=Instant.now(), save it
  5. Generate rawToken + tokenHash (same as O-11 step 4)
  6. renewalSequence = order.tokenRenewalCount + 1
  7. Create new QuoteToken {
       status=ACTIVE, expiresAt=now+48h, renewalSequence, issuedBy=principal
     }
  8. order.tokenRenewalCount++
  9. tokenRepository.save(newToken)
  10. orderRepository.save(order)
  11. Return QuoteTokenResponse

Response:  200 OK
Body:      { "orderId": UUID, "rawToken": string, "expiresAt": instant, "renewalSequence": int }
Errors:    404 | 409 wrong status | 409 OptimisticLockException | 422 renewals exhausted
```

---

### O-14 — Complete Order

```
POST /api/orders/{id}/complete
Auth: [AUTH] SHOP_MANAGER only   ← (D-05: only SHOP_MANAGER triggers COMPLETED)

Steps:
  1. Load order → 404
  2. Guard: order.status == IN_PROGRESS → else 409
  3. order.transitionTo(COMPLETED)
  4. orderRepository.save(order)

Response:  200 OK
Body:      { "id": UUID, "status": "COMPLETED" }
Errors:    404 | 409 wrong status | 403 MECHANIC cannot call
```

---

### O-15 — View Public Quote

```
GET /public/quotes/{token}
Auth: [PUBLIC]

Path param: token = raw URL-safe Base64 token (256 bits)

Steps:
  1. tokenHash = DigestUtils.sha256Hex(token)
  2. Load quoteToken = tokenRepository.findByTokenHash(tokenHash) → 404 if absent
  3. Inline expiry check: if quoteToken.expiresAt <= Instant.now() → 410 "Quote link has expired"
  4. Guard: quoteToken.status == ACTIVE
       USED or INVALIDATED → 409 "This quote link has already been used"
       EXPIRED              → 410 "Quote link has expired"
  5. Load order = quoteToken.repairOrder (with customer, motorcycle, lineItems eager)
  6. Guard: order.status == APPROVED → 409 if IN_PROGRESS, REJECTED, etc.
  7. Return QuoteViewResponse

Response:  200 OK
Body:      {
             "orderId": UUID,
             "customer":   { "fullName": string, "phone": string },
             "motorcycle": { "licensePlate": string, "make": string, "model": string, "year": int },
             "lineItems":  [ { "type": string, "description": string,
                               "quantity": decimal, "unitPrice": decimal, "subtotal": decimal } ],
             "totalAmount": decimal,
             "expiresAt":   instant,
             "quoteStatus": "PENDING_CUSTOMER_DECISION"
           }
Errors:    404 token not found | 409 token already used/invalidated | 410 token expired
```

---

### O-16 — Customer Approves Quote

```
POST /public/quotes/{token}/approve
Auth: [PUBLIC]

Steps:
  1. tokenHash = DigestUtils.sha256Hex(token)
  2. Atomic CAS update:
       rowsUpdated = tokenRepository.markUsed(tokenHash)
         // UPDATE quote_tokens
         //    SET status = 'USED', used_at = NOW()
         //  WHERE token_hash = :tokenHash
         //    AND status = 'ACTIVE'
         //    AND expires_at > NOW()
  3. If rowsUpdated == 0 → 409 "Token already used, expired, or not found"
  4. Load quoteToken (fresh read after CAS)
  5. Load order (with optimistic lock) = quoteToken.repairOrder
  6. Guard: order.status == APPROVED → 409 if not (defensive; should never fire after CAS)
  7. order.transitionTo(IN_PROGRESS)
  8. orderRepository.save(order)
  9. Return confirmation

Response:  200 OK
Body:      { "message": "Quote approved. Your repair is now in progress." }
Errors:    409 token conflict | 409 order state conflict
```

---

### O-17 — Customer Rejects Quote

```
POST /public/quotes/{token}/reject
Auth: [PUBLIC]

Steps:
  1. tokenHash = DigestUtils.sha256Hex(token)
  2. Atomic CAS update:
       rowsUpdated = tokenRepository.markInvalidated(tokenHash)
         // UPDATE quote_tokens
         //    SET status = 'INVALIDATED', used_at = NOW()
         //  WHERE token_hash = :tokenHash
         //    AND status = 'ACTIVE'
         //    AND expires_at > NOW()
  3. If rowsUpdated == 0 → 409 "Token already used, expired, or not found"
  4. Load quoteToken (fresh read after CAS)
  5. Load order (with optimistic lock) = quoteToken.repairOrder
  6. Guard: order.status == APPROVED → 409 if not
  7. order.transitionTo(REJECTED)
  8. orderRepository.save(order)

Response:  200 OK
Body:      { "message": "Quote rejected." }
Errors:    409
```

---

### O-18 — Token Expiry Scheduled Job *(not an HTTP endpoint)*

```
@Scheduled(fixedDelay = 5, timeUnit = MINUTES)
TokenExpiryJob.expireTokensAndCancelOrders():

  1. expiredTokens = tokenRepository.findAllActiveExpiredBefore(Instant.now())
     // SELECT * FROM quote_tokens WHERE status = 'ACTIVE' AND expires_at < NOW()

  2. cancelledCount = 0, expiredCount = 0

  3. For each token in expiredTokens:
     a. token.status = EXPIRED
     b. Load order = token.repairOrder (with optimistic lock)
     c. If order.tokenRenewalCount >= 3 and order.status == APPROVED:
            order.transitionTo(CANCELLED)
            orderRepository.save(order)
            cancelledCount++
     d. tokenRepository.save(token)
     e. expiredCount++

  4. log.info("TokenExpiryJob: {} tokens expired, {} orders cancelled", expiredCount, cancelledCount)
```

---

## N — Norms

### Naming Conventions

| Artifact | Convention | Example |
|---|---|---|
| Classes | UpperCamelCase | `RepairOrderService`, `QuoteTokenResponse` |
| Methods | lowerCamelCase | `generateQuote`, `markUsed` |
| Constants / Enums | UPPER_SNAKE | `IN_PROGRESS`, `SHOP_MANAGER` |
| DB tables | snake_case plural | `repair_orders`, `quote_tokens`, `app_users` |
| DB columns | snake_case | `problem_description`, `token_hash`, `expires_at` |
| REST paths | kebab-case nouns | `/api/orders`, `/api/line-items`, `/public/quotes` |
| DTOs | Noun + `Request` or `Response` | `CreateOrderRequest`, `OrderDetailResponse` |
| Exceptions | Noun + `Exception` | `IllegalTransitionException`, `TokenException` |
| Test methods | `methodName_condition_expectedOutcome` | `generateQuote_checklistIncomplete_returns422` |

### Validation Rules

| Field | Rule |
|---|---|
| `problemDescription` | `@NotBlank`, max 1000 chars |
| `Customer.fullName` | `@NotBlank`, max 200 chars |
| `Customer.phone` | `@NotBlank`, max 20 chars |
| `Motorcycle.licensePlate` | `@NotBlank`, max 15 chars; normalized to uppercase before save |
| `Motorcycle.year` | `@Min(1900)`, `@Max(currentYear + 1)` |
| `LineItem.description` | `@NotBlank`, max 500 chars |
| `LineItem.quantity` | `@DecimalMin("0.01")` |
| `LineItem.unitPrice` | `@DecimalMin("0.00")` |

Use `@Validated` on controllers and `@Valid` on `@RequestBody` parameters.  
Return `400` with body `{ "errors": [ { "field": string, "message": string } ] }` for each violation.

### Logging

- Use SLF4J with Logback; never `System.out.println`
- Profiles: `INFO` in production; `DEBUG` in `application-dev.yml`
- Log every state transition: `log.info("Order {} transitioned {} → {}", id, from, to)`
- Log every token lifecycle event: `log.info("Token {} for order {}: {}", tokenId, orderId, event)` where `event` ∈ {ISSUED, USED, REJECTED, EXPIRED, INVALIDATED, RENEWED}
- Log scheduled job summary at `INFO`: `log.info("TokenExpiryJob: {} expired, {} cancelled")`
- **Never log** raw tokens, passwords, or personal data (phone, email, full name)
- Log unexpected exceptions at `ERROR` with full stack trace

### Error Handling

All handled in `GlobalExceptionHandler` (`@RestControllerAdvice`):

| Exception | HTTP Status | Response body |
|---|---|---|
| `ResourceNotFoundException` | `404` | `{ "error": "NOT_FOUND", "message": string }` |
| `IllegalTransitionException` | `409` | `{ "error": "INVALID_TRANSITION", "from": string, "to": string }` |
| `TokenException` (used/invalidated) | `409` | `{ "error": "TOKEN_INVALID", "reason": string }` |
| `TokenException` (expired) | `410` | `{ "error": "TOKEN_EXPIRED", "reason": string }` |
| `DomainException` | `422` | `{ "error": "DOMAIN_RULE_VIOLATION", "message": string }` |
| `MethodArgumentNotValidException` | `400` | `{ "errors": [ { "field", "message" } ] }` |
| `OptimisticLockException` | `409` | `{ "error": "CONCURRENT_MODIFICATION", "message": "Retry the request." }` |
| `AccessDeniedException` | `403` | `{ "error": "FORBIDDEN" }` |
| `Exception` (unexpected) | `500` | `{ "error": "INTERNAL_ERROR" }` — full stack in log |

### Test Standards

| Layer | Framework | Target |
|---|---|---|
| Domain services (unit) | JUnit 5 + Mockito | 100% of business rules (BR-01 through BR-11, IBR-01 through IBR-08) |
| Persistence | `@DataJpaTest` + Testcontainers PostgreSQL | All custom repository queries including the partial-unique-index constraint |
| API integration | `@SpringBootTest` + `MockMvc` | Every endpoint: happy path + primary error codes |
| Concurrency | Testcontainers + `ExecutorService` (2 threads) | O-16 and O-17: only one concurrent approval request succeeds |

Test class per use case, not per method (e.g., `GenerateQuoteUseCaseTest`).

---

## S — Safeguards

Non-negotiable constraints. Any PR that violates these must be rejected at review.

| # | Safeguard | How Enforced |
|---|---|---|
| **SG-01** | **Raw token never stored.** `QuoteToken` has no `rawToken` field. Only `tokenHash` (SHA-256) is persisted. `rawToken` appears once in O-11 / O-13 response and never again. | Code review: `grep -r rawToken src/main/java` must return no hits in persistence layer. |
| **SG-02** | **State transitions only via `RepairOrder.transitionTo()`.** `status` field has package-private setter. All callers must go through `transitionTo()`, which calls `OrderStatus.canTransitionTo()`. An `@PreUpdate` hook validates the final status is reachable. | No `repairOrder.setStatus()` call outside `RepairOrder.java`. `@Modifying @Query` targeting `repair_orders.status` is forbidden. |
| **SG-03** | **Token single-use enforced by atomic CAS.** `rowsAffected == 0` → 409/410 without loading the order. Concurrent approval race test must show only one request succeeds. | Integration test: two threads POST approve simultaneously; assert exactly one 200 and one 409. |
| **SG-04** | **Line items are immutable beyond the APPROVED state.** Once order status is `IN_PROGRESS`, `COMPLETED`, `REJECTED`, or `CANCELLED`, no line item can be added, removed, or updated. Service checks `isTerminal()` first. | Unit test: `addLineItem_orderTerminal_returns409`. |
| **SG-05** | **`/public/**` requires no auth; `/api/**` requires a valid JWT.** These two rules are mutually exclusive. `SecurityConfig` must explicitly permit the former and protect the latter. | `SecurityConfigTest` asserts anonymous access to `/public/**` returns 2xx, and to `/api/**` returns 401. |
| **SG-06** | **`tokenRenewalCount` is monotonically increasing and capped at 3.** No code path decrements it. DB migration adds `CHECK (token_renewal_count BETWEEN 0 AND 3)`. | Review: no decrement operator on `tokenRenewalCount`. DB constraint in `V3__check_constraint_renewal_count.sql`. |
| **SG-07** | **Optimistic locking on all `RepairOrder` writes.** `@Version` is required. No native SQL UPDATE against `repair_orders.status` is permitted (the only native update allowed is the CAS on `quote_tokens`). | Code review: `JpaOrderRepository` has no `@Modifying @Query` touching `status`. |
| **SG-08** | **Motorcycle–customer consistency.** `motorcycle.customer.id` must equal `repairOrder.customer.id` at order creation. No FK enforces this; the service guard is the only protection. | Unit test: `createOrder_motorcycleCustomerMismatch_returns422`. |
| **SG-09** | **Terminal-state orders are immutable.** `OrderStatus.isTerminal()` is checked as the **first guard** in every service method that modifies an order. All further operations on `COMPLETED`, `REJECTED`, or `CANCELLED` orders return `409` immediately. | Unit test per service method: `*_terminalOrder_returns409`. |
