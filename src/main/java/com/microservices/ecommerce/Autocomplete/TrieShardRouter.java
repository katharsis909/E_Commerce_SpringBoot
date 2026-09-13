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
    public void index(ProductIndexEntry product) { client.put().uri(url(product.name()) + "/internal/trie/products").body(product).retrieve().toBodilessEntity(); }
    public void record(String prefix) { client.post().uri(url(prefix) + "/internal/trie/record/{p}", prefix).retrieve().toBodilessEntity(); }
    public List<AutocompleteSuggestion> search(String prefix) { return client.post().uri(url(prefix) + "/internal/trie/search/{p}", prefix).retrieve().body(new ParameterizedTypeReference<>() {}); }
    public List<String> top(PrefixShard shard) { return client.get().uri(url(shard) + "/internal/trie/top-prefixes").retrieve().body(new ParameterizedTypeReference<>() {}); }
    public void suggestions(PrefixShard shard, List<PrefixSuggestionsUpdate> updates) { client.put().uri(url(shard) + "/internal/trie/suggestions").body(updates).retrieve().toBodilessEntity(); }
    private String url(String prefix) { return url(PrefixShard.forPrefix(AutocompleteService.normalize(prefix))); }
    private String url(PrefixShard shard) { return switch(shard) { case A -> a; case S -> s; case CP -> cp; case BMT -> bmt; case COMMON -> common; case RARE -> rare; }; }
}
