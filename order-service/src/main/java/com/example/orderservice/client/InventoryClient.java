package com.example.orderservice.client;

import com.example.orderservice.dto.ProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @GetMapping("/api/inventory/{productId}")
    ProductDto getProductById(@PathVariable("productId") Long productId);

    @PutMapping("/api/inventory/{productId}/deduct")
    ProductDto deductStock(@PathVariable("productId") Long productId, @RequestParam("quantity") Integer quantity);
}

