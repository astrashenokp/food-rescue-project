package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.LotResponse;
import com.example.foodrescue.common.LotStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lots")
public class LotController {

    private final LotService lotService;

    public LotController(LotService lotService) {
        this.lotService = lotService;
    }

    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody LotRequest request) {
        LotResponse lot = lotService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/lots/" + lot.id())).build();
    }

    @GetMapping
    public List<LotResponse> findAll(
            @RequestParam(required = false) LotStatus status,
            @RequestParam(required = false) FoodCategory category,
            @RequestParam(required = false) UUID donorOrgId) {
        return lotService.findAll(status, category, donorOrgId);
    }

    @GetMapping("/{id}")
    public LotResponse getById(@PathVariable UUID id) {
        return lotService.getById(id);
    }

    @PutMapping("/{id}")
    public LotResponse update(@PathVariable UUID id, @Valid @RequestBody LotRequest request) {
        return lotService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        lotService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publication")
    public LotResponse publish(@PathVariable UUID id) {
        return lotService.publish(id);
    }

    @PostMapping("/{id}/cancellation")
    public LotResponse cancel(@PathVariable UUID id) {
        return lotService.cancel(id);
    }

    @PostMapping("/{id}/approval")
    public LotResponse approve(@PathVariable UUID id, @Valid @RequestBody ApprovalRequest request) {
        return lotService.approve(id, request);
    }
}
