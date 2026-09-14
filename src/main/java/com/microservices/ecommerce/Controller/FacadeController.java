package com.microservices.ecommerce.Controller;

import com.microservices.ecommerce.Model.Product;
import com.microservices.ecommerce.Model.Tag;
import com.microservices.ecommerce.RequestModels.ProductDetailDTO;
import com.microservices.ecommerce.RequestModels.ProductPageItemDTO;
import com.microservices.ecommerce.Service.FacadeService;
import com.microservices.ecommerce.Service.OrderService;
import com.microservices.ecommerce.Service.PhotoService;
import com.microservices.ecommerce.Service.ProductService;
import com.microservices.ecommerce.Service.TagService;
import com.microservices.ecommerce.Autocomplete.AutocompleteSuggestion;
import com.microservices.ecommerce.Autocomplete.ProductIndexEntry;
import com.microservices.ecommerce.Autocomplete.TrieShardRouter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.*;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@ConditionalOnProperty(name = "app.role", havingValue = "router", matchIfMissing = true)
//@RequestMapping
public class FacadeController {

    private final OrderService orderService;
    private final ProductService productService;
    private final FacadeService facadeService;
    private final TrieShardRouter trieShardRouter;
    private final TagService tagService;
    private final PhotoService photoService;

    @Autowired
    public FacadeController(OrderService orderService, ProductService productService, FacadeService facadeService,
                            TrieShardRouter trieShardRouter, TagService tagService, PhotoService photoService) {
        this.orderService = orderService;
        this.productService = productService;
        this.facadeService = facadeService;
        this.trieShardRouter = trieShardRouter;
        this.tagService = tagService;
        this.photoService = photoService;
    }

    @GetMapping("/view/product/{name}")
    public ResponseEntity<?> viewProduct(@PathVariable String name) {
        return productService.findProductByName(name)
                .map(product -> {
                    List<String> tags = tagService.getTagsForProduct(product.getId())
                            .stream().map(Tag::getName).toList();
                    String highResImage = photoService.getMainHighResBase64(product.getId());

                    ProductDetailDTO detailDTO = new ProductDetailDTO(
                            product.getId(),
                            product.getName(),
                            product.getPrice(),
                            product.getStock(),
                            product.getRating(),
                            product.getApproxRating(),
                            tags,
                            highResImage
                    );

                    //ye define krna prta hai HATEOAS me
                    EntityModel<ProductDetailDTO> resource = EntityModel.of(detailDTO);

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
    public ResponseEntity<String> addProduct(@RequestBody Product product, Authentication authentication)
    {
        if(facadeService.addProduct(product)) {
            trieShardRouter.index(new ProductIndexEntry(product.getId(), product.getName()));
            if (authentication != null && authentication.getName() != null) {
                photoService.linkSellerToProduct(authentication.getName(), product);
            }
            // Create resource URI: /view/product/{name}
            URI location = org.springframework.web.util.UriComponentsBuilder.fromPath("/view/product/{name}")
                    .buildAndExpand(product.getName())
                    .encode()
                    .toUri();
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
    public ResponseEntity<Page<ProductPageItemDTO>> getAllProductNames(Pageable pageable) {
        Page<Product> products = productService.findAllProducts(pageable);
        if (products.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        List<Long> productIds = products.getContent().stream().map(Product::getId).toList();
        Map<Long, String> lowResMap = photoService.getLowResBase64Map(productIds);

        Page<ProductPageItemDTO> pageItems = products.map(p -> new ProductPageItemDTO(
                p.getId(),
                p.getName(),
                p.getPrice(),
                p.getApproxRating(),
                lowResMap.get(p.getId())
        ));
        return ResponseEntity.ok(pageItems);
    }

    @GetMapping("/search/trie/{prefix}")
    public ResponseEntity<Page<ProductPageItemDTO>> trieSearch(Pageable pageable, @PathVariable String prefix) {
        trieShardRouter.record(prefix);
        Page<Product> products = productService.findProductsStartingWith(prefix, pageable);
        if (products.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        List<Long> productIds = products.getContent().stream().map(Product::getId).toList();
        Map<Long, String> lowResMap = photoService.getLowResBase64Map(productIds);

        Page<ProductPageItemDTO> pageItems = products.map(p -> new ProductPageItemDTO(
                p.getId(),
                p.getName(),
                p.getPrice(),
                p.getApproxRating(),
                lowResMap.get(p.getId())
        ));
        return ResponseEntity.ok(pageItems);
    }

    @GetMapping("/search/autocomplete/{prefix}")
    public ResponseEntity<List<AutocompleteSuggestion>> autocomplete(@PathVariable String prefix) {
        // Tier 1 & 2: Exact product name prefix, followed by fuzzy product name prefix (Damerau-Levenshtein)
        List<AutocompleteSuggestion> suggestions = trieShardRouter.searchWithFuzzyFallback(prefix);
        if (suggestions != null && !suggestions.isEmpty()) {
            return ResponseEntity.ok(suggestions);
        }

        // Tier 3: Search by exact tags (relational division via SQL)
        List<AutocompleteSuggestion> tagSuggestions = tagService.searchProductsByTags(List.of(prefix));
        if (tagSuggestions != null && !tagSuggestions.isEmpty()) {
            return ResponseEntity.ok(tagSuggestions);
        }

        // Tier 4: Search by fuzzy tag (Damerau-Levenshtein distance <= 1 on tag name)
        List<AutocompleteSuggestion> fuzzyTagSuggestions = tagService.fuzzySearchByTag(prefix, 1);
        if (fuzzyTagSuggestions != null && !fuzzyTagSuggestions.isEmpty()) {
            return ResponseEntity.ok(fuzzyTagSuggestions);
        }

        return ResponseEntity.noContent().build();
    }

}
