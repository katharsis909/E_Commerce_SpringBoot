package com.microservices.ecommerce.Controller;

import com.microservices.ecommerce.Model.ProductPhoto;
import com.microservices.ecommerce.Service.PhotoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@ConditionalOnProperty(name = "app.role", havingValue = "router", matchIfMissing = true)
public class PhotoController {

    private final PhotoService photoService;

    @Autowired
    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping("/products/{productId}/photos")
    public ResponseEntity<?> uploadPhoto(
            @PathVariable long productId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "isMain", defaultValue = "true") boolean isMain,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String sellerUsername = authentication.getName();
        ProductPhoto photo = photoService.uploadPhoto(sellerUsername, productId, file, isMain);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", photo.getId(),
                "productId", productId,
                "isMain", photo.isMain(),
                "message", "Photo uploaded successfully"
        ));
    }
}
