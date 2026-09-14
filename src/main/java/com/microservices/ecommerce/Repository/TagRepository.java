package com.microservices.ecommerce.Repository;

import com.microservices.ecommerce.Model.Product;
import com.microservices.ecommerce.Model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    @Query("""
        SELECT p FROM Product p
        JOIN p.tags t
        WHERE t.name IN :tags
        GROUP BY p.id, p.price, p.stock, p.name, p.version, p.rating, p.approxRating
        HAVING COUNT(DISTINCT t.name) = :tagCount
        ORDER BY p.approxRating DESC, p.name ASC
        """)
    List<Product> searchByTags(@Param("tags") List<String> tags, @Param("tagCount") long tagCount);

    @Query("SELECT DISTINCT t.name FROM Tag t")
    List<String> findAllDistinctTagNames();

    List<Tag> findByProductId(long productId);

    Optional<Tag> findByProductIdAndName(long productId, String name);

    boolean existsByProductIdAndName(long productId, String name);
}
