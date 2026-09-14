# E-Commerce Router with Sharded Autocomplete

Spring Boot e-commerce API for products and orders, with JWT authorization and
a distributed, in-memory autocomplete index.

The main application remains the catalogue and order API. It also acts as the
autocomplete router: it sends a prefix to one trie-shard process based on the
first normalized character.

## Features

- Product creation, lookup, prefix search, and orders.
- JWT-based buyer and seller authorization.
- MySQL-backed catalogue and order data (configurable via environment variables).
- Optimistic locking on products.
- In-memory autocomplete trie split into fixed first-letter shards.
- Damerau-Levenshtein fuzzy prefix search with adjacent character swap support (`iphnoe` → `iphone`).
- Dynamic DP distance-row trie traversal with early branch pruning.
- Adaptive typo tolerance thresholds: exact-first, distance 1 for query length $\ge$ 5, distance 2 for query length $\ge$ 9.
- Product meta-tagging with relational division search (`tags` table indexed on `(name, product_id)`).
- 4-Tier unified search fallback: Exact Name Prefix $\rightarrow$ Fuzzy Name Prefix $\rightarrow$ Exact Tags $\rightarrow$ Fuzzy Tags (distance $\le$ 1).
- Dual-rating binning: continuous `rating` and indexed discrete `approxRating` (0.0–5.0 in 0.5 steps) to minimize B-tree index churn.
- Seller-only single and batch tag management (`/tags/**`).
- 1-in-1,000 server-side search sampling for estimated prefix popularity.
- Weekly best-first/max-heap selection of the top 10,000 prefixes per shard.
- Up to eight product suggestions stored only for selected popular trie nodes.
- Static frontend at `/`: 250 ms debounce, request cancellation, and version
  checks to prevent late responses overwriting current suggestions.

## Architecture

```text
Browser
  -> E-commerce router :8080
       -> catalogue/orders in MySQL
       -> trie shard selected by prefix first letter

Trie shards :8091 .. :8096
  -> in-memory prefix trie
  -> sampled frequency counts
  -> suggestions for top prefix nodes
```

Fixed shard assignment:

| Shard | Prefix roots | Default port |
| --- | --- | --- |
| `A` | `a` | 8091 |
| `S` | `s` | 8092 |
| `CP` | `c`, `p` | 8093 |
| `BMT` | `b`, `m`, `t` | 8094 |
| `COMMON` | `d`–`w` selected common roots | 8095 |
| `RARE` | `q`, `x`, `y`, `z`, digits, symbols | 8096 |

This is a learning implementation. It deliberately does not implement shard
replicas, node-failure recovery, persistence of trie data, or rebalancing.

## Run locally

Requirements: Java 17 and a running MySQL instance (default: `localhost:3306/ecommerce`, credentials `root`/`root`).

Start MySQL with Docker (optional):

```bash
docker run -d --name ecommerce-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=ecommerce mysql:8
```

You can customize the connection using environment variables: `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`.

Build the project:

```bash
sh ./mvnw -DskipTests package
```

Start all trie shards first. For example, shard `A`:

```bash
java -jar target/ecommerce-router-0.0.1-SNAPSHOT.jar \
  --server.port=8091 \
  --app.role=trie-shard \
  --app.trie-shard=A
```

Start the other shards with ports 8092–8096 and shard names `S`, `CP`, `BMT`,
`COMMON`, and `RARE`.

Then start the router, which indexes its catalogue into the running shards:

```bash
java -jar target/ecommerce-router-0.0.1-SNAPSHOT.jar \
  --autocomplete.router.bootstrap-enabled=true
```

Open [http://localhost:8080/](http://localhost:8080/) for the search UI.

## Frontend files

The frontend is served directly by Spring Boot from `src/main/resources/static/`:

- [index.html](src/main/resources/static/index.html) — search page markup.
- [app.js](src/main/resources/static/app.js) — 250 ms debounce, request
  cancellation, and version checks for stale autocomplete responses.
- [app.css](src/main/resources/static/app.css) — search and dropdown styling.

There is no separate Node/React frontend project or build step.

## Main endpoints

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/sign/` | Log in and receive a JWT |
| `POST` | `/add/product` | Add a product; seller role required |
| `GET` | `/view/product/{name}` | View a product |
| `POST` | `/order/product/{name}` | Place an order; buyer role required |
| `POST` | `/tags/{productId}/add?tag={name}` | Add a single tag to a product; seller role required |
| `POST` | `/tags/{productId}/upload` | Batch upload multiple tags to a product; seller role required |
| `GET` | `/tags/{productId}` | List tags for a product |
| `GET` | `/search/trie/{prefix}` | Catalogue prefix search |
| `GET` | `/search/autocomplete/{prefix}` | 4-Tier unified search (exact/fuzzy prefix, exact/fuzzy tags) |

For the detailed autocomplete design and future work, see
[docs/search-autocomplete-design.md](docs/search-autocomplete-design.md).
