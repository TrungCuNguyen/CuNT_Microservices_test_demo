package com.example.orderservice.config;

import com.example.orderservice.event.InventoryResponseEvent;
import com.example.orderservice.event.OrderPlacedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${app.kafka.topics.order-events:order-events}")
    private String orderEventsTopic;

    @Value("${app.kafka.topics.inventory-response-events:inventory-response-events}")
    private String inventoryResponseEventsTopic;

    @Value("${spring.kafka.consumer.group-id:order-group}")
    private String orderGroupId;

    // 1. Topic definitions
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(orderEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryResponseEventsTopic() {
        return TopicBuilder.name(inventoryResponseEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // 2. Producer configuration for OrderPlacedEvent
    @Bean
    public ProducerFactory<String, OrderPlacedEvent> orderPlacedEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, OrderPlacedEvent> orderKafkaTemplate() {
        return new KafkaTemplate<>(orderPlacedEventProducerFactory());
    }

    // 3. Consumer configuration for InventoryResponseEvent
    @Bean
    public ConsumerFactory<String, InventoryResponseEvent> inventoryResponseConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, orderGroupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<InventoryResponseEvent> jsonDeserializer = new JsonDeserializer<>(InventoryResponseEvent.class);
        jsonDeserializer.addTrustedPackages("*");
        jsonDeserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(jsonDeserializer)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryResponseEvent> inventoryResponseKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InventoryResponseEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(inventoryResponseConsumerFactory());
        return factory;
    }
}
