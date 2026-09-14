package com.microservices.ecommerce.Tag;

import com.microservices.ecommerce.Autocomplete.AutocompleteSuggestion;
import com.microservices.ecommerce.Model.Product;
import com.microservices.ecommerce.Model.Tag;
import com.microservices.ecommerce.Repository.ProductsRepository;
import com.microservices.ecommerce.Repository.TagRepository;
import com.microservices.ecommerce.Service.TagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TagSearchIntegrationTest {

    @Autowired
    private TagService tagService;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ProductsRepository productsRepository;

    private Product productApple;
    private Product productSamsung;
    private Product productSony;

    @BeforeEach
    void setUp() {
        tagRepository.deleteAll();
        productsRepository.deleteAll();

        // 1. Create Products with different ratings
        productApple = new Product();
        productApple.setName("iPhone 15 Pro");
        productApple.setPrice(999);
        productApple.setStock(50);
        productApple.updateRating(4.3); // approxRating = 4.5
        productApple = productsRepository.save(productApple);

        productSamsung = new Product();
        productSamsung.setName("Galaxy S24");
        productSamsung.setPrice(899);
        productSamsung.setStock(40);
        productSamsung.updateRating(4.8); // approxRating = 5.0
        productSamsung = productsRepository.save(productSamsung);

        productSony = new Product();
        productSony.setName("Sony Headphones");
        productSony.setPrice(299);
        productSony.setStock(30);
        productSony.updateRating(3.6); // approxRating = 3.5
        productSony = productsRepository.save(productSony);

        // 2. Add Tags via TagService
        tagService.uploadTags(productApple.getId(), List.of("apple", "wireless", "premium"));
        tagService.uploadTags(productSamsung.getId(), List.of("samsung", "wireless", "premium"));
        tagService.uploadTags(productSony.getId(), List.of("sony", "audio", "wireless"));
    }

    @Test
    void testRatingBinningLogic() {
        Product p = new Product();
        p.setName("Test Product");
        p.setPrice(10);
        p.setStock(5);

        // 4.21 rounds to 4.0
        p.updateRating(4.21);
        assertEquals(4.21, p.getRating(), 0.001);
        assertEquals(4.0, p.getApproxRating(), 0.001);

        // 4.26 rounds to 4.5
        p.updateRating(4.26);
        assertEquals(4.26, p.getRating(), 0.001);
        assertEquals(4.5, p.getApproxRating(), 0.001);

        // 4.76 rounds to 5.0
        p.updateRating(4.76);
        assertEquals(4.76, p.getRating(), 0.001);
        assertEquals(5.0, p.getApproxRating(), 0.001);

        // Bounds clamping
        p.updateRating(-2.0);
        assertEquals(0.0, p.getApproxRating(), 0.001);

        p.updateRating(7.5);
        assertEquals(5.0, p.getApproxRating(), 0.001);
    }

    @Test
    void testSingleAndBatchTagUpload() {
        Product p = new Product();
        p.setName("Test Accessories");
        p.setPrice(20);
        p.setStock(10);
        p = productsRepository.save(p);

        tagService.addTag(p.getId(), "cable");
        tagService.uploadTags(p.getId(), List.of("charger", "usb-c", "cable")); // duplicate "cable" should be ignored

        List<Tag> tags = tagService.getTagsForProduct(p.getId());
        assertEquals(3, tags.size());
        assertTrue(tags.stream().anyMatch(t -> t.getName().equals("cable")));
        assertTrue(tags.stream().anyMatch(t -> t.getName().equals("charger")));
        assertTrue(tags.stream().anyMatch(t -> t.getName().equals("usb-c")));
    }

    @Test
    void testRelationalDivisionTagSearchSortedByApproxRating() {
        // Query for ["wireless", "premium"] (tagCount = 2)
        // Both Apple (approxRating 4.5) and Samsung (approxRating 5.0) have both tags
        // Sony only has "wireless", so it must be excluded.
        // Results must be ordered by approxRating DESC: Samsung (5.0) first, then Apple (4.5).
        List<AutocompleteSuggestion> results = tagService.searchProductsByTags(List.of("wireless", "premium"));

        assertEquals(2, results.size());
        assertEquals("Galaxy S24", results.get(0).name());
        assertEquals("iPhone 15 Pro", results.get(1).name());
    }

    @Test
    void testFuzzyTagSearchWithDistanceOne() {
        // "wireles" (missing 's') has edit distance 1 to "wireless"
        List<AutocompleteSuggestion> results = tagService.fuzzySearchByTag("wireles", 1);
        assertFalse(results.isEmpty());
        // All 3 products have "wireless"
        assertEquals(3, results.size());
        // Ordered by approxRating DESC: Samsung (5.0) -> Apple (4.5) -> Sony (3.5)
        assertEquals("Galaxy S24", results.get(0).name());
        assertEquals("iPhone 15 Pro", results.get(1).name());
        assertEquals("Sony Headphones", results.get(2).name());
    }

    @Test
    void testFuzzyTagSearchAdjacentTransposition() {
        // "audoi" (swapped 'oi' -> 'io') has Damerau-Levenshtein distance 1 to "audio"
        List<AutocompleteSuggestion> results = tagService.fuzzySearchByTag("audoi", 1);
        assertEquals(1, results.size());
        assertEquals("Sony Headphones", results.get(0).name());
    }

    @Test
    void testFuzzyTagSearchExceedingDistanceReturnsEmpty() {
        // "wireeeeless" has edit distance > 1 to "wireless"
        List<AutocompleteSuggestion> results = tagService.fuzzySearchByTag("wireeeeless", 1);
        assertTrue(results.isEmpty());
    }
}
