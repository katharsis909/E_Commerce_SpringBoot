package com.microservices.ecommerce.Service;

import com.microservices.ecommerce.Model.Product;
import com.microservices.ecommerce.RequestModels.ProductJSON;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class FacadeService {
    //Reason for creating FacadeService below -:
    /*
    There was literally no need of even OrderService
        2 services are made to allow MicroServices later on
            FacadeService is needed to use methods of Order Service & Product Service each for a single mapping
            I know this is a overkill!
     */

    OrderService orderService;
    ProductService productService;

    @Autowired
    public FacadeService(OrderService orderService, ProductService productService) {
        this.orderService = orderService;
        this.productService = productService;
    }
    @Transactional
    public void placeOrder(String name)
    {
        productService.placeOrder(name);
        Optional<Product> product = productService.findProductByName(name);
        //try to not use findProduct again until save;
        Product product1 = product.orElse(null);
        orderService.placeOrder(product1);
    }

    public boolean addProduct(Product product)
    {
        productService.addProduct(product);
        return true;
    }
}
