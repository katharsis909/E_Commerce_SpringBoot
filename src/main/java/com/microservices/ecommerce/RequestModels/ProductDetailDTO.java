package com.microservices.ecommerce.RequestModels;

import java.util.List;

public record ProductDetailDTO(
        long id,
        String name,
        int price,
        int stock,
        double rating,
        double approxRating,
        List<String> tags,
        String highResImage
) {
}
