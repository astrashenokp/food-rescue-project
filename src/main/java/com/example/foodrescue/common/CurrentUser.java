package com.example.foodrescue.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUser {

    public UUID profileId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ProfileOwner owner) {
            return owner.getProfileId();
        }
        return null;
    }
}
