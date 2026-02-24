package com.microservices.Monolith_example.RequestModels;

import lombok.Data;

@Data
public class RequestAuth {
    private String username;
    private String password;
}