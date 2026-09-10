# Shop & Inventory Modular Monolith (IT342)

## 1. Supabase Setup Steps

1. **Database Provisioning:**
   * Created a Postgres database instance on Supabase hosted in region `ap-northeast-2`.
   * Executed DDL scripts via the Supabase SQL Editor to construct two core domain tables:
     * `inventory` (`product_id` [PK], `name`, `stock`)
     * `orders` (`order_id` [PK], `product_id` [FK], `quantity`, `status`, `reason`, `created_at`)
   * Seeded initial product data (`P100`, `P200`, `P300`) with initial stock levels using an `UPSERT` statement.

2. **Network Connection Configuration:**
   * Configured `application.properties` to connect using Supabase's **Session Pooler** (`aws-0-ap-northeast-2.pooler.supabase.com:5432`) to ensure compatibility across IPv4 networks.
   * Passed connection parameters (`postgres.okanezqedfcarinbqnlg`) and managed sensitive database passwords via environment variables.

---

## 2. Network Tab Evidence

*Replace the image placeholders below with your actual screenshots in your repository:*

### Confirmed Order Scenario
* **Action:** Order placed for `P100 Wireless Mouse` (Quantity: 2).
* **HTTP Method & Endpoint:** `POST http://localhost:8080/api/orders`
* **Response Status:** `CONFIRMED`
* **Result Payload:** Remaining Stock updated to `23`.
* ![Confirmed Order Screenshot](./docs/confirmed-order.png)

### Rejected Order Scenario
* **Action:** Order placed for `P300 USB-C Hub` (Quantity: 1, Stock: 0).
* **HTTP Method & Endpoint:** `POST http://localhost:8080/api/orders`
* **Response Status:** `REJECTED`
* **Result Payload:** Reason returned as `Insufficient stock`.
* ![Rejected Order Screenshot](./docs/rejected-order.png)

---

## 3. Architecture & Design Reflection

### 1. In-Process Integration vs. Network-Separated Microservices
In an in-process modular monolith, the `shop` and `inventory` modules share a single runtime (JVM), memory space, and database connection pool. Communicating in-process provides **ACID transaction guarantees for free**—a single `@Transactional` annotation handles checking stock, reserving inventory, and recording the order atomically. It also eliminates network latency, serialization overhead, and partial network failures. 

If this application were split into separate microservices over HTTP or messaging, we would lose these out-of-the-box guarantees. To add them back, we would need to implement **distributed transaction patterns** (such as the Saga pattern with compensating actions) or transactional outbox patterns to handle failure states. Additionally, we would need network resilience mechanisms like retry logic, circuit breakers, rate limiting, and API gateways for service discovery and security.

### 2. Package-Private Visibility on `InventoryServiceImpl`
Keeping `InventoryServiceImpl` package-private strictly enforces module encapsulation at compile time. By omitting the `public` modifier, classes outside the `edu.cit.nunez.inventory` package (such as `OrderService` in `edu.cit.nunez.shop`) are physically blocked by the Java compiler from referencing or instantiating `InventoryServiceImpl` directly. Instead, external modules must depend exclusively on the exported `InventoryService` interface.

If `InventoryServiceImpl` were made `public`, developers could bypass interface abstraction and bind tight couplings directly to internal implementation details. This breaks module boundaries, encourages unintended side effects (like directly mutating internal helper methods), and makes future refactoring or replacing the inventory implementation significantly harder without breaking client code.

### 3. Triggers for Microservice Extraction & Necessary Code Changes
`Inventory` should be extracted into an independent microservice when domain scaling requirements diverge—for example, if high-frequency warehouse scans or third-party stock synchronization overload the shared database, or if an independent team needs to deploy inventory updates without risking the order checkout pipeline.

To extract `Inventory` into its own microservice, the following code changes are required:
* **Replace Direct Service Calls:** In `OrderService`, remove constructor injection of the Java `InventoryService` interface and replace it with an external REST client (e.g., Spring `RestClient`, `WebClient`, or `FeignClient`) or asynchronous message listeners (e.g., RabbitMQ / Kafka).
* **Decouple Entities & Data Access:** Remove direct references to `edu.cit.nunez.inventory.Inventory` from the shop module's DTOs/Entities and introduce internal DTOs (`InventoryResponse`) within the `shop` module.
* **Database Separation:** Split the Supabase database into two separate databases or schemas so that `shop` can no longer perform direct SQL table joins on `inventory`.
