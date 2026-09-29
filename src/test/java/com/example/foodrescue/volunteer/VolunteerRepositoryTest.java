package com.example.foodrescue.volunteer;

import com.example.foodrescue.delivery.DestinationPoint;
import com.example.foodrescue.delivery.DestinationPointRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class VolunteerRepositoryTest {

    @Autowired
    private VolunteerRepository volunteerRepository;

    @Autowired
    private DestinationPointRepository destinationPointRepository;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private UUID volunteerId;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @BeforeEach
    void setUp() {
        volunteerId = UUID.randomUUID();
        VolunteerProfile profile = new VolunteerProfile(volunteerId);
        profile.setFullName("Іван Петренко");
        profile.setEmail("ivan@example.com");
        profile.setPhone("+380501234567");
        profile.setTransportType(TransportType.CAR);
        profile.setActivityZone("Київ");
        em.persist(profile);
        em.flush();
        em.clear();
    }

    @Test
    void existsByEmail_existingEmail_returnsTrue() {
        assertTrue(volunteerRepository.existsByEmail("ivan@example.com"));
    }

    @Test
    void existsByEmail_unknownEmail_returnsFalse() {
        assertFalse(volunteerRepository.existsByEmail("unknown@example.com"));
    }

    @Test
    void existsByEmailAndIdNot_sameVolunteer_returnsFalse() {
        assertFalse(volunteerRepository.existsByEmailAndIdNot("ivan@example.com", volunteerId));
    }

    @Test
    void existsByEmailAndIdNot_differentVolunteer_returnsTrue() {
        UUID otherId = UUID.randomUUID();
        VolunteerProfile other = new VolunteerProfile(otherId);
        other.setFullName("Другий");
        other.setEmail("other@example.com");
        other.setPhone("+380509999999");
        other.setTransportType(TransportType.BIKE);
        other.setActivityZone("Львів");
        em.persist(other);
        em.flush();

        assertTrue(volunteerRepository.existsByEmailAndIdNot("ivan@example.com", otherId));
    }

    @Test
    void findAllWithPoints_returnsAllVolunteers() {
        List<VolunteerProfile> result = volunteerRepository.findAllWithPoints();
        assertEquals(1, result.size());
        assertEquals("Іван Петренко", result.get(0).getFullName());
    }

    @Test
    void findByIdWithPoints_existingId_returnsVolunteer() {
        Optional<VolunteerProfile> result = volunteerRepository.findByIdWithPoints(volunteerId);
        assertTrue(result.isPresent());
        assertEquals("ivan@example.com", result.get().getEmail());
    }

    @Test
    void findByIdWithPoints_unknownId_returnsEmpty() {
        Optional<VolunteerProfile> result = volunteerRepository.findByIdWithPoints(UUID.randomUUID());
        assertFalse(result.isPresent());
    }

    @Test
    void findByIdWithPoints_withPreferredPoints_loadsPointsInSingleQuery() {
        // Додаємо точку призначення
        DestinationPoint point = new DestinationPoint(
                UUID.randomUUID(), UUID.randomUUID(), "Центр допомоги",
                "вул. Хрещатик, 1", "09:00-18:00", Set.of());
        em.persist(point);

        // Прив'язуємо до волонтера
        VolunteerProfile profile = em.find(VolunteerProfile.class, volunteerId);
        profile.getPreferredPoints().add(point);
        em.flush();
        em.clear();

        // Перевіряємо N+1: Hibernate statistics
        Statistics stats = statistics();
        stats.clear();

        Optional<VolunteerProfile> result = volunteerRepository.findByIdWithPoints(volunteerId);

        long queryCount = stats.getPrepareStatementCount();

        assertTrue(result.isPresent());
        assertEquals(1, result.get().getPreferredPoints().size());
        assertEquals("Центр допомоги", result.get().getPreferredPoints().iterator().next().getName());
        assertEquals(1, queryCount, "JOIN FETCH повинен виконати лише 1 запит");
    }

    @Test
    void incrementCompleted_incrementsCounter() {
        volunteerRepository.incrementCompleted(volunteerId);
        em.flush();
        em.clear();

        VolunteerProfile updated = em.find(VolunteerProfile.class, volunteerId);
        assertEquals(1, updated.getCompletedDeliveries());
    }

    @Test
    void incrementNoShows_incrementsCounter() {
        volunteerRepository.incrementNoShows(volunteerId);
        em.flush();
        em.clear();

        VolunteerProfile updated = em.find(VolunteerProfile.class, volunteerId);
        assertEquals(1, updated.getNoShows());
    }

    @Test
    void incrementLatePickups_incrementsCounter() {
        volunteerRepository.incrementLatePickups(volunteerId);
        em.flush();
        em.clear();

        VolunteerProfile updated = em.find(VolunteerProfile.class, volunteerId);
        assertEquals(1, updated.getLatePickups());
    }
}
