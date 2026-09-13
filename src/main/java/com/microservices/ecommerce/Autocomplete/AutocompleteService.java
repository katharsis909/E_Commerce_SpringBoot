package com.microservices.ecommerce.Autocomplete;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** In-memory state owned by one trie-shard process. */
@Service
@ConditionalOnProperty(name = "app.role", havingValue = "trie-shard")
public class AutocompleteService {
    private final int sampleRate, topPrefixLimit, suggestionsPerPrefix;
    private final PrefixShard ownedShard;
    private final MemoryNode root = new MemoryNode("");
    private final Map<String, MemoryNode> nodesByPrefix = new HashMap<>();
    private final Set<MemoryNode> nodesWithSuggestions = new HashSet<>();

    public AutocompleteService(@Value("${app.trie-shard}") PrefixShard ownedShard,
                               @Value("${autocomplete.sample-rate:1000}") int sampleRate,
                               @Value("${autocomplete.top-prefix-limit:10000}") int topPrefixLimit,
                               @Value("${autocomplete.suggestions-per-prefix:8}") int suggestionsPerPrefix) {
        this.ownedShard = ownedShard;
        this.sampleRate = sampleRate;
        this.topPrefixLimit = topPrefixLimit;
        this.suggestionsPerPrefix = suggestionsPerPrefix;
    }

    public synchronized void indexProduct(ProductIndexEntry product) {
        String name = normalize(product.name());
        if (name.isEmpty() || !owns(name)) return;
        for (int length = 1; length <= name.length(); length++) ensureNode(name.substring(0, length));
    }

    public synchronized void recordSearch(String rawPrefix) {
        String prefix = normalize(rawPrefix);
        if (!owns(prefix) || ThreadLocalRandom.current().nextInt(sampleRate) != 0) return;
        for (int length = 1; length <= prefix.length(); length++) ensureNode(prefix.substring(0, length)).frequency += sampleRate;
    }

    public synchronized List<AutocompleteSuggestion> search(String rawPrefix) {
        String prefix = normalize(rawPrefix);
        recordSearch(prefix);
        MemoryNode node = nodesByPrefix.get(prefix);
        return node == null ? List.of() : List.copyOf(node.suggestions);
    }

    public synchronized List<String> topPrefixes() {
        PriorityQueue<MemoryNode> queue = new PriorityQueue<>(Comparator.comparingLong((MemoryNode n) -> n.frequency).reversed().thenComparing(n -> n.prefix));
        queue.addAll(root.children.values());
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty() && result.size() < topPrefixLimit) {
            MemoryNode node = queue.remove(); result.add(node.prefix); queue.addAll(node.children.values());
        }
        return result;
    }

    public synchronized void replaceSuggestions(List<PrefixSuggestionsUpdate> updates) {
        for (MemoryNode node : nodesWithSuggestions) node.suggestions = List.of();
        nodesWithSuggestions.clear();
        for (PrefixSuggestionsUpdate update : updates) {
            MemoryNode node = nodesByPrefix.get(normalize(update.prefix()));
            if (node != null && owns(node.prefix)) {
                node.suggestions = List.copyOf(update.suggestions().subList(0, Math.min(suggestionsPerPrefix, update.suggestions().size())));
                nodesWithSuggestions.add(node);
            }
        }
    }

    private boolean owns(String prefix) { return !prefix.isEmpty() && PrefixShard.forPrefix(prefix) == ownedShard; }
    private MemoryNode ensureNode(String prefix) {
        MemoryNode known = nodesByPrefix.get(prefix); if (known != null) return known;
        MemoryNode parent = root; StringBuilder path = new StringBuilder();
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i); path.append(c); String currentPrefix = path.toString();
            MemoryNode current = nodesByPrefix.get(currentPrefix);
            if (current == null) { current = new MemoryNode(currentPrefix); nodesByPrefix.put(currentPrefix, current); parent.children.put(c, current); }
            parent = current;
        }
        return parent;
    }
    static String normalize(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private static final class MemoryNode {
        private final String prefix; private long frequency; private List<AutocompleteSuggestion> suggestions = List.of();
        private final Map<Character, MemoryNode> children = new HashMap<>();
        private MemoryNode(String prefix) { this.prefix = prefix; }
    }
}
