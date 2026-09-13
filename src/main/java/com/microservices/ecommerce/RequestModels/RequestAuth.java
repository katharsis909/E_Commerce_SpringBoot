package com.microservices.ecommerce.RequestModels;

import lombok.Data;

@Data
public class RequestAuth {
    private String username;
    private String password;
}