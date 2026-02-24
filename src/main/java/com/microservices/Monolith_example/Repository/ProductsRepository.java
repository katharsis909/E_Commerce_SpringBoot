package com.microservices.Monolith_example.Repository;

import com.microservices.Monolith_example.Model.Product;
import com.microservices.Monolith_example.Projections.ProductNameProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductsRepository extends JpaRepository<Product,String> {
    boolean existsByName(String name);
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
}
