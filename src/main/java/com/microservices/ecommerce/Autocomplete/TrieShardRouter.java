package com.microservices.ecommerce.Autocomplete;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "router", matchIfMissing = true)
public class TrieShardRouter {
    private final RestClient client = RestClient.create();
    private final String a, s, cp, bmt, common, rare;
    public TrieShardRouter(@Value("${app.trie-shards.a}") String a, @Value("${app.trie-shards.s}") String s,
                           @Value("${app.trie-shards.cp}") String cp, @Value("${app.trie-shards.bmt}") String bmt,
                           @Value("${app.trie-shards.common}") String common, @Value("${app.trie-shards.rare}") String rare) {
        this.a=a; this.s=s; this.cp=cp; this.bmt=bmt; this.common=common; this.rare=rare;
    }
    public void index(ProductIndexEntry product) {
        try {
            client.put().uri(url(product.name()) + "/internal/trie/products").body(product).retrieve().toBodilessEntity();
        } catch (Exception ignored) {}
    }

    public void record(String prefix) {
        try {
            client.post().uri(url(prefix) + "/internal/trie/record/{p}", prefix).retrieve().toBodilessEntity();
        } catch (Exception ignored) {}
    }

    public List<AutocompleteSuggestion> search(String prefix) {
        try {
            List<AutocompleteSuggestion> results = client.post().uri(url(prefix) + "/internal/trie/search/{p}", prefix).retrieve().body(new ParameterizedTypeReference<>() {});
            return results != null ? results : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<AutocompleteSuggestion> fuzzy(PrefixShard shard, String prefix, int maxDistance) {
        try {
            List<AutocompleteSuggestion> results = client.post()
                    .uri(url(shard) + "/internal/trie/fuzzy-search/{p}?maxDistance={d}", prefix, maxDistance)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return results != null ? results : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<AutocompleteSuggestion> searchWithFuzzyFallback(String rawPrefix) {
        // 1. Exact prefix search first: "iph" -> iphone...
        List<AutocompleteSuggestion> exactResults = search(rawPrefix);
        if (!exactResults.isEmpty()) {
            return exactResults;
        }

        // 2. If exact search returns nothing:
        String prefix = AutocompleteService.normalize(rawPrefix);
        int length = prefix.length();

        // - only if query length >= 5
        if (length < 5) {
            return List.of();
        }

        // - run fuzzy-prefix search with edit distance 1; allow distance 2 only for length >= 9
        int maxDistance = (length >= 9) ? 2 : 1;
        PrefixShard primaryShard = PrefixShard.forPrefix(prefix);

        List<AutocompleteSuggestion> fuzzyResults = fuzzy(primaryShard, prefix, maxDistance);
        if (!fuzzyResults.isEmpty()) {
            return fuzzyResults;
        }

        // Check remaining shards in case the initial character itself had a typo/swap
        for (PrefixShard otherShard : PrefixShard.values()) {
            if (otherShard == primaryShard) continue;
            List<AutocompleteSuggestion> otherResults = fuzzy(otherShard, prefix, maxDistance);
            if (!otherResults.isEmpty()) {
                return otherResults;
            }
        }

        return List.of();
    }

    public List<String> top(PrefixShard shard) { return client.get().uri(url(shard) + "/internal/trie/top-prefixes").retrieve().body(new ParameterizedTypeReference<>() {}); }
    public void suggestions(PrefixShard shard, List<PrefixSuggestionsUpdate> updates) { client.put().uri(url(shard) + "/internal/trie/suggestions").body(updates).retrieve().toBodilessEntity(); }
    private String url(String prefix) { return url(PrefixShard.forPrefix(AutocompleteService.normalize(prefix))); }
    private String url(PrefixShard shard) { return switch(shard) { case A -> a; case S -> s; case CP -> cp; case BMT -> bmt; case COMMON -> common; case RARE -> rare; }; }
}
