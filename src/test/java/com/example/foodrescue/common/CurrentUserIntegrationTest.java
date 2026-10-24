package com.example.foodrescue.common;

import com.example.foodrescue.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class CurrentUserIntegrationTest {

    @Autowired
    CurrentUser currentUser;

    @Test
    @WithUserDetails("volunteer1")
    void profileId_DemoVolunteer_ReturnsDemoVolunteerId() {
        assertThat(currentUser.profileId()).isEqualTo(DemoData.DEMO_VOLUNTEER);
    }

    @Test
    @WithUserDetails("donor1")
    void profileId_DemoDonor_ReturnsDemoDonorOrg() {
        assertThat(currentUser.profileId()).isEqualTo(DemoData.DONOR);
    }

    @Test
    @WithUserDetails("admin1")
    void profileId_AdminWithoutProfile_ReturnsNull() {
        assertThat(currentUser.profileId()).isNull();
    }
}
