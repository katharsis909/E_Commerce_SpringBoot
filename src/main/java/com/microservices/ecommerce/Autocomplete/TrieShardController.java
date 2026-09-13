package com.microservices.ecommerce.Autocomplete;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/internal/trie")
@ConditionalOnProperty(name = "app.role", havingValue = "trie-shard")
public class TrieShardController {
    private final AutocompleteService trie;
    public TrieShardController(AutocompleteService trie) { this.trie = trie; }
    @PutMapping("/products") public void index(@RequestBody ProductIndexEntry product) { trie.indexProduct(product); }
    @PostMapping("/record/{prefix}") public void record(@PathVariable String prefix) { trie.recordSearch(prefix); }
    @PostMapping("/search/{prefix}") public List<AutocompleteSuggestion> search(@PathVariable String prefix) { return trie.search(prefix); }
    @GetMapping("/top-prefixes") public List<String> topPrefixes() { return trie.topPrefixes(); }
    @PutMapping("/suggestions") public void suggestions(@RequestBody List<PrefixSuggestionsUpdate> updates) { trie.replaceSuggestions(updates); }
}
