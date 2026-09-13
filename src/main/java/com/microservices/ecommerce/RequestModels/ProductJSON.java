package com.microservices.ecommerce.RequestModels;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
// to allow JSON Serialization
public class ProductJSON {
    private int price;
    private int stock;
    private String name;
}
