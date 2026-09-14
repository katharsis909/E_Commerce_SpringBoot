package com.microservices.ecommerce.Autocomplete;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutocompleteFuzzySearchTest {

    private AutocompleteService shardCommon;
    private AutocompleteService shardCP;

    @BeforeEach
    void setUp() {
        shardCommon = new AutocompleteService(PrefixShard.COMMON, 1000, 10000, 8);
        shardCP = new AutocompleteService(PrefixShard.CP, 1000, 10000, 8);

        // Index products into shardCommon (owns 'd'..'w', including 'i')
        shardCommon.indexProduct(new ProductIndexEntry(1L, "iPhone 15"));
        shardCommon.indexProduct(new ProductIndexEntry(2L, "iPhone 15 Pro"));
        shardCommon.indexProduct(new ProductIndexEntry(3L, "iPad Air"));

        // Index products into shardCP (owns 'c', 'p')
        shardCP.indexProduct(new ProductIndexEntry(4L, "Pixel 8"));
        shardCP.indexProduct(new ProductIndexEntry(5L, "PlayStation 5"));
    }

    @Test
    void exactPrefixSearchReturnsMatches() {
        List<AutocompleteSuggestion> results = shardCommon.search("iph");
        assertFalse(results.isEmpty(), "Exact prefix 'iph' should find iPhone 15");
        assertTrue(results.stream().anyMatch(s -> s.name().equals("iPhone 15")));
    }

    @Test
    void exactPrefixSearchReturnsEmptyForTypo() {
        List<AutocompleteSuggestion> results = shardCommon.search("iphnoe");
        assertTrue(results.isEmpty(), "Exact search for typo 'iphnoe' should return empty");
    }

    @Test
    void damerauLevenshteinAdjacentSwapFindsProduct() {
        // "iphnoe" (swap 'oe' -> 'eo') has Damerau-Levenshtein distance 1 to "iphone"
        List<AutocompleteSuggestion> fuzzyResults = shardCommon.fuzzySearch("iphnoe", 1);
        assertFalse(fuzzyResults.isEmpty(), "Fuzzy search with distance 1 should match 'iphnoe' -> 'iPhone 15'");
        assertTrue(fuzzyResults.stream().anyMatch(s -> s.name().equals("iPhone 15")));
    }

    @Test
    void singleEditInsertionFindsProduct() {
        // "iphonne" (extra 'n') has edit distance 1 to "iphone"
        List<AutocompleteSuggestion> fuzzyResults = shardCommon.fuzzySearch("iphonne", 1);
        assertFalse(fuzzyResults.isEmpty(), "Fuzzy search should match insertion 'iphonne' -> 'iPhone 15'");
        assertTrue(fuzzyResults.stream().anyMatch(s -> s.name().equals("iPhone 15")));
    }

    @Test
    void singleEditDeletionFindsProduct() {
        // "iphne" (missing 'o') has edit distance 1 to "iphone"
        List<AutocompleteSuggestion> fuzzyResults = shardCommon.fuzzySearch("iphne", 1);
        assertFalse(fuzzyResults.isEmpty(), "Fuzzy search should match deletion 'iphne' -> 'iPhone 15'");
        assertTrue(fuzzyResults.stream().anyMatch(s -> s.name().equals("iPhone 15")));
    }

    @Test
    void singleEditSubstitutionFindsProduct() {
        // "iphxne" ('o' replaced by 'x') has edit distance 1 to "iphone"
        List<AutocompleteSuggestion> fuzzyResults = shardCommon.fuzzySearch("iphxne", 1);
        assertFalse(fuzzyResults.isEmpty(), "Fuzzy search should match substitution 'iphxne' -> 'iPhone 15'");
        assertTrue(fuzzyResults.stream().anyMatch(s -> s.name().equals("iPhone 15")));
    }

    @Test
    void editDistanceTwoMatchesWhenAllowed() {
        // "iphxnee" has edit distance 2 to "iphone"
        List<AutocompleteSuggestion> dist1Results = shardCommon.fuzzySearch("iphxnee", 1);
        assertTrue(dist1Results.isEmpty(), "Distance 1 should not match 'iphxnee'");

        List<AutocompleteSuggestion> dist2Results = shardCommon.fuzzySearch("iphxnee", 2);
        assertFalse(dist2Results.isEmpty(), "Distance 2 should match 'iphxnee' -> 'iPhone 15'");
        assertTrue(dist2Results.stream().anyMatch(s -> s.name().equals("iPhone 15")));
    }

    @Test
    void excessiveDistanceReturnsEmpty() {
        // "xyzphone" has edit distance 3 to "iphone"
        List<AutocompleteSuggestion> dist2Results = shardCommon.fuzzySearch("xyzphone", 2);
        assertTrue(dist2Results.isEmpty(), "Distance 3 should be pruned when maxDistance is 2");
    }

    @Test
    void crossShardFirstLetterTranspositionMatches() {
        // "pihone" (swapped first two letters 'p' and 'i') evaluated on shard COMMON
        List<AutocompleteSuggestion> results = shardCommon.fuzzySearch("pihone", 1);
        assertFalse(results.isEmpty(), "Damerau-Levenshtein should match swapped first letters 'pihone' -> 'iPhone 15'");
        assertTrue(results.stream().anyMatch(s -> s.name().equals("iPhone 15")));
    }

    @Test
    void routerGatingBehavior() {
        java.util.concurrent.atomic.AtomicInteger fuzzyMaxDistanceCalled = new java.util.concurrent.atomic.AtomicInteger(-1);
        TrieShardRouter router = new TrieShardRouter("http://a", "http://s", "http://cp", "http://bmt", "http://common", "http://rare") {
            @Override
            public List<AutocompleteSuggestion> search(String prefix) {
                if ("exact".equals(prefix)) {
                    return List.of(new AutocompleteSuggestion(1L, "Exact Match"));
                }
                return List.of();
            }

            @Override
            public List<AutocompleteSuggestion> fuzzy(PrefixShard shard, String prefix, int maxDistance) {
                fuzzyMaxDistanceCalled.set(maxDistance);
                return List.of(new AutocompleteSuggestion(2L, "Fuzzy Match"));
            }
        };

        // 1. Exact match returns immediately without invoking fuzzy
        List<AutocompleteSuggestion> exact = router.searchWithFuzzyFallback("exact");
        assertEquals("Exact Match", exact.get(0).name());
        assertEquals(-1, fuzzyMaxDistanceCalled.get(), "Fuzzy search must not be called when exact match is found");

        // 2. Length < 5 returns empty without invoking fuzzy
        List<AutocompleteSuggestion> shortQuery = router.searchWithFuzzyFallback("four");
        assertTrue(shortQuery.isEmpty(), "Queries shorter than 5 characters must not trigger fuzzy search");
        assertEquals(-1, fuzzyMaxDistanceCalled.get());

        // 3. 5 <= length < 9 invokes fuzzy with maxDistance = 1
        List<AutocompleteSuggestion> midQuery = router.searchWithFuzzyFallback("iphone");
        assertEquals("Fuzzy Match", midQuery.get(0).name());
        assertEquals(1, fuzzyMaxDistanceCalled.get(), "Query of length 6 must use maxDistance = 1");

        // 4. length >= 9 invokes fuzzy with maxDistance = 2
        fuzzyMaxDistanceCalled.set(-1);
        List<AutocompleteSuggestion> longQuery = router.searchWithFuzzyFallback("smartphone");
        assertEquals("Fuzzy Match", longQuery.get(0).name());
        assertEquals(2, fuzzyMaxDistanceCalled.get(), "Query of length 10 must use maxDistance = 2");
    }
}
