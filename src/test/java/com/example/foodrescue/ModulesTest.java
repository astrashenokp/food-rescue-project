package com.example.foodrescue;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulesTest {

    @Test
    void verifiesModuleStructure() {
        ApplicationModules.of(FoodRescueApplication.class).verify();
    }
}
