package com.microservices.ecommerce.Autocomplete;

/** Fixed, assumed English-like root-letter shard map. */
public enum PrefixShard {
    A, S, CP, BMT, COMMON, RARE;

    public static PrefixShard forPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return RARE;
        }
        return switch (prefix.charAt(0)) {
            case 'a' -> A;
            case 's' -> S;
            case 'c', 'p' -> CP;
            case 'b', 'm', 't' -> BMT;
            case 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'n', 'o', 'r', 'u', 'v', 'w' -> COMMON;
            default -> RARE;
        };
    }
}
