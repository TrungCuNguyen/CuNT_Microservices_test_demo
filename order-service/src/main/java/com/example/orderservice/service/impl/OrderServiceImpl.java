package com.example.orderservice.service.impl;

import com.example.orderservice.client.InventoryClient;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.dto.ProductDto;
import com.example.orderservice.entity.Order;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.service.OrderService;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    public OrderServiceImpl(OrderRepository orderRepository, InventoryClient inventoryClient) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        if (request.getProductId() == null || request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ProductId and valid positive quantity are required");
        }

        ProductDto product;
        try {
            log.info("Calling Inventory Service via OpenFeign for productId: {}", request.getProductId());
            product = inventoryClient.getProductById(request.getProductId());
        } catch (FeignException.NotFound e) {
            log.warn("Product not found in Inventory Service: {}", request.getProductId());
            Order cancelledOrder = Order.builder()
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .status("CANCELLED")
                    .totalPrice(BigDecimal.ZERO)
                    .build();
            Order saved = orderRepository.save(cancelledOrder);
            return OrderResponse.builder()
                    .id(saved.getId())
                    .productId(saved.getProductId())
                    .quantity(saved.getQuantity())
                    .status("CANCELLED")
                    .totalPrice(BigDecimal.ZERO)
                    .message("Product not found with id: " + request.getProductId())
                    .build();
        } catch (Exception e) {
            log.error("Failed to communicate with Inventory Service: {}", e.getMessage());
            Order cancelledOrder = Order.builder()
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .status("CANCELLED")
                    .totalPrice(BigDecimal.ZERO)
                    .build();
            Order saved = orderRepository.save(cancelledOrder);
            return OrderResponse.builder()
                    .id(saved.getId())
                    .productId(saved.getProductId())
                    .quantity(saved.getQuantity())
                    .status("CANCELLED")
                    .totalPrice(BigDecimal.ZERO)
                    .message("Unable to verify inventory: " + e.getMessage())
                    .build();
        }

        // Kiem tra so luong ton kho
        if (product.getQuantity() == null || product.getQuantity() < request.getQuantity()) {
            log.warn("Insufficient stock for productId: {}. Available: {}, Requested: {}",
                    request.getProductId(), product.getQuantity(), request.getQuantity());

            Order cancelledOrder = Order.builder()
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .status("CANCELLED")
                    .totalPrice(BigDecimal.ZERO)
                    .build();
            Order saved = orderRepository.save(cancelledOrder);

            return OrderResponse.builder()
                    .id(saved.getId())
                    .productId(saved.getProductId())
                    .quantity(saved.getQuantity())
                    .status("CANCELLED")
                    .totalPrice(BigDecimal.ZERO)
                    .message(String.format("Insufficient stock. Available: %d, Requested: %d",
                            product.getQuantity() != null ? product.getQuantity() : 0, request.getQuantity()))
                    .build();
        }

        // Tinh tong tien = don gia * so luong
        BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));

        // Tru kho thong qua Inventory Service
        try {
            inventoryClient.deductStock(request.getProductId(), request.getQuantity());
            log.info("Deducted {} items for productId: {} from inventory", request.getQuantity(), request.getProductId());
        } catch (Exception e) {
            log.error("Failed to deduct stock from Inventory Service: {}", e.getMessage());
            Order cancelledOrder = Order.builder()
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .status("CANCELLED")
                    .totalPrice(BigDecimal.ZERO)
                    .build();
            Order saved = orderRepository.save(cancelledOrder);
            return OrderResponse.builder()
                    .id(saved.getId())
                    .productId(saved.getProductId())
                    .quantity(saved.getQuantity())
                    .status("CANCELLED")
                    .totalPrice(BigDecimal.ZERO)
                    .message("Stock deduction failed: " + e.getMessage())
                    .build();
        }

        Order order = Order.builder()
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .status("COMPLETED")
                .totalPrice(totalPrice)
                .build();

        Order saved = orderRepository.save(order);
        log.info("Order created successfully with id: {}, total_price: {}", saved.getId(), saved.getTotalPrice());

        return OrderResponse.builder()
                .id(saved.getId())
                .productId(saved.getProductId())
                .quantity(saved.getQuantity())
                .status(saved.getStatus())
                .totalPrice(saved.getTotalPrice())
                .message("Order completed successfully")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with id: " + id));
        return mapToResponse(order);
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .status(order.getStatus())
                .totalPrice(order.getTotalPrice())
                .message("Order status: " + order.getStatus())
                .build();
    }
}

