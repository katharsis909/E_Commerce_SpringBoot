package com.microservices.ecommerce.Controller;

import com.microservices.ecommerce.Model.Tag;
import com.microservices.ecommerce.RequestModels.TagRequest;
import com.microservices.ecommerce.Service.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tags")
@ConditionalOnProperty(name = "app.role", havingValue = "router", matchIfMissing = true)
public class TagController {

    private final TagService tagService;

    @Autowired
    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @PostMapping("/{productId}/add")
    public ResponseEntity<Tag> addTag(@PathVariable long productId, @RequestParam String tag) {
        Tag createdTag = tagService.addTag(productId, tag);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTag);
    }

    @PostMapping("/{productId}/upload")
    public ResponseEntity<List<Tag>> uploadTags(@PathVariable long productId, @RequestBody TagRequest request) {
        List<Tag> createdTags = tagService.uploadTags(productId, request.tags());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTags);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<List<Tag>> getTags(@PathVariable long productId) {
        List<Tag> tags = tagService.getTagsForProduct(productId);
        return ResponseEntity.ok(tags);
    }
}
