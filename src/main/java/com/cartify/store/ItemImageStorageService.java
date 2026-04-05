package com.cartify.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ItemImageStorageService {

    private final Path uploadDir = Path.of("uploads");

    public List<String> saveImages(MultipartFile[] images) throws IOException {
        List<String> storedPaths = new ArrayList<>();
        if (images == null || images.length == 0) {
            return storedPaths;
        }

        Files.createDirectories(uploadDir);

        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) {
                continue;
            }
            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                continue;
            }

            String extension = fileExtension(image.getOriginalFilename());
            String filename = UUID.randomUUID() + extension;
            Path destination = uploadDir.resolve(filename);
            Files.copy(image.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            storedPaths.add("/uploads/" + filename);
        }
        return storedPaths;
    }

    private String fileExtension(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.'));
    }
}
