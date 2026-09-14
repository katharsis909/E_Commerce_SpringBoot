package com.microservices.ecommerce.Model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @CreationTimestamp
    private LocalDateTime timestamp;
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;
    // Stores the immutable product primary key.
}
