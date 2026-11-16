package com.example.orderservice.consumer;

import com.example.orderservice.entity.Order;
import com.example.orderservice.event.InventoryResponseEvent;
import com.example.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderResponseConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(
            topics = "${app.kafka.topics.inventory-response-events:inventory-response-events}",
            groupId = "${spring.kafka.consumer.group-id:order-group}",
            containerFactory = "inventoryResponseKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleInventoryResponse(@Payload InventoryResponseEvent event) {
        log.info(">>> [ORDER SERVICE KAFKA CONSUMER] Received InventoryResponseEvent for orderId: {}, status: {}",
                event.getOrderId(), event.getStatus());

        if (event.getOrderId() == null) {
            log.warn("Invalid event: orderId is null");
            return;
        }

        orderRepository.findById(event.getOrderId()).ifPresentOrElse(order -> {
            order.setStatus(event.getStatus());
            if (event.getTotalPrice() != null) {
                order.setTotalPrice(event.getTotalPrice());
            }
            orderRepository.save(order);
            log.info(">>> [ORDER SERVICE] Order #{} status successfully updated to '{}', total_price: {}",
                    order.getId(), order.getStatus(), order.getTotalPrice());
        }, () -> log.error("Order not found in database with id: {}", event.getOrderId()));
    }
}
