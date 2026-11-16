package com.example.inventoryservice.consumer;

import com.example.inventoryservice.entity.Product;
import com.example.inventoryservice.event.InventoryResponseEvent;
import com.example.inventoryservice.event.OrderPlacedEvent;
import com.example.inventoryservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryOrderConsumer {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, InventoryResponseEvent> inventoryKafkaTemplate;

    @Value("${app.kafka.topics.inventory-response-events:inventory-response-events}")
    private String inventoryResponseEventsTopic;

    @KafkaListener(
            topics = "${app.kafka.topics.order-events:order-events}",
            groupId = "${spring.kafka.consumer.group-id:inventory-group}",
            containerFactory = "orderPlacedKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleOrderPlacedEvent(@Payload OrderPlacedEvent event) {
        log.info(">>> [INVENTORY SERVICE KAFKA CONSUMER] Received OrderPlacedEvent for orderId: {}, productId: {}, quantity: {}",
                event.getOrderId(), event.getProductId(), event.getQuantity());

        if (event.getProductId() == null || event.getQuantity() == null || event.getQuantity() <= 0) {
            sendResponse(event.getOrderId(), event.getProductId(), event.getQuantity(), "CANCELLED", BigDecimal.ZERO, "Invalid order data");
            return;
        }

        Optional<Product> productOpt = productRepository.findById(event.getProductId());

        if (productOpt.isEmpty()) {
            log.warn(">>> [INVENTORY SERVICE] Product not found with id: {}", event.getProductId());
            sendResponse(event.getOrderId(), event.getProductId(), event.getQuantity(), "CANCELLED", BigDecimal.ZERO, "Product not found");
            return;
        }

        Product product = productOpt.get();

        if (product.getQuantity() == null || product.getQuantity() < event.getQuantity()) {
            log.warn(">>> [INVENTORY SERVICE] Insufficient stock for productId: {}. Available: {}, Requested: {}",
                    product.getId(), product.getQuantity(), event.getQuantity());
            sendResponse(event.getOrderId(), event.getProductId(), event.getQuantity(), "CANCELLED", BigDecimal.ZERO,
                    String.format("Insufficient stock. Available: %d, Requested: %d",
                            product.getQuantity() != null ? product.getQuantity() : 0, event.getQuantity()));
            return;
        }

        // Đủ hàng: Trừ kho & tính tổng tiền
        product.setQuantity(product.getQuantity() - event.getQuantity());
        productRepository.save(product);

        BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(event.getQuantity()));

        log.info(">>> [INVENTORY SERVICE] Stock deducted successfully for productId: {}. Remaining stock: {}, Total: {}",
                product.getId(), product.getQuantity(), totalPrice);

        sendResponse(event.getOrderId(), event.getProductId(), event.getQuantity(), "COMPLETED", totalPrice, "Order processed and stock deducted successfully");
    }

    private void sendResponse(Long orderId, Long productId, Integer quantity, String status, BigDecimal totalPrice, String message) {
        InventoryResponseEvent responseEvent = InventoryResponseEvent.builder()
                .orderId(orderId)
                .productId(productId)
                .quantity(quantity)
                .status(status)
                .totalPrice(totalPrice)
                .message(message)
                .build();

        try {
            inventoryKafkaTemplate.send(inventoryResponseEventsTopic, String.valueOf(orderId), responseEvent);
            log.info(">>> [KAFKA PRODUCER] Published InventoryResponseEvent for orderId: {} with status: {} to topic '{}'",
                    orderId, status, inventoryResponseEventsTopic);
        } catch (Exception e) {
            log.error(">>> [KAFKA PRODUCER ERROR] Failed to send inventory response event: {}", e.getMessage());
        }
    }
}
