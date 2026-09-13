package com.microservices.ecommerce.Autocomplete;

import com.microservices.ecommerce.Model.Product;
import com.microservices.ecommerce.Service.ProductService;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Router-side coordination: catalogue data stays here; trie state stays on shard processes. */
@Component
@ConditionalOnProperty(name = "autocomplete.router.bootstrap-enabled", havingValue = "true")
public class RouterAutocompleteCoordinator {
    private final ProductService productService;
    private final TrieShardRouter trieShardRouter;
    private final int suggestionsPerPrefix;

    public RouterAutocompleteCoordinator(ProductService productService, TrieShardRouter trieShardRouter,
                                         @org.springframework.beans.factory.annotation.Value("${autocomplete.suggestions-per-prefix:8}") int suggestionsPerPrefix) {
        this.productService = productService;
        this.trieShardRouter = trieShardRouter;
        this.suggestionsPerPrefix = suggestionsPerPrefix;
    }

    @PostConstruct
    public void indexCatalogueOnShards() {
        for (Product product : productService.allProductsByName()) {
            trieShardRouter.index(new ProductIndexEntry(product.getId(), product.getName()));
        }
        refreshPopularSuggestions();
    }

    @Scheduled(cron = "${autocomplete.rebuild.cron:0 0 0 * * SUN}")
    public void refreshPopularSuggestions() {
        for (PrefixShard shard : PrefixShard.values()) {
            List<PrefixSuggestionsUpdate> updates = new ArrayList<>();
            for (String prefix : trieShardRouter.top(shard)) {
                updates.add(new PrefixSuggestionsUpdate(prefix, productService.firstProductsMatching(prefix, suggestionsPerPrefix)));
            }
            trieShardRouter.suggestions(shard, updates);
        }
    }
}
