package com.example.inventoryservice.service;

import com.example.inventoryservice.dto.ProductRequest;
import com.example.inventoryservice.dto.ProductResponse;

import java.util.List;

public interface InventoryService {
    ProductResponse getProductById(Long productId);
    List<ProductResponse> getAllProducts();
    ProductResponse createProduct(ProductRequest request);
    ProductResponse deductStock(Long productId, Integer quantity);
}

