package com.example.inventoryservice.service.impl;

import com.example.inventoryservice.dto.ProductRequest;
import com.example.inventoryservice.dto.ProductResponse;
import com.example.inventoryservice.entity.Product;
import com.example.inventoryservice.repository.ProductRepository;
import com.example.inventoryservice.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl implements InventoryService {

    private final ProductRepository productRepository;

    public InventoryServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found with id: " + productId));
        return mapToResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = Product.builder()
                .productName(request.getProductName())
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .build();
        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse deductStock(Long productId, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found with id: " + productId));

        if (product.getQuantity() < quantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient stock for product id: " + productId);
        }

        product.setQuantity(product.getQuantity() - quantity);
        Product updated = productRepository.save(product);
        return mapToResponse(updated);
    }

    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .productName(product.getProductName())
                .quantity(product.getQuantity())
                .price(product.getPrice())
                .build();
    }
}

