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
read path is entirely in memory:

```text
GET /search/autocomplete/{prefix}
  -> normalize prefix
  -> Tier 1: Exact prefix trie lookup (distance 0)
       -> If found: return suggestions immediately
  -> Tier 2: Fuzzy prefix trie lookup (Damerau-Levenshtein)
       -> If length < 5: skip fuzzy prefix
       -> If 5 <= length < 9: maxDistance = 1
       -> If length >= 9: maxDistance = 2
       -> Primary shard trie traversal (broadcasts if initial swap)
       -> If found: return suggestions
  -> Tier 3: Exact Meta-Tag Relational Division Search (SQL)
       -> Matches product tags via:
            WHERE t.name IN :tags HAVING COUNT(DISTINCT t.name) = :count
            ORDER BY p.approxRating DESC, p.name ASC
       -> If found: return suggestions
  -> Tier 4: Fuzzy Meta-Tag Search (Edit Distance 1)
       -> Finds candidate tags with Damerau-Levenshtein distance <= 1
       -> Executes relational division query on matched tags
       -> If found: return suggestions
  -> If all tiers empty: return 204 No Content
```

Trie state and frequency data are intentionally lost when a shard restarts. A
router restart re-indexes the catalogue into the running shards. No `trie_node`
table or `prefix_suggestion` table exists in this version.

## Damerau-Levenshtein Trie Traversal

Fuzzy search uses a dynamic programming row vector passed down the trie during depth-first search:
- **State carried**: `currentRow`, parent's `prevRow`, and grandparent's `prevPrevRow` plus edge character history (`currentChar`, `prevChar`).
- **Adjacent Transposition**: Detects adjacent character swaps `c == prevQueryChar && prevChar == queryChar` and costs them as 1 edit (`iphnoe` -> `iphone`), unlike standard Levenshtein which costs 2 edits.
- **Subtree Pruning**: At each node, if `min(currentRow) > maxDistance`, the entire subtree is pruned immediately because distance cannot decrease deeper down the branch.
- **Suggestion Ranking**: Suggestions are ranked by edit distance ascending (distance 1 before distance 2), followed by node popularity frequency descending, and product name ascending.

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
  --server.port=8091 --app.role=trie-shard --app.trie-shard=A
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
