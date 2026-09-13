# Sharded in-memory autocomplete system

## Current implementation

The catalogue/API application is a router. The trie runs in six separate
trie-shard processes. The router selects a shard from the first normalized
letter of the prefix. Node failure, replication, rebalancing, and movement of
data are deliberately not handled in this version.

Trie nodes are not stored in a database. At router startup, it sends every
catalogue product (`productId`, `name`) to the owning trie shard. That shard
builds one in-memory node for every product-name prefix. Every node holds:

```text
prefix
estimated search frequency
children
```

Only the 10,000 highest-frequency nodes selected by the weekly job additionally
hold up to eight product suggestions (product ID and product name). For
example, adding the product `Keyboard` creates the nodes `k`, `ke`, `key`, and
so on, but does not populate a suggestion list for every one of them. Newly
created products are also inserted into the trie immediately. The autocomplete
read path is then entirely in memory:

```text
GET /search/autocomplete/{prefix}
  -> normalize prefix
  -> trie lookup
  -> return the node's up to eight suggestions
```

Trie state and frequency data are intentionally lost when a shard restarts. A
router restart re-indexes the catalogue into the running shards. No `trie_node`
table or `prefix_suggestion` table exists in this version.

## Search popularity and weekly refresh

The server samples one request out of every 1,000 search requests.  A sampled
search adds `1,000` to every trie node on its prefix path; this keeps the count
as an estimated count without a write for every request.

Each Sunday at midnight, the router asks each shard for its top prefixes. Each
shard performs a best-first traversal locally: it seeds a **max-priority
queue** with root children, removes the most frequent prefix, adds that node's
children, and stops after 10,000 prefixes. A child never has a frequency
greater than its parent.

For these selected popular nodes only, the router runs the current `LIKE
prefix%` product query, then sends its up-to-eight-product list to the owning
shard. That query is deliberately replaceable later if product rating,
relevance, stock, or another ranking rule is introduced.

## Fixed shards

```text
A: a
S: s
CP: c, p
BMT: b, m, t
COMMON: d, e, f, g, h, i, j, k, l, n, o, r, u, v, w
RARE: q, x, y, z, digits, symbols
```

This is an assumed English-like distribution, not one measured from the
catalogue. Start all six shards before the router. Example for shard A:

```bash
java -jar target/ecommerce-router-0.0.1-SNAPSHOT.jar \
  --server.port=8091 --app.role=trie-shard --app.trie-shard=A \
  --spring.datasource.url=jdbc:h2:mem:trie-a
```

Repeat with `S`, `CP`, `BMT`, `COMMON`, and `RARE` on ports 8092 through 8096.
Then start the router with `--autocomplete.router.bootstrap-enabled=true`; it
will send the catalogue to the shards and build the first top-prefix snapshot.

## Later work

- Persist trie frequencies and/or prefix-to-product lists only when the memory
  version has been measured.
- Add a shared cache if one process no longer has enough memory.
- Use stream processing for daily/trending results.
- Let the frontend keep a small browser-side, user-specific recent-search list.
  It should remain separate from the global trie ranking.
