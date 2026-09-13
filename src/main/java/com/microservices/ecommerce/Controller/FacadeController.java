package com.microservices.ecommerce.Controller;

import com.microservices.ecommerce.Projections.ProductNameProjection;
import com.microservices.ecommerce.Model.Product;
import com.microservices.ecommerce.Projections.ProductNameProjection;
import com.microservices.ecommerce.Service.FacadeService;
import com.microservices.ecommerce.Service.OrderService;
import com.microservices.ecommerce.Service.ProductService;
import com.microservices.ecommerce.Autocomplete.AutocompleteSuggestion;
import com.microservices.ecommerce.Autocomplete.ProductIndexEntry;
import com.microservices.ecommerce.Autocomplete.TrieShardRouter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.*;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@ConditionalOnProperty(name = "app.role", havingValue = "router", matchIfMissing = true)
//@RequestMapping
public class FacadeController {

    private OrderService orderService;
    private ProductService productService;
    private FacadeService facadeService;
    private TrieShardRouter trieShardRouter;

    @Autowired
    public FacadeController(OrderService orderService, ProductService productService, FacadeService facadeService,
                            TrieShardRouter trieShardRouter) {
        this.orderService = orderService;
        this.productService = productService;
        this.facadeService = facadeService;
        this.trieShardRouter = trieShardRouter;
    }

    @GetMapping("/view/product/{name}")
    public ResponseEntity<?> viewProduct(@PathVariable String name) {
        return productService.findProductByName(name)
                .map(product -> {
                    //ye define krna prta hai HATEOAS me
                    EntityModel<Product> resource = EntityModel.of(product);

                    // link to self - IDK why needed
                    resource.add(linkTo(methodOn(FacadeController.class).viewProduct(name))
                            .withSelfRel());
                    /* Even if mapping changes in future, you are not defining the mappings,
                    but on the methods under those mappings */

                    // link to place order
                    //visible as order
                    resource.add(linkTo(methodOn(FacadeController.class).placeOrder(name))
                            .withRel("order"));

                    return ResponseEntity.ok(resource);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/order/product/{name}")
    /*placingOrder changes state of system,
    so It should be post mapping and not get mapping;
    caches,proxies all assume GET would not have changed the state!*/
    public ResponseEntity<?> placeOrder(@PathVariable String name)
    {
        facadeService.placeOrder(name);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PostMapping("/add/product")
    public ResponseEntity<String> addProduct(@RequestBody Product product)
    {
        if(facadeService.addProduct(product)) {
            trieShardRouter.index(new ProductIndexEntry(product.getId(), product.getName()));
            // Create resource URI: view/product/{name}
            URI location = URI.create("view/product/" + product.getName());
            return ResponseEntity.created(location).build();
            //created is Status code 201
            //location is header
        }
        else
            return ResponseEntity.internalServerError().build();
    }

    @GetMapping("/search/all")
    /*
    Pageable automatically gets converted by dependency - spring-boot-starter-data-rest
    The pageable is sent in RequestParam or QueryParam
    Why?
    - Does not enforces ordering of parameters
    - Parameters can be optional if written default - someValue inside @RequestParam
    - Parameters are not resources

     */
    public ResponseEntity<Page<ProductNameProjection>> getAllProductNames(Pageable pageable) {
        Page<ProductNameProjection> productNames = productService.findAllProductNames(pageable);
        if (productNames.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(productNames);
    }

    @GetMapping("/search/trie/{prefix}")
    public ResponseEntity<Page<ProductNameProjection>> trieSearch(Pageable pageable, @PathVariable String prefix) {
        trieShardRouter.record(prefix);
        Page<ProductNameProjection> productNames = productService.trieSearch(prefix,pageable);
        if (productNames.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(productNames);
    }

    @GetMapping("/search/autocomplete/{prefix}")
    public ResponseEntity<List<AutocompleteSuggestion>> autocomplete(@PathVariable String prefix) {
        List<AutocompleteSuggestion> suggestions = trieShardRouter.search(prefix);
        if (suggestions.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(suggestions);
    }

}
