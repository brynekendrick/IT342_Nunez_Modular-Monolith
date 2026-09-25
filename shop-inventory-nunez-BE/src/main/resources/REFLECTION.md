# Lab 3 Reflection - LegacySupply Integration

## 1. Session Lifetime & Invalidations
* **Observed Behavior:** LegacySupply session tokens are short-lived, stateful credentials that automatically expire after inactivity or remote server restarts.
* **Handling Strategy:** The adapter handles token expiration gracefully by catching `HTTP 401 Unauthorized` and `E-AUTH` error codes. When caught, `sessionToken` is cleared (`sessionToken = null`). On the subsequent API request or scheduled polling cycle, the adapter automatically calls `authenticate()` to acquire a fresh token without interrupting core application workflows.

## 2. Unit of Measure (UOM) & Case Math
* **Case Order Math:** Customer unit demands are converted into supplier case quantities using ceiling division:
  $$\text{Cases to Order} = \left\lceil \frac{\text{Units Needed}}{\text{Pack Size}} \right\rceil$$
  For example, requesting 15 units of a product with a pack size of 10 calculates $\lceil 15 / 10 \rceil = 2$ cases to order.
* **Restock Math:** When a purchase order is marked as `DELIVERED`, an internal `OrderDeliveredEvent` is published. The inventory domain listens for this event and increments stock by:
  $$\text{Units Restocked} = \text{Cases Delivered} \times \text{Pack Size}$$
  Receiving 2 cases of a 10-pack item adds 20 units back into current inventory.

## 3. Decoupling & Architecture Impact
* **SupplierGateway Abstraction:** Domain modules (`inventory` and `shop`) communicate strictly through the `SupplierGateway` Java interface rather than coupling directly to third-party HTTP endpoints.
* **Architectural Isolation:** Encapsulating LegacySupply's proprietary XML payloads, session management, and rate-limiting rules inside the supplier Anti-Corruption Layer (ACL) protects core business logic. Upgrading API contracts or switching supplier vendors only requires modifying the adapter implementation without altering domain code.