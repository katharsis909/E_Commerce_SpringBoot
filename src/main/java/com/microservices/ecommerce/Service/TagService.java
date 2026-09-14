package com.microservices.ecommerce.Service;

import com.microservices.ecommerce.Autocomplete.AutocompleteSuggestion;
import com.microservices.ecommerce.Model.Product;
import com.microservices.ecommerce.Model.Tag;
import com.microservices.ecommerce.Repository.ProductsRepository;
import com.microservices.ecommerce.Repository.TagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TagService {

    private final TagRepository tagRepository;
    private final ProductsRepository productsRepository;

    @Autowired
    public TagService(TagRepository tagRepository, ProductsRepository productsRepository) {
        this.tagRepository = tagRepository;
        this.productsRepository = productsRepository;
    }

    @Transactional
    public Tag addTag(long productId, String rawTagName) {
        if (rawTagName == null || rawTagName.isBlank()) {
            throw new IllegalArgumentException("Tag name must not be blank");
        }
        Product product = productsRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

        String normalizedName = normalizeTag(rawTagName);
        if (tagRepository.existsByProductIdAndName(productId, normalizedName)) {
            return tagRepository.findByProductIdAndName(productId, normalizedName).orElseThrow();
        }

        Tag tag = new Tag(normalizedName, product);
        product.getTags().add(tag);
        return tagRepository.save(tag);
    }

    @Transactional
    public List<Tag> uploadTags(long productId, Collection<String> rawTagNames) {
        if (rawTagNames == null || rawTagNames.isEmpty()) {
            return List.of();
        }
        Product product = productsRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

        List<Tag> savedTags = new ArrayList<>();
        for (String rawTag : rawTagNames) {
            if (rawTag == null || rawTag.isBlank()) continue;
            String normalized = normalizeTag(rawTag);
            if (!tagRepository.existsByProductIdAndName(productId, normalized)) {
                Tag tag = new Tag(normalized, product);
                product.getTags().add(tag);
                savedTags.add(tagRepository.save(tag));
            }
        }
        return savedTags;
    }

    @Transactional(readOnly = true)
    public List<Tag> getTagsForProduct(long productId) {
        return tagRepository.findByProductId(productId);
    }

    @Transactional(readOnly = true)
    public List<AutocompleteSuggestion> searchProductsByTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }
        List<String> normalizedTags = rawTags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(this::normalizeTag)
                .distinct()
                .toList();

        if (normalizedTags.isEmpty()) {
            return List.of();
        }

        List<Product> products = tagRepository.searchByTags(normalizedTags, normalizedTags.size());
        return products.stream()
                .map(p -> new AutocompleteSuggestion(p.getId(), p.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AutocompleteSuggestion> fuzzySearchByTag(String rawTag, int maxDistance) {
        if (rawTag == null || rawTag.isBlank()) {
            return List.of();
        }
        String normalizedQuery = normalizeTag(rawTag);
        List<String> allDistinctTags = tagRepository.findAllDistinctTagNames();

        List<String> matchingTags = new ArrayList<>();
        for (String tag : allDistinctTags) {
            if (damerauLevenshteinDistance(normalizedQuery, tag) <= maxDistance) {
                matchingTags.add(tag);
            }
        }

        if (matchingTags.isEmpty()) {
            return List.of();
        }

        // Relational division for single-tag matching across candidate fuzzy tags
        List<Product> products = tagRepository.searchByTags(matchingTags, 1L);
        return products.stream()
                .map(p -> new AutocompleteSuggestion(p.getId(), p.getName()))
                .toList();
    }

    public String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
    }

    public static int damerauLevenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) return Integer.MAX_VALUE;
        int len1 = s1.length();
        int len2 = s2.length();

        int[][] d = new int[len1 + 1][len2 + 1];
        for (int i = 0; i <= len1; i++) d[i][0] = i;
        for (int j = 0; j <= len2; j++) d[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                int del = d[i - 1][j] + 1;
                int ins = d[i][j - 1] + 1;
                int sub = d[i - 1][j - 1] + cost;
                int min = Math.min(del, Math.min(ins, sub));

                if (i > 1 && j > 1 &&
                        s1.charAt(i - 1) == s2.charAt(j - 2) &&
                        s1.charAt(i - 2) == s2.charAt(j - 1)) {
                    min = Math.min(min, d[i - 2][j - 2] + 1);
                }
                d[i][j] = min;
            }
        }
        return d[len1][len2];
    }
}
