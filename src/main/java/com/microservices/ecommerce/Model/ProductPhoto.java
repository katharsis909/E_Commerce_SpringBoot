package com.microservices.ecommerce.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_photos", indexes = {
        @Index(name = "idx_photo_product", columnList = "product_id"),
        @Index(name = "idx_photo_product_main", columnList = "product_id, is_main")
})
@Data
@NoArgsConstructor
public class ProductPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Product product;

    @Column(name = "low_res_path", nullable = false)
    private String lowResPath;

    @Column(name = "high_res_path", nullable = false)
    private String highResPath;

    @Column(name = "is_main", nullable = false)
    private boolean isMain = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ProductPhoto(Product product, String lowResPath, String highResPath, boolean isMain) {
        this.product = product;
        this.lowResPath = lowResPath;
        this.highResPath = highResPath;
        this.isMain = isMain;
    }
}
