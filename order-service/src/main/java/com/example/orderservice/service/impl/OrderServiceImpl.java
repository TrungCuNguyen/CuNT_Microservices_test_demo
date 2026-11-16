package com.example.orderservice.service.impl;

import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.entity.Order;
import com.example.orderservice.event.OrderPlacedEvent;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderPlacedEvent> orderKafkaTemplate;

    @Value("${app.kafka.topics.order-events:order-events}")
    private String orderEventsTopic;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        if (request.getProductId() == null || request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ProductId and valid positive quantity are required");
        }

        // 1. Tạo đơn hàng ban đầu với trạng thái PENDING
        Order order = Order.builder()
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .status("PENDING")
                .totalPrice(BigDecimal.ZERO)
                .build();

        Order savedOrder = orderRepository.save(order);
        log.info("==> [ORDER SERVICE] Created Order #{} with status PENDING", savedOrder.getId());

        // 2. Tạo sự kiện OrderPlacedEvent
        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .orderId(savedOrder.getId())
                .productId(savedOrder.getProductId())
                .quantity(savedOrder.getQuantity())
                .timestamp(System.currentTimeMillis())
                .build();

        // 3. Gửi event vào Kafka topic 'order-events' bất đồng bộ
        try {
            orderKafkaTemplate.send(orderEventsTopic, String.valueOf(savedOrder.getId()), event);
            log.info("==> [KAFKA PRODUCER] Published OrderPlacedEvent for orderId: {} to topic '{}'",
                    savedOrder.getId(), orderEventsTopic);
        } catch (Exception e) {
            log.error("==> [KAFKA PRODUCER ERROR] Failed to publish order event to Kafka: {}", e.getMessage());
        }

        // 4. Trả về kết quả ngay lập tức cho client
        return OrderResponse.builder()
                .id(savedOrder.getId())
                .productId(savedOrder.getProductId())
                .quantity(savedOrder.getQuantity())
                .status(savedOrder.getStatus())
                .totalPrice(savedOrder.getTotalPrice())
                .message("Order placed with status PENDING. Inventory verification and stock deduction in progress via Kafka.")
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
