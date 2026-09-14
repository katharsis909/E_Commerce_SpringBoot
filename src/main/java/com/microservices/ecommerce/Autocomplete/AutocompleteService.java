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
        AutocompleteSuggestion suggestion = new AutocompleteSuggestion(product.productId(), product.name());
        for (int length = 1; length <= name.length(); length++) {
            MemoryNode node = ensureNode(name.substring(0, length));
            addSuggestion(node, suggestion);
        }
    }

    private void addSuggestion(MemoryNode node, AutocompleteSuggestion suggestion) {
        if (node.suggestions.isEmpty()) {
            node.suggestions = new ArrayList<>();
        } else if (!(node.suggestions instanceof ArrayList)) {
            node.suggestions = new ArrayList<>(node.suggestions);
        }
        boolean exists = false;
        for (AutocompleteSuggestion s : node.suggestions) {
            if (s.productId() == suggestion.productId()) {
                exists = true;
                break;
            }
        }
        if (!exists && node.suggestions.size() < suggestionsPerPrefix) {
            node.suggestions.add(suggestion);
        }
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

    public synchronized List<AutocompleteSuggestion> fuzzySearch(String rawPrefix, int maxDistance) {
        String query = normalize(rawPrefix);
        if (query.isEmpty() || maxDistance < 1) return List.of();

        int m = query.length();
        int[] initialRow = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            initialRow[j] = j;
        }

        Map<Long, ScoredSuggestion> bestSuggestions = new HashMap<>();

        for (Map.Entry<Character, MemoryNode> entry : root.children.entrySet()) {
            char edgeChar = entry.getKey();
            MemoryNode child = entry.getValue();
            dfsFuzzy(child, edgeChar, '\0', initialRow, null, query, m, maxDistance, bestSuggestions);
        }

        return bestSuggestions.values().stream()
                .sorted(Comparator.comparingInt(ScoredSuggestion::distance)
                        .thenComparing(Comparator.comparingLong(ScoredSuggestion::frequency).reversed())
                        .thenComparing(s -> s.suggestion().name()))
                .map(ScoredSuggestion::suggestion)
                .limit(suggestionsPerPrefix)
                .toList();
    }

    private void dfsFuzzy(MemoryNode node,
                          char currentChar,
                          char prevChar,
                          int[] prevRow,
                          int[] prevPrevRow,
                          String query,
                          int m,
                          int maxDistance,
                          Map<Long, ScoredSuggestion> bestSuggestions) {
        int[] currentRow = new int[m + 1];
        currentRow[0] = prevRow[0] + 1;
        int rowMin = currentRow[0];

        for (int j = 1; j <= m; j++) {
            char queryChar = query.charAt(j - 1);
            int cost = (queryChar == currentChar) ? 0 : 1;
            int insert = currentRow[j - 1] + 1;
            int delete = prevRow[j] + 1;
            int replace = prevRow[j - 1] + cost;
            int minVal = Math.min(insert, Math.min(delete, replace));

            // Damerau-Levenshtein adjacent transposition
            if (prevPrevRow != null && j > 1) {
                char prevQueryChar = query.charAt(j - 2);
                if (currentChar == prevQueryChar && prevChar == queryChar) {
                    int transpose = prevPrevRow[j - 2] + 1;
                    minVal = Math.min(minVal, transpose);
                }
            }
            currentRow[j] = minVal;
            if (minVal < rowMin) {
                rowMin = minVal;
            }
        }

        // Branch pruning: if every value in the current DP row exceeds maxDistance,
        // no child in this subtree can ever achieve edit distance <= maxDistance.
        if (rowMin > maxDistance) {
            return;
        }

        // Match condition: edit distance between query and this node's prefix <= maxDistance
        int editDistance = currentRow[m];
        if (editDistance <= maxDistance && !node.suggestions.isEmpty()) {
            for (AutocompleteSuggestion s : node.suggestions) {
                ScoredSuggestion existing = bestSuggestions.get(s.productId());
                if (existing == null || editDistance < existing.distance() ||
                        (editDistance == existing.distance() && node.frequency > existing.frequency())) {
                    bestSuggestions.put(s.productId(), new ScoredSuggestion(s, editDistance, node.frequency));
                }
            }
        }

        // Traverse children carrying the previous two rows and character history
        for (Map.Entry<Character, MemoryNode> entry : node.children.entrySet()) {
            dfsFuzzy(entry.getValue(), entry.getKey(), currentChar, currentRow, prevRow, query, m, maxDistance, bestSuggestions);
        }
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
    public static String normalize(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }

    private record ScoredSuggestion(AutocompleteSuggestion suggestion, int distance, long frequency) {}

    private static final class MemoryNode {
        private final String prefix; private long frequency; private List<AutocompleteSuggestion> suggestions = List.of();
        private final Map<Character, MemoryNode> children = new HashMap<>();
        private MemoryNode(String prefix) { this.prefix = prefix; }
    }
}
