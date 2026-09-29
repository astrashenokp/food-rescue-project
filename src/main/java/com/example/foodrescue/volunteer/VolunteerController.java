package com.example.foodrescue.volunteer;

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
@RequestMapping("/api/v1")
public class VolunteerController {

    private final VolunteerService volunteerService;

    public VolunteerController(VolunteerService volunteerService) {
        this.volunteerService = volunteerService;
    }

    @PostMapping("/volunteers")
    public ResponseEntity<Void> create(@Valid @RequestBody VolunteerRequest request) {
        VolunteerResponse response = volunteerService.create(request);
        URI location = URI.create("/api/v1/volunteers/" + response.id());
        return ResponseEntity.created(location).build();
    }

    @GetMapping("/volunteers")
    public List<VolunteerResponse> getAll() {
        return volunteerService.getAll();
    }

    @GetMapping("/volunteers/{id}")
    public VolunteerResponse getById(@PathVariable UUID id) {
        return volunteerService.getById(id);
    }

    @PutMapping("/volunteers/{id}")
    public VolunteerResponse update(@PathVariable UUID id, @Valid @RequestBody VolunteerRequest request) {
        return volunteerService.update(id, request);
    }

    @DeleteMapping("/volunteers/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        volunteerService.delete(id);
        return ResponseEntity.noContent().build();
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

    @GetMapping("/volunteers/{id}/preferred-points")
    public List<PreferredPointResponse> getPreferredPoints(@PathVariable UUID id) {
        return volunteerService.getPreferredPoints(id);
    }

    @PutMapping("/volunteers/{id}/preferred-points/{pointId}")
    public PreferredPointResponse addPreferredPoint(@PathVariable UUID id,
                                                    @PathVariable UUID pointId) {
        return volunteerService.addPreferredPoint(id, pointId);
    }

    @DeleteMapping("/volunteers/{id}/preferred-points/{pointId}")
    public ResponseEntity<Void> removePreferredPoint(@PathVariable UUID id,
                                                     @PathVariable UUID pointId) {
        volunteerService.removePreferredPoint(id, pointId);
        return ResponseEntity.noContent().build();
    }
}
