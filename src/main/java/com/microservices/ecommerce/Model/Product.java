package com.microservices.ecommerce.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "product", indexes = {
        @Index(name = "idx_product_approx_rating", columnList = "approx_rating")
})
@Data
@NoArgsConstructor
//needed to instantiate the class by Hibernate
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private int price;
    private int stock;

    @Column(nullable = false, unique = true)
    private String name;

    private double rating = 0.0;

    @Column(name = "approx_rating")
    private double approxRating = 0.0;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Tag> tags = new HashSet<>();

    //@JsonIgnore - not required for serializing at all
    //only required for deserializing
    @Version
    private long version;

    public void updateRating(double newRating) {
        this.rating = Math.max(0.0, Math.min(5.0, newRating));
        this.approxRating = Math.round(this.rating * 2.0) / 2.0;
    }
}
