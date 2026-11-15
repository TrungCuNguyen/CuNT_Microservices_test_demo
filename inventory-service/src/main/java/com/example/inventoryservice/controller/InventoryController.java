package com.example.inventoryservice.controller;

import com.example.inventoryservice.dto.ProductRequest;
import com.example.inventoryservice.dto.ProductResponse;
import com.example.inventoryservice.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Requirement Task 2:
     * GET /api/inventory/{productId} returns product information
     */
    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable("productId") Long productId) {
        ProductResponse product = inventoryService.getProductById(productId);
        return ResponseEntity.ok(product);
    }

    /**
     * Helper endpoint: List all inventory items
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(inventoryService.getAllProducts());
    }

    /**
     * Helper endpoint: Create product in inventory
     */
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.createProduct(request));
    }

    /**
     * Endpoint to deduct stock during order processing
     */
    @PutMapping("/{productId}/deduct")
    public ResponseEntity<ProductResponse> deductStock(
            @PathVariable("productId") Long productId,
            @RequestParam("quantity") Integer quantity) {
        return ResponseEntity.ok(inventoryService.deductStock(productId, quantity));
    }
}

