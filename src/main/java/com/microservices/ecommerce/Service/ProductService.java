package com.microservices.ecommerce.Service;

import com.microservices.ecommerce.Projections.ProductNameProjection;
import com.microservices.ecommerce.Exception.ProductAlreadyExistsException;
import com.microservices.ecommerce.Model.Product;
import com.microservices.ecommerce.Repository.ProductsRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.microservices.ecommerce.Autocomplete.AutocompleteSuggestion;

import java.util.List;

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

    public Optional<Product> findProductByName(String name)
    {
        return productsRepository.findByName(name);
    }

    @Transactional
    public void placeOrder(String name)
    {
        Optional<Product> product = productsRepository.findByName(name);
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

    public List<Product> allProductsByName() {
        return productsRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    public List<AutocompleteSuggestion> firstProductsMatching(String prefix, int limit) {
        return productsRepository.findProductsByNameStartingWith(prefix, PageRequest.of(0, limit))
                .map(product -> new AutocompleteSuggestion(product.getId(), product.getName()))
                .getContent();
    }

}
