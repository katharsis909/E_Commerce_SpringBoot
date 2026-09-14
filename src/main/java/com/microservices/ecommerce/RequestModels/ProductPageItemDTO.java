package com.microservices.ecommerce.RequestModels;

public record ProductPageItemDTO(
        long id,
        String name,
        int price,
        double approxRating,
        String lowResImage
) {
}
