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
    private final AddressDao addressDao;
    private final StoreOrderDao storeOrderDao;
    private final StoreOrderItemDao storeOrderItemDao;

    public CustomerStoreController(
            ItemDao itemDao,
            CartItemDao cartItemDao,
            AddressDao addressDao,
            StoreOrderDao storeOrderDao,
            StoreOrderItemDao storeOrderItemDao) {
        this.itemDao = itemDao;
        this.cartItemDao = cartItemDao;
        this.addressDao = addressDao;
        this.storeOrderDao = storeOrderDao;
        this.storeOrderItemDao = storeOrderItemDao;
    }

    @GetMapping("/items")
    public ResponseEntity<?> listItems(@RequestParam(required = false) String q, HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Customer login required."));
        }

        List<Item> items = (q == null || q.isBlank()) ? itemDao.findAll() : itemDao.search(q.trim());
        return ResponseEntity.ok(items.stream().map(this::toItemResponse).toList());
    }

    @GetMapping("/cart")
    public ResponseEntity<?> getCart(HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Customer login required."));
        }
        return ResponseEntity.ok(buildCart(customerId));
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

        CartItem cartItem = cartItemDao.findByCustomerIdAndItemId(customerId, request.itemId()).orElseGet(CartItem::new);
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

    @GetMapping("/addresses")
    public ResponseEntity<?> getAddresses(HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Customer login required."));
        }
        List<AddressResponse> addresses = addressDao.findByCustomerId(customerId).stream().map(this::toAddressResponse).toList();
        return ResponseEntity.ok(addresses);
    }

    @PostMapping("/addresses")
    public ResponseEntity<?> addAddress(@RequestBody AddressRequest request, HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Customer login required."));
        }

        String error = validateAddressRequest(request);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("message", error));
        }

        Address address = toAddress(customerId, request);
        addressDao.save(address);
        return ResponseEntity.status(HttpStatus.CREATED).body(toAddressResponse(address));
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody CheckoutRequest request, HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Customer login required."));
        }

        CartResponse cart = buildCart(customerId);
        if (cart.items().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cart is empty."));
        }

        Address shippingAddress = resolveCheckoutAddress(customerId, request);
        if (shippingAddress == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Valid shipping address is required."));
        }

        StoreOrder order = new StoreOrder();
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.ORDERED);
        order.setTotalAmount(cart.total());
        order.setShippingFullName(shippingAddress.getFullName());
        order.setShippingPhone(shippingAddress.getPhone());
        order.setShippingLine1(shippingAddress.getLine1());
        order.setShippingLine2(shippingAddress.getLine2());
        order.setShippingCity(shippingAddress.getCity());
        order.setShippingState(shippingAddress.getState());
        order.setShippingPostalCode(shippingAddress.getPostalCode());
        order.setShippingCountry(shippingAddress.getCountry());
        storeOrderDao.save(order);

        List<StoreOrderItem> orderItems = cart.items().stream().map(ci -> {
            StoreOrderItem item = new StoreOrderItem();
            item.setOrderId(order.getId());
            item.setItemId(ci.itemId());
            item.setItemName(ci.itemName());
            item.setItemPrice(ci.itemPrice());
            item.setQuantity(ci.quantity());
            item.setLineTotal(ci.lineTotal());
            item.setImageUrl(firstOrEmpty(ci.imageUrls()));
            return item;
        }).toList();
        storeOrderItemDao.saveAll(orderItems);
        cartItemDao.deleteByCustomerId(customerId);

        return ResponseEntity.ok(Map.of(
                "message", "Order placed successfully.",
                "orderId", String.valueOf(order.getId()),
                "status", order.getStatus().name()));
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getOrders(HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Customer login required."));
        }

        List<StoreOrder> orders = storeOrderDao.findByCustomerId(customerId);
        List<Long> orderIds = orders.stream().map(StoreOrder::getId).toList();
        Map<Long, List<OrderItemResponse>> itemsByOrderId = storeOrderItemDao.findByOrderIds(orderIds).stream()
                .map(this::toOrderItemResponse)
                .collect(Collectors.groupingBy(OrderItemResponse::orderId));

        List<OrderResponse> response = orders.stream()
                .map(order -> new OrderResponse(
                        order.getId(),
                        order.getStatus().name(),
                        order.getTotalAmount(),
                        order.getCreatedAt(),
                        toShippingAddressResponse(order),
                        itemsByOrderId.getOrDefault(order.getId(), List.of())))
                .toList();

        return ResponseEntity.ok(response);
    }

    private CartResponse buildCart(Long customerId) {
        List<CartItem> cartItems = cartItemDao.findByCustomerId(customerId);
        List<Long> itemIds = cartItems.stream().map(CartItem::getItemId).toList();
        Map<Long, Item> itemMap = itemDao.findAllByIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        List<CartItemResponse> entries = cartItems.stream()
                .map(cartItem -> toCartResponse(cartItem, itemMap.get(cartItem.getItemId())))
                .filter(entry -> entry != null)
                .toList();

        BigDecimal total = entries.stream().map(CartItemResponse::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(entries, total);
    }

    private Address resolveCheckoutAddress(Long customerId, CheckoutRequest request) {
        if (request.addressId() != null) {
            return addressDao.findByIdAndCustomerId(request.addressId(), customerId).orElse(null);
        }
        String validationError = validateAddressRequest(request.address());
        if (validationError != null) {
            return null;
        }

        Address newAddress = toAddress(customerId, request.address());
        if (Boolean.TRUE.equals(request.saveForLater())) {
            addressDao.save(newAddress);
        }
        return newAddress;
    }

    private String validateAddressRequest(AddressRequest request) {
        if (request == null) {
            return "Address details are required.";
        }
        if (isBlank(request.fullName()) || isBlank(request.phone()) || isBlank(request.line1())
                || isBlank(request.city()) || isBlank(request.state())
                || isBlank(request.postalCode()) || isBlank(request.country())) {
            return "Please fill all required address fields.";
        }
        return null;
    }

    private Address toAddress(Long customerId, AddressRequest request) {
        Address address = new Address();
        address.setCustomerId(customerId);
        address.setFullName(request.fullName().trim());
        address.setPhone(request.phone().trim());
        address.setLine1(request.line1().trim());
        address.setLine2(request.line2() == null ? "" : request.line2().trim());
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setPostalCode(request.postalCode().trim());
        address.setCountry(request.country().trim());
        return address;
    }

    private Long customerId(HttpSession session) {
        Object value = session.getAttribute("customerId");
        if (value instanceof Long id) {
            return id;
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstOrEmpty(List<String> values) {
        return (values == null || values.isEmpty()) ? "" : values.get(0);
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

    private AddressResponse toAddressResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getFullName(),
                address.getPhone(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.getCreatedAt());
    }

    private ShippingAddressResponse toShippingAddressResponse(StoreOrder order) {
        return new ShippingAddressResponse(
                order.getShippingFullName(),
                order.getShippingPhone(),
                order.getShippingLine1(),
                order.getShippingLine2(),
                order.getShippingCity(),
                order.getShippingState(),
                order.getShippingPostalCode(),
                order.getShippingCountry());
    }

    private OrderItemResponse toOrderItemResponse(StoreOrderItem item) {
        return new OrderItemResponse(
                item.getOrderId(),
                item.getItemId(),
                item.getItemName(),
                item.getItemPrice(),
                item.getQuantity(),
                item.getLineTotal(),
                item.getImageUrl());
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

    public record AddressRequest(
            String fullName,
            String phone,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String country) {
    }

    public record CheckoutRequest(Long addressId, AddressRequest address, Boolean saveForLater) {
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

    public record AddressResponse(
            Long id,
            String fullName,
            String phone,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String country,
            String createdAt) {
    }

    public record ShippingAddressResponse(
            String fullName,
            String phone,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String country) {
    }

    public record OrderItemResponse(
            Long orderId,
            Long itemId,
            String itemName,
            BigDecimal itemPrice,
            Integer quantity,
            BigDecimal lineTotal,
            String imageUrl) {
    }

    public record OrderResponse(
            Long id,
            String status,
            BigDecimal totalAmount,
            String createdAt,
            ShippingAddressResponse shippingAddress,
            List<OrderItemResponse> items) {
    }
}
