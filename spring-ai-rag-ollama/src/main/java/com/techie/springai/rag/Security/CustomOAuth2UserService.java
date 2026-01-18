package com.techie.springai.rag.Security;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.techie.springai.rag.Repository.UserRepository;
import com.techie.springai.rag.entity.User;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger logger = LoggerFactory.getLogger(CustomOAuth2UserService.class);
    
    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        logger.info("🔧 CustomOAuth2UserService INITIALIZED");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        logger.info("===========================================" );
        logger.info("🔐 CUSTOM OAUTH2 USER SERVICE CALLED!");
        logger.info("===========================================");
        
        OAuth2User oauth2User = super.loadUser(userRequest);

        try {
            String googleId = oauth2User.getAttribute("sub");
            String email = oauth2User.getAttribute("email");
            String name = oauth2User.getAttribute("name");
            String picture = oauth2User.getAttribute("picture");

            logger.info("📧 Email: {}", email);
            logger.info("👤 Name: {}", name);
            logger.info("🆔 Google ID: {}", googleId);

            if (email == null || email.isEmpty()) {
                throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info"), 
                    "Email not found in OAuth2 response"
                );
            }

            // Check if user exists
            User existingUser = userRepository.findByEmail(email).orElse(null);

            if (existingUser == null) {
                logger.info("✨ User does NOT exist. Creating NEW user...");
                
                User newUser = new User();
                newUser.setGoogleId(googleId);
                newUser.setEmail(email);
                newUser.setName(name);
                newUser.setProfilePictureUrl(picture);
                newUser.setCreatedAt(LocalDateTime.now());
                
                User savedUser = userRepository.saveAndFlush(newUser);
                logger.info("✅✅✅ USER CREATED - ID: {}, Email: {}", savedUser.getId(), savedUser.getEmail());
                
                // Verify
                User verify = userRepository.findByEmail(email).orElse(null);
                if (verify != null) {
                    logger.info("✅ VERIFIED: User exists with ID: {}", verify.getId());
                } else {
                    logger.error("❌ CRITICAL ERROR: User was not saved!");
                }
                
                return oauth2User;
            }

            logger.info("✅ Existing user - ID: {}", existingUser.getId());

            // Update user info if changed
            boolean updated = false;
            
            if (googleId != null && !googleId.equals(existingUser.getGoogleId())) {
                existingUser.setGoogleId(googleId);
                updated = true;
            }
            
            if (name != null && !name.equals(existingUser.getName())) {
                existingUser.setName(name);
                updated = true;
            }
            
            if (picture != null && !picture.equals(existingUser.getProfilePictureUrl())) {
                existingUser.setProfilePictureUrl(picture);
                updated = true;
            }

            if (updated) {
                userRepository.saveAndFlush(existingUser);
                logger.info("🔄 User updated");
            }

            return oauth2User;
            
        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("❌ ERROR:", e);
            throw new OAuth2AuthenticationException(
                new OAuth2Error("user_processing_error"), 
                "Failed to process user: " + e.getMessage(), 
                e
            );
        }
    }
}