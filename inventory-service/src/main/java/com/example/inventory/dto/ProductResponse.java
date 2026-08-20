package com.example.inventory.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private UUID id;
    private String name;
    private Integer availableStock;
    private Integer reservedStock;

    /**
     * Optimistic locking version değeri.
     * Her başarılı güncelleme sonrası artırılır.
     * İstemci bu değeri izleyerek eşzamanlı güncellemeleri takip edebilir.
     */
    private Long version;
}
