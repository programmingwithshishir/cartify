package com.cartify.store;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 7000;

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

    public String downloadImageFromUrl(String sourceUrl) throws IOException {
        Files.createDirectories(uploadDir);

        URI uri = URI.create(sourceUrl);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.connect();

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Failed to download image. HTTP " + code);
        }

        String extension = fileExtensionFromUrl(sourceUrl);
        String filename = UUID.randomUUID() + extension;
        Path destination = uploadDir.resolve(filename);

        try (InputStream in = connection.getInputStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
        }

        return "/uploads/" + filename;
    }

    public String createPlaceholderImage(String title, String tags) throws IOException {
        Files.createDirectories(uploadDir);
        String filename = UUID.randomUUID() + ".svg";
        Path destination = uploadDir.resolve(filename);

        String[] palette = paletteForTags(tags);
        String safeTitle = escapeXml(shorten(title, 30));
        String safeTags = escapeXml(shorten(tags == null ? "" : tags.replace(",", " • "), 38));

        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="800" height="800" viewBox="0 0 800 800">
                  <defs>
                    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%%" stop-color="%s"/>
                      <stop offset="100%%" stop-color="%s"/>
                    </linearGradient>
                  </defs>
                  <rect width="800" height="800" fill="url(#g)"/>
                  <circle cx="680" cy="130" r="120" fill="rgba(255,255,255,0.12)"/>
                  <circle cx="120" cy="680" r="160" fill="rgba(255,255,255,0.08)"/>
                  <text x="60" y="410" fill="#ffffff" font-family="Segoe UI, Arial, sans-serif" font-size="52" font-weight="700">%s</text>
                  <text x="60" y="470" fill="#eaf2ff" font-family="Segoe UI, Arial, sans-serif" font-size="28">%s</text>
                </svg>
                """.formatted(palette[0], palette[1], safeTitle, safeTags);

        Files.writeString(destination, svg, StandardCharsets.UTF_8);
        return "/uploads/" + filename;
    }

    public void deleteLocalImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        for (String url : imageUrls) {
            if (url == null || !url.startsWith("/uploads/")) {
                continue;
            }
            String filename = url.substring("/uploads/".length()).trim();
            if (filename.isBlank() || filename.contains("..")) {
                continue;
            }
            try {
                Files.deleteIfExists(uploadDir.resolve(filename));
            } catch (IOException ignored) {
                // Keep API flow resilient even if cleanup fails.
            }
        }
    }

    public boolean isLocalImageAvailable(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/uploads/")) {
            return false;
        }
        String filename = imageUrl.substring("/uploads/".length()).trim();
        if (filename.isBlank() || filename.contains("..")) {
            return false;
        }
        return Files.exists(uploadDir.resolve(filename));
    }

    private String fileExtension(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.'));
    }

    private String fileExtensionFromUrl(String sourceUrl) {
        try {
            String path = URI.create(sourceUrl).getPath();
            if (path == null || !path.contains(".")) {
                return ".jpg";
            }
            String ext = path.substring(path.lastIndexOf('.'));
            if (ext.length() > 6) {
                return ".jpg";
            }
            return ext;
        } catch (Exception ex) {
            return ".jpg";
        }
    }

    private String[] paletteForTags(String tags) {
        String t = tags == null ? "" : tags.toLowerCase();
        if (t.contains("electronics")) {
            return new String[]{"#0b3c8a", "#0ea5e9"};
        }
        if (t.contains("fashion") || t.contains("women") || t.contains("men")) {
            return new String[]{"#9a3412", "#f97316"};
        }
        if (t.contains("jewelry") || t.contains("luxury")) {
            return new String[]{"#854d0e", "#eab308"};
        }
        if (t.contains("sports") || t.contains("running")) {
            return new String[]{"#14532d", "#22c55e"};
        }
        return new String[]{"#374151", "#3b82f6"};
    }

    private String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }

    private String escapeXml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
