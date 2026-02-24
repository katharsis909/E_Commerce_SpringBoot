package com.microservices.Monolith_example.Model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    @CreationTimestamp
    private LocalDateTime timestamp;
    @ManyToOne
    @JoinColumn(name = "product_name")
    private Product product;
    //Stores primary key of another table, with the name - product_name
}
