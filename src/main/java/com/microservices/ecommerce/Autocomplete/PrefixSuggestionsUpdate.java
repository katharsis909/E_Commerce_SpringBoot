package com.microservices.ecommerce.Autocomplete;

import java.util.List;

public record PrefixSuggestionsUpdate(String prefix, List<AutocompleteSuggestion> suggestions) {}
