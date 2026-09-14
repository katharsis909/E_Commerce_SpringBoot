package com.microservices.ecommerce.Service;

import com.microservices.ecommerce.Model.Product;
import com.microservices.ecommerce.Model.ProductPhoto;
import com.microservices.ecommerce.Model.SellerProduct;
import com.microservices.ecommerce.Repository.ProductPhotoRepository;
import com.microservices.ecommerce.Repository.ProductsRepository;
import com.microservices.ecommerce.Repository.SellerProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

@Service
public class PhotoService {

    private final SellerProductRepository sellerProductRepository;
    private final ProductPhotoRepository productPhotoRepository;
    private final ProductsRepository productsRepository;
    private final String baseImageDir;

    @Autowired
    public PhotoService(SellerProductRepository sellerProductRepository,
                        ProductPhotoRepository productPhotoRepository,
                        ProductsRepository productsRepository,
                        @Value("${app.images.dir:./data/images}") String baseImageDir) {
        this.sellerProductRepository = sellerProductRepository;
        this.productPhotoRepository = productPhotoRepository;
        this.productsRepository = productsRepository;
        this.baseImageDir = baseImageDir;
    }

    @Transactional
    public ProductPhoto uploadPhoto(String sellerUsername, long productId, MultipartFile file, boolean isMain) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }

        if (sellerUsername == null || !sellerProductRepository.existsBySellerUsernameAndProductId(sellerUsername, productId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seller '" + sellerUsername + "' is not authorized for product ID: " + productId);
        }

        Product product = productsRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found with id: " + productId));

        BufferedImage originalImage;
        try {
            originalImage = ImageIO.read(file.getInputStream());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read image stream", e);
        }
        if (originalImage == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image format");
        }

        // Generate low-res thumbnail: max 150px maintaining aspect ratio
        int origWidth = originalImage.getWidth();
        int origHeight = originalImage.getHeight();
        int maxThumbSize = 150;
        int thumbWidth = origWidth;
        int thumbHeight = origHeight;

        if (origWidth > maxThumbSize || origHeight > maxThumbSize) {
            if (origWidth >= origHeight) {
                thumbWidth = maxThumbSize;
                thumbHeight = Math.max(1, (int) Math.round((double) origHeight * maxThumbSize / origWidth));
            } else {
                thumbHeight = maxThumbSize;
                thumbWidth = Math.max(1, (int) Math.round((double) origWidth * maxThumbSize / origHeight));
            }
        }

        BufferedImage thumbImage = new BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = thumbImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(originalImage, 0, 0, thumbWidth, thumbHeight, Color.WHITE, null);
        g2d.dispose();

        Path highResFolder = Paths.get(baseImageDir, "high_res");
        Path lowResFolder = Paths.get(baseImageDir, "low_res");

        try {
            Files.createDirectories(highResFolder);
            Files.createDirectories(lowResFolder);

            String filename = UUID.randomUUID() + ".jpg";
            Path highResPath = highResFolder.resolve(filename);
            Path lowResPath = lowResFolder.resolve(filename);

            // Write high-res file to disk
            Files.write(highResPath, file.getBytes());

            // Write low-res file to disk
            ImageIO.write(thumbImage, "jpg", lowResPath.toFile());

            // Determine isMain flag
            if (isMain || productPhotoRepository.findByProductIdAndIsMainTrue(productId).isEmpty()) {
                productPhotoRepository.clearMainPhotos(productId);
                isMain = true;
            }

            ProductPhoto photo = new ProductPhoto(product, lowResPath.toString(), highResPath.toString(), isMain);
            return productPhotoRepository.save(photo);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to persist image files to disk", e);
        }
    }

    public String readImageAsBase64(String filePath) {
        if (filePath == null) return null;
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(path);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public String getMainLowResBase64(long productId) {
        return productPhotoRepository.findByProductIdAndIsMainTrue(productId)
                .map(photo -> readImageAsBase64(photo.getLowResPath()))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public String getMainHighResBase64(long productId) {
        return productPhotoRepository.findByProductIdAndIsMainTrue(productId)
                .map(photo -> readImageAsBase64(photo.getHighResPath()))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getLowResBase64Map(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ProductPhoto> mainPhotos = productPhotoRepository.findByProductIdInAndIsMainTrue(productIds);
        Map<Long, String> map = new HashMap<>();
        for (ProductPhoto photo : mainPhotos) {
            String b64 = readImageAsBase64(photo.getLowResPath());
            if (b64 != null) {
                map.put(photo.getProduct().getId(), b64);
            }
        }
        return map;
    }

    @Transactional
    public void linkSellerToProduct(String sellerUsername, Product product) {
        if (sellerUsername != null && product != null && product.getId() != 0) {
            if (!sellerProductRepository.existsBySellerUsernameAndProductId(sellerUsername, product.getId())) {
                sellerProductRepository.save(new SellerProduct(sellerUsername, product));
            }
        }
    }

    public boolean isSellerAuthorized(String sellerUsername, long productId) {
        return sellerProductRepository.existsBySellerUsernameAndProductId(sellerUsername, productId);
    }
}
