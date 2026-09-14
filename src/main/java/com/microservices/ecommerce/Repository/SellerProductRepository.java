package com.microservices.ecommerce.Repository;

import com.microservices.ecommerce.Model.SellerProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerProductRepository extends JpaRepository<SellerProduct, Long> {

    boolean existsBySellerUsernameAndProductId(String sellerUsername, long productId);

    Optional<SellerProduct> findByProductId(long productId);

    Optional<SellerProduct> findBySellerUsernameAndProductId(String sellerUsername, long productId);
}
