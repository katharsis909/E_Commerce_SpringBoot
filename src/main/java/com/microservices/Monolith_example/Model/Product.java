package com.microservices.Monolith_example.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
//needed to instantiate the class by Hibernate
public class Product {
    private int price;
    private int stock;
    @Id
    private String name;
    //@JsonIgnore - not required for serializing at all
    //only required for deserializing
    @Version
    private long version;
}
