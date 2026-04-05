package com.cartify.store;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/admin/items")
public class AdminItemController {

    private final ItemDao itemDao;
    private final ItemImageStorageService imageStorageService;

    public AdminItemController(ItemDao itemDao, ItemImageStorageService imageStorageService) {
        this.itemDao = itemDao;
        this.imageStorageService = imageStorageService;
    }

    @GetMapping
    public ResponseEntity<?> listItems(@RequestParam(required = false) String q, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Admin login required."));
        }

        List<Item> items = (q == null || q.isBlank())
                ? itemDao.findAll()
                : itemDao.search(q.trim());

        List<ItemResponse> response = items.stream().map(this::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createItem(
            @RequestParam String name,
            @RequestParam String description,
            @RequestParam BigDecimal price,
            @RequestParam String categoryTags,
            @RequestParam(value = "images", required = false) MultipartFile[] images,
            HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Admin login required."));
        }

        String basicValidationError = validateBasicFields(name, description, price, categoryTags);
        if (basicValidationError != null) {
            return ResponseEntity.badRequest().body(Map.of("message", basicValidationError));
        }

        List<String> imageUrls;
        try {
            imageUrls = imageStorageService.saveImages(images);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to save image files."));
        }

        if (imageUrls.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please upload at least one image file."));
        }

        Item item = new Item();
        item.setName(name.trim());
        item.setDescription(description.trim());
        item.setPrice(price);
        item.setImageUrls(joinCommaSeparated(imageUrls));
        item.setCategoryTags(normalizeCommaSeparated(categoryTags));
        itemDao.save(item);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(item));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateItem(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam String description,
            @RequestParam BigDecimal price,
            @RequestParam String categoryTags,
            @RequestParam(required = false, defaultValue = "") String existingImageUrls,
            @RequestParam(value = "images", required = false) MultipartFile[] images,
            HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Admin login required."));
        }

        String basicValidationError = validateBasicFields(name, description, price, categoryTags);
        if (basicValidationError != null) {
            return ResponseEntity.badRequest().body(Map.of("message", basicValidationError));
        }

        Item item = itemDao.findById(id).orElse(null);
        if (item == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Item not found."));
        }

        List<String> uploadedUrls;
        try {
            uploadedUrls = imageStorageService.saveImages(images);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to save image files."));
        }

        List<String> finalImages = uploadedUrls.isEmpty()
                ? splitCommaSeparated(existingImageUrls)
                : uploadedUrls;

        if (finalImages.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please keep or upload at least one image."));
        }

        if (!uploadedUrls.isEmpty()) {
            imageStorageService.deleteLocalImages(splitCommaSeparated(item.getImageUrls()));
        }

        item.setName(name.trim());
        item.setDescription(description.trim());
        item.setPrice(price);
        item.setImageUrls(joinCommaSeparated(finalImages));
        item.setCategoryTags(normalizeCommaSeparated(categoryTags));
        itemDao.save(item);
        return ResponseEntity.ok(toResponse(item));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteItem(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Admin login required."));
        }

        Item item = itemDao.findById(id).orElse(null);
        if (item == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Item not found."));
        }

        imageStorageService.deleteLocalImages(splitCommaSeparated(item.getImageUrls()));
        itemDao.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Item deleted."));
    }

    private boolean isAdmin(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute("isAdmin"));
    }

    private String validateBasicFields(String name, String description, BigDecimal price, String categoryTags) {
        if (name == null || name.isBlank()) {
            return "Item name is required.";
        }
        if (description == null || description.isBlank()) {
            return "Item description is required.";
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            return "Price must be zero or more.";
        }
        if (categoryTags == null || categoryTags.isBlank()) {
            return "At least one category tag is required.";
        }
        return null;
    }

    private String normalizeCommaSeparated(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .collect(Collectors.joining(","));
    }

    private String joinCommaSeparated(List<String> values) {
        return values.stream()
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .collect(Collectors.joining(","));
    }

    private ItemResponse toResponse(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                splitCommaSeparated(item.getImageUrls()),
                splitCommaSeparated(item.getCategoryTags()));
    }

    private List<String> splitCommaSeparated(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .toList();
    }

    public record ItemResponse(
            Long id,
            String name,
            String description,
            BigDecimal price,
            List<String> imageUrls,
            List<String> categoryTags) {
    }
}
