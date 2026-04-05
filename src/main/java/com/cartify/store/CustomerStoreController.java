package com.cartify.store;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/customer")
public class CustomerStoreController {

    private final ItemDao itemDao;
    private final CartItemDao cartItemDao;

    public CustomerStoreController(ItemDao itemDao, CartItemDao cartItemDao) {
        this.itemDao = itemDao;
        this.cartItemDao = cartItemDao;
    }

    @GetMapping("/items")
    public ResponseEntity<?> listItems(@RequestParam(required = false) String q, HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Customer login required."));
        }

        List<Item> items = (q == null || q.isBlank())
                ? itemDao.findAll()
                : itemDao.search(q.trim());

        return ResponseEntity.ok(items.stream().map(this::toItemResponse).toList());
    }

    @GetMapping("/cart")
    public ResponseEntity<?> getCart(HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Customer login required."));
        }

        List<CartItem> cartItems = cartItemDao.findByCustomerId(customerId);
        List<Long> itemIds = cartItems.stream().map(CartItem::getItemId).toList();
        Map<Long, Item> itemMap = itemDao.findAllByIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        List<CartItemResponse> entries = cartItems.stream()
                .map(cartItem -> toCartResponse(cartItem, itemMap.get(cartItem.getItemId())))
                .filter(entry -> entry != null)
                .toList();

        BigDecimal total = entries.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResponseEntity.ok(new CartResponse(entries, total));
    }

    @PostMapping("/cart")
    public ResponseEntity<?> addToCart(@RequestBody AddToCartRequest request, HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Customer login required."));
        }

        if (request.itemId() == null || request.quantity() == null || request.quantity() < 1) {
            return ResponseEntity.badRequest().body(Map.of("message", "Valid item and quantity are required."));
        }

        if (!itemDao.existsById(request.itemId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Item not found."));
        }

        CartItem cartItem = cartItemDao.findByCustomerIdAndItemId(customerId, request.itemId())
                .orElseGet(CartItem::new);
        if (cartItem.getId() == null) {
            cartItem.setCustomerId(customerId);
            cartItem.setItemId(request.itemId());
            cartItem.setQuantity(request.quantity());
        } else {
            cartItem.setQuantity(cartItem.getQuantity() + request.quantity());
        }

        cartItemDao.save(cartItem);
        return ResponseEntity.ok(Map.of("message", "Item added to cart."));
    }

    @PutMapping("/cart/{cartItemId}")
    public ResponseEntity<?> updateCartQuantity(
            @PathVariable Long cartItemId,
            @RequestBody UpdateCartQuantityRequest request,
            HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Customer login required."));
        }

        if (request.quantity() == null || request.quantity() < 1) {
            return ResponseEntity.badRequest().body(Map.of("message", "Quantity must be at least 1."));
        }

        CartItem cartItem = cartItemDao.findByIdAndCustomerId(cartItemId, customerId).orElse(null);
        if (cartItem == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cart item not found."));
        }

        cartItem.setQuantity(request.quantity());
        cartItemDao.save(cartItem);
        return ResponseEntity.ok(Map.of("message", "Cart updated."));
    }

    @DeleteMapping("/cart/{cartItemId}")
    public ResponseEntity<?> deleteCartItem(@PathVariable Long cartItemId, HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Customer login required."));
        }

        CartItem cartItem = cartItemDao.findByIdAndCustomerId(cartItemId, customerId).orElse(null);
        if (cartItem == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cart item not found."));
        }

        cartItemDao.delete(cartItem);
        return ResponseEntity.ok(Map.of("message", "Item removed from cart."));
    }

    private Long customerId(HttpSession session) {
        Object value = session.getAttribute("customerId");
        if (value instanceof Long id) {
            return id;
        }
        return null;
    }

    private ItemResponse toItemResponse(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                splitCommaSeparated(item.getImageUrls()),
                splitCommaSeparated(item.getCategoryTags()));
    }

    private CartItemResponse toCartResponse(CartItem cartItem, Item item) {
        if (item == null) {
            return null;
        }
        BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        return new CartItemResponse(
                cartItem.getId(),
                item.getId(),
                item.getName(),
                item.getPrice(),
                cartItem.getQuantity(),
                lineTotal,
                splitCommaSeparated(item.getImageUrls()));
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

    public record AddToCartRequest(Long itemId, Integer quantity) {
    }

    public record UpdateCartQuantityRequest(Integer quantity) {
    }

    public record ItemResponse(
            Long id,
            String name,
            String description,
            BigDecimal price,
            List<String> imageUrls,
            List<String> categoryTags) {
    }

    public record CartItemResponse(
            Long cartItemId,
            Long itemId,
            String itemName,
            BigDecimal itemPrice,
            Integer quantity,
            BigDecimal lineTotal,
            List<String> imageUrls) {
    }

    public record CartResponse(List<CartItemResponse> items, BigDecimal total) {
    }
}
