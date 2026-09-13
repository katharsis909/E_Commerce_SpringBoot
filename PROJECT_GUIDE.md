# E-Commerce Spring Boot Router

This repository is an e-commerce catalogue and order API router. It persists catalogue data in H2, protects write operations with JWT-based buyer and seller roles, and routes autocomplete work to separate in-memory trie-shard processes.

Read this guide from top to bottom to understand the project at its current point in development.

## 1. System at a glance

```text
HTTP client
   |
   +--> Spring Security filter chain
   |       +--> JWT request filter (when an Authorization header is supplied)
   |
   +--> Controllers
   |       +--> Facade service ---> Product service ---> Products repository ---> H2 product table
   |       |                    +-> Order service -----> Order repository -----> H2 orders table
   |       |
   |       +--> Login controller ---> Authentication manager ---> in-memory users
   |
   +--> Global exception handler
```

The entry point is `ECommerceApplication`. It enables normal Spring Boot auto-configuration and scheduled jobs.

## 2. What the application currently offers

### Catalogue

Products use a generated immutable numeric `id` as the primary key. Their `name` is a required, unique business field and can be changed without changing their identity. Products also have `price`, `stock`, and JPA optimistic-lock `version` fields.

- Sellers can create products.
- Anyone can view one product by name.
- Anyone can page through product names or search them case-insensitively by prefix.
- Duplicate names return HTTP 409 through the global exception handler.

### Orders

Buyers can place an order for a product name. The request currently decrements the product stock and inserts an `Order` row linked to that product. The response is HTTP 200 with no body.

### Authentication and authorization

Login authenticates against three in-memory accounts and returns a JWT valid for one hour. The token carries the username and roles.

| User | Password | Role | Primary capability |
| --- | --- | --- | --- |
| `Aryan` | `pass123` | `buyer` | Place orders |
| `Aditya` | `pass123` | `seller` | Add products |
| `DBA` | `pass123` | `admin` | No dedicated endpoint currently |

The token signing key and these credentials are development-only values hard-coded in source. They must be externalized before any non-local use.

## 3. Request flow

### Add a product

```text
POST /add/product
  -> Security requires ROLE_seller
  -> FacadeController
  -> FacadeService.addProduct
  -> ProductService checks name uniqueness and saves the product
  -> 201 Created, with Location: view/product/{name}
```

Request body:

```json
{ "name": "Keyboard", "price": 2500, "stock": 10 }
```

### Place an order

```text
POST /order/product/{name}
  -> Security requires ROLE_buyer
  -> FacadeService.placeOrder
  -> ProductService decrements stock
  -> OrderService persists an Order linked to the product
  -> 200 OK
```

`FacadeService.placeOrder` is the transaction boundary for this cross-service workflow. Its `@Transactional` annotation means the stock update and order insert succeed or fail together, preserving order consistency. Explicit handling for missing products and insufficient stock is still needed.

## 4. API reference

Unless noted, protected calls need `Authorization: Bearer <JWT>`.

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| `POST` | `/sign/` | Public | Authenticate and receive a JWT |
| `GET` | `/sign/test` | Public | Simple authentication-area smoke endpoint |
| `GET` | `/view/product/{name}` | Public | Get a product with HATEOAS `self` and `order` links |
| `POST` | `/add/product` | Seller | Create a product |
| `POST` | `/order/product/{name}` | Buyer | Decrement stock and create an order |
| `GET` | `/search/all?page=0&size=20` | Public | Page through product-name projections |
| `GET` | `/search/trie/{prefix}?page=0&size=20` | Public | Case-insensitive prefix search of product names |
| `GET` | `/search/autocomplete/{prefix}` | Public | Return up to eight in-memory product suggestions |
| `GET` | `/test/200` | Public | Basic controller smoke endpoint |
| `GET` | `/h2-console` | Public | H2 browser for local development |

Example login:

```bash
curl -X POST http://localhost:8080/sign/ \
  -H 'Content-Type: application/json' \
  -d '{"username":"Aditya","password":"pass123"}'
```

Use the returned token in subsequent calls:

```bash
curl -X POST http://localhost:8080/add/product \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <seller-token>' \
  -d '{"name":"Keyboard","price":2500,"stock":10}'
```

## 5. Code map

```text
src/main/java/com/microservices/ecommerce/
├── ECommerceApplication.java             application bootstrap and scheduling
├── Controller/                           HTTP endpoints
│   ├── FacadeController.java             product, search, and order endpoints
│   ├── LoginController.java              JWT login endpoint
│   └── TestController.java               basic test endpoint
├── Service/                              business operations
│   ├── FacadeService.java                coordinates product and order services
│   ├── ProductService.java               product persistence and stock updates
│   └── OrderService.java                 order creation
├── Model/                                JPA entities: Product and Order
├── Repository/                           Spring Data JPA access
├── Configuration/Security/               JWT, filter-chain, and in-memory users
├── RequestModels/                        login/product request shapes
├── Projections/                          name-only product search response
├── Exception/                            product conflict exception
├── Autocomplete/                         router and trie-shard components
├── DatabaseClearer/                      scheduled-downtime prototype
└── Later/                                commented-out future payment/notification stubs
```

`FacadeController` delegates cross-service workflows to `FacadeService`. The facade's key responsibility is to invoke both `ProductService` and `OrderService` within a single transaction—for example, decrementing stock and creating an order atomically. This keeps orchestration out of the controller. The `ProductJSON` request model exists but is not yet used; the add endpoint receives the JPA `Product` entity directly.

### Autocomplete

The autocomplete endpoint routes to a separate in-memory trie shard based on
the first letter of the normalized prefix. Each shard records sampled prefix
frequency; only its weekly selected top 10,000 nodes hold up to eight product
suggestions. The full design, shard startup commands, and deferred work are in
[`docs/search-autocomplete-design.md`](docs/search-autocomplete-design.md).

The router also serves a no-build frontend at `/`. It waits 250 ms after the
latest keystroke, cancels the prior request when possible, and uses a strictly
increasing client-side version to ignore an older response that arrives late.


## 6. Data and configuration

`src/main/resources/application.properties` configures a file-backed H2 database at `./data/testdb`. Hibernate uses `ddl-auto=update`, so it creates or adjusts tables based on the entities. The H2 console is enabled at `/h2-console`.

The data files under `data/` are local runtime state. They are not a schema migration mechanism.

## 7. Running and testing

Requirements: Java 17 and the Maven wrapper included in this repository.

```bash
./mvnw spring-boot:run
./mvnw test
```

The application normally listens on Spring Boot's default port, `8080`.

## 8. Current implementation notes

These are the most important items to resolve before treating the API as production-ready:

The following items have been resolved: `DowntimeFilter` now resides in `DatabaseClearer/DowntimeFilter.java` and calls `DowntimeLock.isDowntime()`; Maven explicitly configures Lombok annotation processing; and `Product` now has a generated immutable numeric ID rather than using its name as the primary key. `sh ./mvnw test` compiles successfully and passes the current test suite.

1. Validate product existence and available stock before creating an order.
2. The `SecurityFilterChain` has no final `anyRequest()` rule. Decide explicitly how unmatched routes should be handled.
3. Move JWT key material and user provisioning out of code; do not use the supplied demo accounts outside local development.
4. Add controller/service tests for authorization, duplicate products, missing products, zero stock, and ordering rollback.
5. Replace Hibernate schema auto-update with versioned migrations once the schema needs to be stable.

## 9. Suggested reading order for contributors

1. Start with `ECommerceApplication.java` and this guide.
2. Read `FacadeController.java` for public behavior.
3. Follow each controller call into `FacadeService`, `ProductService`, and `OrderService`.
4. Review `Product`, `Order`, and their repositories to understand persistence.
5. Read `SecurityConfig`, `LoginController`, `JWTRequestFilter`, and `JWTUtil` together to understand access control.
6. Review the implementation notes above before extending the scheduled downtime work.
