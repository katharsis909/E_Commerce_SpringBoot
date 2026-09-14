package com.microservices.ecommerce.Photo;

import com.microservices.ecommerce.Model.Product;
import com.microservices.ecommerce.Model.ProductPhoto;
import com.microservices.ecommerce.Model.SellerProduct;
import com.microservices.ecommerce.Repository.ProductPhotoRepository;
import com.microservices.ecommerce.Repository.ProductsRepository;
import com.microservices.ecommerce.Repository.SellerProductRepository;
import com.microservices.ecommerce.Repository.TagRepository;
import com.microservices.ecommerce.Service.PhotoService;
import com.microservices.ecommerce.Service.TagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductPhotoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductsRepository productsRepository;

    @Autowired
    private SellerProductRepository sellerProductRepository;

    @Autowired
    private ProductPhotoRepository productPhotoRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private TagService tagService;

    @Autowired
    private PhotoService photoService;

    private Product testProduct;
    private byte[] sampleImageBytes;

    @BeforeEach
    void setUp() throws Exception {
        productPhotoRepository.deleteAll();
        sellerProductRepository.deleteAll();
        tagRepository.deleteAll();
        productsRepository.deleteAll();

        // 1. Create a product
        testProduct = new Product();
        testProduct.setName("MacBook Pro M3");
        testProduct.setPrice(1999);
        testProduct.setStock(15);
        testProduct.updateRating(4.8);
        testProduct = productsRepository.save(testProduct);

        // 2. Link seller1 as owner
        sellerProductRepository.save(new SellerProduct("seller1", testProduct));

        // 3. Create a 400x300 sample image in-memory
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, 400, 300);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Test Laptop", 50, 50);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        sampleImageBytes = baos.toByteArray();
    }

    @AfterEach
    void tearDown() {
        // Clean up any uploaded test files
        for (ProductPhoto photo : productPhotoRepository.findAll()) {
            try {
                if (photo.getHighResPath() != null) Files.deleteIfExists(Paths.get(photo.getHighResPath()));
                if (photo.getLowResPath() != null) Files.deleteIfExists(Paths.get(photo.getLowResPath()));
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void testSellerUploadPhotoSuccessAndGeneratesLowResThumbnail() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "laptop.jpg", "image/jpeg", sampleImageBytes
        );

        mockMvc.perform(multipart("/products/" + testProduct.getId() + "/photos")
                        .file(file)
                        .param("isMain", "true")
                        .with(user("seller1").roles("seller")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(testProduct.getId()))
                .andExpect(jsonPath("$.isMain").value(true));

        List<ProductPhoto> photos = productPhotoRepository.findByProductId(testProduct.getId());
        assertEquals(1, photos.size());
        ProductPhoto photo = photos.get(0);
        assertTrue(photo.isMain());

        // Verify high-res file exists on disk
        File highResFile = new File(photo.getHighResPath());
        assertTrue(highResFile.exists(), "High-res file must exist on disk");
        assertTrue(highResFile.length() > 0);

        // Verify low-res file exists on disk and is resized to <= 150px
        File lowResFile = new File(photo.getLowResPath());
        assertTrue(lowResFile.exists(), "Low-res file must exist on disk");
        BufferedImage thumb = ImageIO.read(lowResFile);
        assertNotNull(thumb, "Low-res file should be a readable image");
        assertTrue(thumb.getWidth() <= 150, "Thumbnail width must be <= 150px but was " + thumb.getWidth());
        assertTrue(thumb.getHeight() <= 150, "Thumbnail height must be <= 150px but was " + thumb.getHeight());
    }

    @Test
    void testUnauthorizedSellerReceivesForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "laptop.jpg", "image/jpeg", sampleImageBytes
        );

        // seller2 is NOT mapped to testProduct in seller_products
        mockMvc.perform(multipart("/products/" + testProduct.getId() + "/photos")
                        .file(file)
                        .with(user("seller2").roles("seller")))
                .andExpect(status().isForbidden());

        // No photo should have been persisted
        List<ProductPhoto> photos = productPhotoRepository.findByProductId(testProduct.getId());
        assertTrue(photos.isEmpty());
    }

    @Test
    void testBuyerRoleForbiddenFromPhotoUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "laptop.jpg", "image/jpeg", sampleImageBytes
        );

        // User with ROLE_buyer should be rejected with 403 Forbidden by SecurityConfig
        mockMvc.perform(multipart("/products/" + testProduct.getId() + "/photos")
                        .file(file)
                        .with(user("buyer1").roles("buyer")))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPaginatedSearchReturnsInlineLowResBase64() throws Exception {
        // Upload photo via service for seller1
        MockMultipartFile file = new MockMultipartFile(
                "file", "laptop.jpg", "image/jpeg", sampleImageBytes
        );
        photoService.uploadPhoto("seller1", testProduct.getId(), file, true);

        // Call GET /search/all
        mockMvc.perform(get("/search/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("MacBook Pro M3"))
                .andExpect(jsonPath("$.content[0].approxRating").value(5.0))
                .andExpect(jsonPath("$.content[0].lowResImage").isString())
                .andExpect(jsonPath("$.content[0].lowResImage", not(emptyString())));

        // Verify Base64 can be decoded into image bytes
        String lowResBase64 = photoService.getMainLowResBase64(testProduct.getId());
        assertNotNull(lowResBase64);
        byte[] decoded = Base64.getDecoder().decode(lowResBase64);
        assertTrue(decoded.length > 0);
    }

    @Test
    void testTappedProductDetailReturnsInlineHighResBase64AndTags() throws Exception {
        // Upload photo
        MockMultipartFile file = new MockMultipartFile(
                "file", "laptop.jpg", "image/jpeg", sampleImageBytes
        );
        photoService.uploadPhoto("seller1", testProduct.getId(), file, true);

        // Add tags
        tagService.uploadTags(testProduct.getId(), List.of("apple", "laptop", "silicon"));

        // Call GET /view/product/{name}
        mockMvc.perform(get("/view/product/" + testProduct.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MacBook Pro M3"))
                .andExpect(jsonPath("$.price").value(1999))
                .andExpect(jsonPath("$.stock").value(15))
                .andExpect(jsonPath("$.approxRating").value(5.0))
                .andExpect(jsonPath("$.tags", containsInAnyOrder("apple", "laptop", "silicon")))
                .andExpect(jsonPath("$.highResImage").isString())
                .andExpect(jsonPath("$.highResImage", not(emptyString())))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.order.href").exists());

        // Verify Base64 decodes back to high-res bytes
        String highResBase64 = photoService.getMainHighResBase64(testProduct.getId());
        assertNotNull(highResBase64);
        byte[] decoded = Base64.getDecoder().decode(highResBase64);
        assertArrayEquals(sampleImageBytes, decoded);
    }

    @Test
    void testMainPhotoPromotionAndReplacement() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "file", "photo1.jpg", "image/jpeg", sampleImageBytes
        );
        ProductPhoto photo1 = photoService.uploadPhoto("seller1", testProduct.getId(), file1, true);
        assertTrue(photo1.isMain());

        // Upload a second image with isMain=true
        BufferedImage img2 = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        ImageIO.write(img2, "jpg", baos2);
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "photo2.jpg", "image/jpeg", baos2.toByteArray()
        );
        ProductPhoto photo2 = photoService.uploadPhoto("seller1", testProduct.getId(), file2, true);

        // Photo 1 is no longer main, Photo 2 is now main
        ProductPhoto reloaded1 = productPhotoRepository.findById(photo1.getId()).orElseThrow();
        ProductPhoto reloaded2 = productPhotoRepository.findById(photo2.getId()).orElseThrow();
        assertFalse(reloaded1.isMain());
        assertTrue(reloaded2.isMain());
    }

    @Test
    void testAddProductLinksSellerAutomatically() throws Exception {
        String productJson = """
                {
                    "name": "iPad Mini 6",
                    "price": 499,
                    "stock": 20
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/add/product")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(productJson)
                        .with(user("bob_seller").roles("seller")))
                .andExpect(status().isCreated());

        Product savedProduct = productsRepository.findByName("iPad Mini 6").orElseThrow();
        assertTrue(sellerProductRepository.existsBySellerUsernameAndProductId("bob_seller", savedProduct.getId()));

        // bob_seller can immediately upload a photo
        MockMultipartFile file = new MockMultipartFile(
                "file", "ipad.jpg", "image/jpeg", sampleImageBytes
        );
        mockMvc.perform(multipart("/products/" + savedProduct.getId() + "/photos")
                        .file(file)
                        .with(user("bob_seller").roles("seller")))
                .andExpect(status().isCreated());
    }
}
