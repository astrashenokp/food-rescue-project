package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.DemoData;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class VolunteerDemoData implements CommandLineRunner {

    private final VolunteerStore volunteerStore;

    public VolunteerDemoData(VolunteerStore volunteerStore) {
        this.volunteerStore = volunteerStore;
    }

    @Override
    public void run(String... args) {
        VolunteerProfile demo = new VolunteerProfile();
        demo.setId(DemoData.DEMO_VOLUNTEER);
        demo.setFullName("Демо Волонтер");
        demo.setEmail("demo@volunteer.ua");
        demo.setPhone("+380501234567");
        demo.setTransportType(TransportType.CAR);
        demo.setActivityZone("Київ, центр");
        volunteerStore.save(demo);
    }
}
