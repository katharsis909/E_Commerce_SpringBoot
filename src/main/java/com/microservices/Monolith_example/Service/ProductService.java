package com.microservices.Monolith_example.Service;

import com.microservices.Monolith_example.Projections.ProductNameProjection;
import com.microservices.Monolith_example.Exception.ProductAlreadyExistsException;
import com.microservices.Monolith_example.Model.Product;
import com.microservices.Monolith_example.Repository.ProductsRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

@Service
public class ProductService {
    ProductsRepository productsRepository;

    @Autowired
    ProductService(ProductsRepository productsRepository)
    {
        this.productsRepository = productsRepository;
    }

    public void addProduct(Product product)
    {
        if (productsRepository.existsByName(product.getName())) {
            throw new ProductAlreadyExistsException("Product with name " + product.getName() + " already exists");
        }
        productsRepository.save(product);
    }

    public Optional<Product> findProduct(String id)
    {
        return productsRepository.findById(id);
    }

    @Transactional
    public void placeOrder(String name)
    {
        Optional<Product> product = productsRepository.findById(name);
        Product product1 = product.orElse(null);
        //l - Exception handling
        product1.setStock(product1.getStock()-1);
        //productsRepository.save(product1);
        //Above line is redundant because transactional saves all changes on end of scope

    }

    public Page<ProductNameProjection> trieSearch(String prefix, Pageable pageable) {
        return productsRepository.findByNameStartingWith(prefix,pageable);
    }

    public Page<ProductNameProjection> findAllProductNames(Pageable pageable) {
        return productsRepository.findAllBy(pageable);
    }
}
