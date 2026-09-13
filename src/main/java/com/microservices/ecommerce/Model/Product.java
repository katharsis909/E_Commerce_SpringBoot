package com.microservices.ecommerce.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
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
    //@JsonIgnore - not required for serializing at all
    //only required for deserializing
    @Version
    private long version;
}
