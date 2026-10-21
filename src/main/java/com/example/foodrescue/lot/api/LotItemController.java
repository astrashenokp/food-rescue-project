package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lots/{lotId}/items")
public class LotItemController {

    private final LotItemService lotItemService;

    public LotItemController(LotItemService lotItemService) {
        this.lotItemService = lotItemService;
    }

    @PostMapping
    public ResponseEntity<Void> addItem(@PathVariable UUID lotId, @Valid @RequestBody FoodItemRequest request) {
        FoodItemResponse item = lotItemService.addItem(lotId, request);
        return ResponseEntity.created(URI.create("/api/v1/lots/" + lotId + "/items/" + item.id())).build();
    }

    @GetMapping
    public List<FoodItemResponse> findItems(@PathVariable UUID lotId) {
        return lotItemService.findItems(lotId);
    }

    @GetMapping("/{itemId}")
    public FoodItemResponse getItem(@PathVariable UUID lotId, @PathVariable UUID itemId) {
        return lotItemService.getItem(lotId, itemId);
    }

    @PutMapping("/{itemId}")
    public FoodItemResponse updateItem(@PathVariable UUID lotId,
                                       @PathVariable UUID itemId,
                                       @Valid @RequestBody FoodItemRequest request) {
        return lotItemService.updateItem(lotId, itemId, request);
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable UUID lotId, @PathVariable UUID itemId) {
        lotItemService.deleteItem(lotId, itemId);
        return ResponseEntity.noContent().build();
    }
}
