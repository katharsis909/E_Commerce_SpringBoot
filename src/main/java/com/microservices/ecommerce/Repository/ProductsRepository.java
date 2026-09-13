package com.microservices.ecommerce.Repository;

import com.microservices.ecommerce.Model.Product;
import com.microservices.ecommerce.Projections.ProductNameProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductsRepository extends JpaRepository<Product, Long> {
    boolean existsByName(String name);
    java.util.Optional<Product> findByName(String name);
    Page<ProductNameProjection> findAllBy(Pageable pageable);
    /*
    making the below query case-insensitive!
     */
    @Query("""
        SELECT p.name AS name
        FROM Product p
        WHERE LOWER(p.name) LIKE LOWER(CONCAT(:prefix, '%'))
        """)
    Page<ProductNameProjection> findByNameStartingWith(@Param("prefix") String name, Pageable pageable);

    @Query("""
        SELECT p
        FROM Product p
        WHERE LOWER(p.name) LIKE LOWER(CONCAT(:prefix, '%'))
        ORDER BY p.name ASC
        """)
    Page<Product> findProductsByNameStartingWith(@Param("prefix") String prefix, Pageable pageable);

}
