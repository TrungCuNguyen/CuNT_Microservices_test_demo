package com.example.inventoryservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryResponseEvent implements Serializable {
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private String status; // COMPLETED or CANCELLED
    private BigDecimal totalPrice;
    private String message;
}
