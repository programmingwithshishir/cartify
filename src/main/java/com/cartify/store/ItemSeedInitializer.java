package com.cartify.store;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ItemSeedInitializer implements CommandLineRunner {

    private static final int MIN_ITEMS = 50;

    private final ItemDao itemDao;
    private final ItemImageStorageService imageStorageService;

    public ItemSeedInitializer(ItemDao itemDao, ItemImageStorageService imageStorageService) {
        this.itemDao = itemDao;
        this.imageStorageService = imageStorageService;
    }

    @Override
    public void run(String... args) {
        int existing = itemDao.count();

        List<SeedTemplate> templates = List.of(
                new SeedTemplate("Travel Backpack", "Durable backpack for travel and work.",
                        new BigDecimal("49.99"), "https://fakestoreapi.com/img/81fPKd-2AYL._AC_SL1500_.jpg", "bags,travel"),
                new SeedTemplate("Casual Cotton Shirt", "Comfort-first shirt for daily wear.",
                        new BigDecimal("19.99"), "https://fakestoreapi.com/img/71YXzeOuslL._AC_UY879_.jpg", "fashion,shirt,men"),
                new SeedTemplate("Bomber Jacket", "Warm and stylish jacket for winter.",
                        new BigDecimal("59.99"), "https://fakestoreapi.com/img/71li-ujtlUL._AC_UX679_.jpg", "fashion,jacket,winter"),
                new SeedTemplate("Premium Ring", "Elegant jewelry piece with modern finish.",
                        new BigDecimal("139.99"), "https://fakestoreapi.com/img/71ya4mJqRNL._AC_UL640_QL65_ML3_.jpg", "jewelry,ring"),
                new SeedTemplate("Portable SSD", "Fast storage for work, gaming, and backups.",
                        new BigDecimal("89.99"), "https://fakestoreapi.com/img/61U7T1koQqL._AC_SX679_.jpg", "electronics,storage"),
                new SeedTemplate("Gaming Monitor", "Sharp display with smooth refresh rate.",
                        new BigDecimal("249.99"), "https://fakestoreapi.com/img/81QpkIctqPL._AC_SX679_.jpg", "electronics,monitor,gaming"),
                new SeedTemplate("Bluetooth Headphones", "Wireless headphones with balanced sound.",
                        new BigDecimal("54.99"), "https://fakestoreapi.com/img/61mtL65D4cL._AC_SX679_.jpg", "electronics,audio"),
                new SeedTemplate("Women Casual Jacket", "Light jacket for daily casual outfits.",
                        new BigDecimal("42.99"), "https://fakestoreapi.com/img/71HblAHs5xL._AC_UY879_-2.jpg", "fashion,women,jacket"),
                new SeedTemplate("Summer Top", "Soft and breathable top for summer.",
                        new BigDecimal("17.99"), "https://fakestoreapi.com/img/51eg55uWmdL._AC_UX679_.jpg", "fashion,women,top"),
                new SeedTemplate("Analog Watch", "Classic analog watch for formal and casual style.",
                        new BigDecimal("109.99"), "https://fakestoreapi.com/img/61pHAEJ4NML._AC_UX679_.jpg", "accessories,watch"),
                new SeedTemplate("Running Shoes", "Lightweight shoes built for daily running.",
                        new BigDecimal("69.99"), "https://fakestoreapi.com/img/81Zt42ioCgL._AC_SX679_.jpg", "sports,shoes,running"),
                new SeedTemplate("Diamond Ring", "Premium ring crafted for special occasions.",
                        new BigDecimal("399.99"), "https://fakestoreapi.com/img/61sbMiUnoGL._AC_UL640_QL65_ML3_.jpg", "jewelry,luxury,ring"));

        List<String> variants = List.of("Classic", "Pro", "Lite", "Plus", "Max");
        List<SeedItem> generated = new ArrayList<>();

        for (SeedTemplate template : templates) {
            for (int i = 0; i < variants.size(); i++) {
                String name = template.baseName() + " " + variants.get(i);
                BigDecimal adjustedPrice = template.basePrice().add(BigDecimal.valueOf(i * 7L));
                generated.add(new SeedItem(
                        name,
                        template.description() + " (" + variants.get(i) + " variant)",
                        adjustedPrice,
                        template.imageUrl(),
                        template.tags()));
            }
        }

        for (SeedItem seed : generated) {
            if (existing >= MIN_ITEMS) {
                break;
            }
            if (itemDao.existsByName(seed.name())) {
                continue;
            }
            Item item = new Item();
            item.setName(seed.name());
            item.setDescription(seed.description());
            item.setPrice(seed.price());
            item.setImageUrls(toLocalOrRemote(seed.imageUrl(), seed.name(), seed.tags()));
            item.setCategoryTags(seed.tags());
            itemDao.save(item);
            existing++;
        }

        localizeExistingRemoteImages();
    }

    private void localizeExistingRemoteImages() {
        List<Item> items = itemDao.findAll();
        for (Item item : items) {
            List<String> urls = splitCommaSeparated(item.getImageUrls());
            boolean changed = false;
            List<String> mapped = new ArrayList<>();
            if (urls.isEmpty()) {
                mapped.add(createPlaceholderSafe(item.getName(), item.getCategoryTags()));
                changed = true;
            } else {
                for (String url : urls) {
                    if (isRemote(url)) {
                        String localized = toLocalOrRemote(url, item.getName(), item.getCategoryTags());
                        mapped.add(localized);
                        changed = changed || !localized.equals(url);
                    } else if (isLocalUpload(url) && !imageStorageService.isLocalImageAvailable(url)) {
                        mapped.add(createPlaceholderSafe(item.getName(), item.getCategoryTags()));
                        changed = true;
                    } else {
                        mapped.add(url);
                    }
                }
            }

            if (changed) {
                item.setImageUrls(joinCommaSeparated(mapped));
                itemDao.save(item);
            }
        }
    }

    private String toLocalOrRemote(String imageUrl) {
        return toLocalOrRemote(imageUrl, "", "");
    }

    private String toLocalOrRemote(String imageUrl, String title, String tags) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return createPlaceholderSafe(title, tags);
        }
        if (!isRemote(imageUrl)) {
            return imageUrl;
        }
        try {
            return imageStorageService.downloadImageFromUrl(imageUrl);
        } catch (Exception ex) {
            return createPlaceholderSafe(title, tags);
        }
    }

    private boolean isRemote(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private boolean isLocalUpload(String url) {
        return url != null && url.startsWith("/uploads/");
    }

    private String createPlaceholderSafe(String title, String tags) {
        try {
            return imageStorageService.createPlaceholderImage(title, tags);
        } catch (Exception ex) {
            return "";
        }
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

    private String joinCommaSeparated(List<String> values) {
        return values.stream()
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .collect(Collectors.joining(","));
    }

    private record SeedTemplate(
            String baseName,
            String description,
            BigDecimal basePrice,
            String imageUrl,
            String tags) {
    }

    private record SeedItem(
            String name,
            String description,
            BigDecimal price,
            String imageUrl,
            String tags) {
    }
}
