package com.microservices.ecommerce.Repository;

import com.microservices.ecommerce.Model.ProductPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductPhotoRepository extends JpaRepository<ProductPhoto, Long> {

    List<ProductPhoto> findByProductId(long productId);

    Optional<ProductPhoto> findByProductIdAndIsMainTrue(long productId);

    List<ProductPhoto> findByProductIdInAndIsMainTrue(Collection<Long> productIds);

    @Modifying
    @Query("UPDATE ProductPhoto p SET p.isMain = false WHERE p.product.id = :productId")
    void clearMainPhotos(@Param("productId") long productId);
}
