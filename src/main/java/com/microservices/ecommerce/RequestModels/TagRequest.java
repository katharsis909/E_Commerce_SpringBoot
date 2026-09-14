package com.microservices.ecommerce.RequestModels;

import java.util.List;

public record TagRequest(List<String> tags) {
}
