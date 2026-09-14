package com.microservices.ecommerce.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "seller_products", indexes = {
        @Index(name = "idx_seller_user", columnList = "seller_username"),
        @Index(name = "idx_seller_product", columnList = "seller_username, product_id", unique = true)
})
@Data
@NoArgsConstructor
public class SellerProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_username", nullable = false)
    private String sellerUsername;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Product product;

    public SellerProduct(String sellerUsername, Product product) {
        this.sellerUsername = sellerUsername;
        this.product = product;
    }
}
