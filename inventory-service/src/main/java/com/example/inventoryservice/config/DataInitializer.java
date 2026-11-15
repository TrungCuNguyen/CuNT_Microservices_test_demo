package com.example.inventoryservice.config;

import com.example.inventoryservice.entity.Product;
import com.example.inventoryservice.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner initData(ProductRepository productRepository) {
        return args -> {
            if (productRepository.count() == 0) {
                log.info("Seeding initial inventory products...");
                productRepository.saveAll(List.of(
                        Product.builder().productName("Dell XPS 15 9530").quantity(15).price(new BigDecimal("1899.99")).build(),
                        Product.builder().productName("Apple iPhone 16 Pro Max").quantity(30).price(new BigDecimal("1299.00")).build(),
                        Product.builder().productName("Logitech MX Master 3S").quantity(100).price(new BigDecimal("99.50")).build()
                ));
                log.info("Inventory products seeded successfully.");
            }
        };
    }
}

