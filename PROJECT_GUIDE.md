# E-Commerce Spring Boot Router

This repository is an e-commerce catalogue and order API router. It persists catalogue data in MySQL, protects write operations with JWT-based buyer and seller roles, and routes autocomplete work to separate in-memory trie-shard processes.

Read this guide from top to bottom to understand the project at its current point in development.

## 1. System at a glance

```text
HTTP client
   |
   +--> Spring Security filter chain
   |       +--> JWT request filter (when an Authorization header is supplied)
   |
   +--> Controllers
   |       +--> Facade service ---> Product service ---> Products repository ---> MySQL product table
   |       |                    +-> Order service -----> Order repository -----> MySQL orders table
   |       |
   |       +--> Login controller ---> Authentication manager ---> in-memory users
   |
   +--> Global exception handler
```

The entry point is `ECommerceApplication`. It enables normal Spring Boot auto-configuration and scheduled jobs.

## 2. What the application currently offers

### Catalogue

Products use a generated immutable numeric `id` as the primary key. Their `name` is a required, unique business field and can be changed without changing their identity. Products also have `price`, `stock`, `rating`, `approxRating`, and JPA optimistic-lock `version` fields.

- Sellers can create products (automatically recorded in `seller_products`).
- Owning sellers can upload product photos (`/products/{productId}/photos`), which synchronously generates low-resolution thumbnails (max 150px) on disk and stores normalized file paths in `product_photos`.
- Anyone can view product details (`/view/product/{name}`), which returns full metadata, tags, and the high-resolution photo inlined directly as Base64 (`highResImage`).
- Anyone can page through products (`/search/all`, `/search/trie/{prefix}`), which includes lightweight low-resolution thumbnails inlined directly as Base64 (`lowResImage`).
- Duplicate names return HTTP 409 through the global exception handler.

### Orders

Buyers can place an order for a product name. The request currently decrements the product stock and inserts an `Order` row linked to that product. The response is HTTP 200 with no body.

### Authentication and authorization

Login authenticates against three in-memory accounts and returns a JWT valid for one hour. The token carries the username and roles.

| User | Password | Role | Primary capability |
| --- | --- | --- | --- |
| `Aryan` | `pass123` | `buyer` | Place orders |
| `Aditya` | `pass123` | `seller` | Add products and upload photos |
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
  -> PhotoService links seller to product in seller_products
  -> 201 Created, with Location: /view/product/{name}
```

Request body:

```json
{ "name": "Keyboard", "price": 2500, "stock": 10 }
```

### Upload a product photo

```text
POST /products/{productId}/photos
  -> Security requires ROLE_seller
  -> PhotoController
  -> PhotoService verifies seller ownership in seller_products
  -> Saves high-res photo to disk (./data/images/high_res/{uuid}.jpg)
  -> Synchronously generates low-res thumbnail (max 150px) to disk (./data/images/low_res/{uuid}.jpg)
  -> Inserts normalized record in product_photos (low_res_path, high_res_path, is_main)
  -> 201 Created
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
| `GET` | `/view/product/{name}` | Public | Get product details, tags, and inlined Base64 high-resolution photo (`highResImage`) |
| `POST` | `/add/product` | Seller | Create a product and link seller ownership |
| `POST` | `/products/{productId}/photos` | Seller | Upload product photo (multipart file, `isMain`); seller ownership required |
| `POST` | `/order/product/{name}` | Buyer | Decrement stock and create an order |
| `POST` | `/tags/{productId}/add?tag={name}` | Seller | Add a single tag to a product |
| `POST` | `/tags/{productId}/upload` | Seller | Batch upload tags to a product |
| `GET` | `/tags/{productId}` | Seller | List tags for a product |
| `GET` | `/search/all?page=0&size=20` | Public | Paginated product listing with inlined Base64 low-resolution thumbnails (`lowResImage`) |
| `GET` | `/search/trie/{prefix}?page=0&size=20` | Public | Prefix search with inlined Base64 low-resolution thumbnails (`lowResImage`) |
| `GET` | `/search/autocomplete/{prefix}` | Public | 4-Tier unified search (exact/fuzzy prefix, exact/fuzzy tags) |
| `GET` | `/test/200` | Public | Basic controller smoke endpoint |

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
│   ├── PhotoController.java              seller product photo upload endpoint
│   ├── TagController.java                tag creation and batch upload endpoints
│   ├── LoginController.java              JWT login endpoint
│   └── TestController.java               basic test endpoint
├── Service/                              business operations
│   ├── FacadeService.java                coordinates product and order services
│   ├── ProductService.java               product persistence, stock updates, pagination
│   ├── PhotoService.java                 image resize, disk storage, Base64 retrieval
│   ├── TagService.java                   tag management and relational division search
│   └── OrderService.java                 order creation
├── Model/                                JPA entities: Product, Order, Tag, SellerProduct, ProductPhoto
├── Repository/                           Spring Data JPA access
│   ├── ProductsRepository.java
│   ├── OrderRepository.java
│   ├── TagRepository.java
│   ├── SellerProductRepository.java
│   └── ProductPhotoRepository.java
├── Configuration/Security/               JWT, filter-chain, and in-memory users
├── RequestModels/                        DTOs: ProductDetailDTO, ProductPageItemDTO, TagRequest
├── Projections/                          name-only product search response
├── Exception/                            product conflict exception
├── Autocomplete/                         router and trie-shard components
├── DatabaseClearer/                      scheduled-downtime prototype
└── Later/                                commented-out future payment/notification stubs
```

`FacadeController` delegates cross-service workflows to `FacadeService`, `ProductService`, `TagService`, and `PhotoService`. When browsing products via `/search/all` or `/search/trie/{prefix}`, the controller fetches low-resolution thumbnail bytes from disk and encodes them directly to Base64 in `ProductPageItemDTO`, allowing instant client-side rendering without secondary image requests. Similarly, `/view/product/{name}` delivers tags and the full high-resolution image inlined as Base64 in `ProductDetailDTO`.

### Product Photos & Disk Storage
Photo files are persisted on disk under `./data/images/high_res/` and `./data/images/low_res/`. Thumbnails are generated synchronously on upload, scaling proportionally to a max dimension of 150px using bilinear interpolation. The SQL table `product_photos` stores normalized metadata (`id`, `product_id`, `low_res_path`, `high_res_path`, `is_main`, `created_at`). Seller ownership is verified against `seller_products` before any photo upload is accepted.

### Autocomplete

The autocomplete endpoint routes to a separate in-memory trie shard based on
the first letter of the normalized prefix. Each shard records sampled prefix
frequency; only its weekly selected top 10,000 nodes hold up to eight product
suggestions. Product additions also immediately register product suggestions along
their prefix path.

The search bar endpoint (`/search/autocomplete/{prefix}`) runs a 4-tier search pipeline:
1. **Exact prefix search** on product names (distance 0).
2. **Fuzzy prefix search** on product names using Damerau-Levenshtein distance (distance 1 for query length 5–8, distance 2 for length $\ge$ 9; handles adjacent transpositions like `iphnoe` -> `iphone`).
3. **Exact meta-tag search** using relational division SQL (`WHERE t.name IN :tags HAVING COUNT(DISTINCT t.name) = :count ORDER BY p.approxRating DESC`).
4. **Fuzzy meta-tag search** allowing Damerau-Levenshtein distance $\le$ 1 on tag names.

### Dual Rating & Binned Indexing
To prevent database B-tree index rebalancing on every review decimal change:
- `rating`: continuous exact average rating.
- `approxRating`: discrete rating rounded to the nearest multiple of 0.5 in range [0.0, 5.0] (11 buckets).
- An index is placed on `approx_rating`. All search queries sort by `ORDER BY p.approxRating DESC, p.name ASC`.

The full design, shard startup commands, and algorithms are in [`docs/search-autocomplete-design.md`](docs/search-autocomplete-design.md).

The router also serves a no-build frontend at `/`. It waits 250 ms after the
latest keystroke, cancels the prior request when possible, and uses a strictly
increasing client-side version to ignore an older response that arrives late.


## 6. Data and configuration

`src/main/resources/application.properties` configures a MySQL database with defaults connecting to `jdbc:mysql://localhost:3306/ecommerce`. Connection parameters can be overridden via `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, and `MYSQL_PASSWORD` environment variables. Hibernate uses `ddl-auto=update`, automatically creating or updating tables based on entity definitions.

For automated test suites and continuous integration, `src/test/resources/application.properties` provides an isolated in-memory H2 database profile.

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
