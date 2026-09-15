package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.lot.LotResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class VolunteerController {

    private final VolunteerService volunteerService;

    public VolunteerController(VolunteerService volunteerService) {
        this.volunteerService = volunteerService;
    }

    @PostMapping("/volunteers")
    public ResponseEntity<Void> create(@Valid @RequestBody VolunteerRequest request) {
        VolunteerProfile profile = volunteerService.create(request);
        URI location = URI.create("/api/v1/volunteers/" + profile.getId());
        return ResponseEntity.created(location).build();
    }

    @GetMapping("/volunteers/{id}")
    public VolunteerResponse getById(@PathVariable UUID id) {
        return VolunteerResponse.from(volunteerService.getById(id));
    }

    @PostMapping("/lots/{id}/reservation")
    public ResponseEntity<Void> reserve(@PathVariable UUID id,
                                        @Valid @RequestBody ReservationRequest request) {
        volunteerService.reserve(id, request);
        URI location = URI.create("/api/v1/lots/" + id + "/reservation");
        return ResponseEntity.created(location).build();
    }

    @DeleteMapping("/lots/{id}/reservation")
    public ResponseEntity<Void> cancelReservation(@PathVariable UUID id) {
        volunteerService.cancelReservation(id);
        return ResponseEntity.noContent().build();
    }
}
