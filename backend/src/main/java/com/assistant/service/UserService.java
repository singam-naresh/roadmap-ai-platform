package com.assistant.service;

import com.assistant.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    /**
     * Gets the currently authenticated user from the security context.
     * Returns null if no user is authenticated.
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || 
            authentication.getPrincipal().equals("anonymousUser")) {
            return null;
        }
        
        return (User) authentication.getPrincipal();
    }

    /**
     * Gets the ID of the currently authenticated user.
     * Returns null if no user is authenticated.
     */
    public Long getCurrentUserId() {
        User user = getCurrentUser();
        return user != null ? user.getId() : null;
    }
}