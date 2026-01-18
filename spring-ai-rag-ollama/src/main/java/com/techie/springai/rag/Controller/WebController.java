package com.techie.springai.rag.Controller;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.techie.springai.rag.Repository.UserRepository;
import com.techie.springai.rag.entity.Document;
import com.techie.springai.rag.entity.User;
import com.techie.springai.rag.service.DocumentService;

@Controller
public class WebController {

    private static final Logger logger = LoggerFactory.getLogger(WebController.class);
    
    private final DocumentService documentService;
    private final UserRepository userRepository;

    public WebController(DocumentService documentService, UserRepository userRepository) {
        this.documentService = documentService;
        this.userRepository = userRepository;
        logger.info("✅ WebController initialized");
    }

    @GetMapping("/")
    public String index() {
        logger.info("📄 Accessing index page");
        return "index";
    }

    @GetMapping("/home")
    public String home(@AuthenticationPrincipal OAuth2User principal, Model model) {
        logger.info("===========================================");
        logger.info("🏠 HOME PAGE REQUEST");
        
        try {
            if (principal == null) {
                logger.error("❌ Principal is NULL - User not authenticated");
                return "redirect:/";
            }

            String email = principal.getAttribute("email");
            String name = principal.getAttribute("name");
            String picture = principal.getAttribute("picture");
            
            logger.info("✅ Authenticated user:");
            logger.info("   Email: {}", email);
            logger.info("   Name: {}", name);
            
            if (email == null) {
                logger.error("❌ Email is NULL!");
                model.addAttribute("userName", name != null ? name : "Unknown");
                model.addAttribute("userEmail", "");
                model.addAttribute("userPicture", picture);
                model.addAttribute("documents", new ArrayList<>());
                return "home";
            }
            
            User user = userRepository.findByEmail(email).orElse(null);
            
            if (user == null) {
                logger.warn("⚠️ User NOT found in database: {}", email);
                logger.warn("⚠️ This should not happen - OAuth service should have created user!");
                
                model.addAttribute("userName", name);
                model.addAttribute("userEmail", email);
                model.addAttribute("userPicture", picture);
                model.addAttribute("documents", new ArrayList<>());
            } else {
                logger.info("✅ User found in DB - ID: {}, Email: {}", user.getId(), user.getEmail());
                
                model.addAttribute("userName", user.getName());
                model.addAttribute("userEmail", user.getEmail());
                model.addAttribute("userPicture", user.getProfilePictureUrl());
                
                List<Document> documents = documentService.getUserDocuments(user.getId());
                model.addAttribute("documents", documents);
                
                logger.info("📚 Loaded {} documents", documents.size());
            }
            
            logger.info("✅ Rendering home view");
            logger.info("===========================================");
            return "home";
            
        } catch (Exception e) {
            logger.error("❌ EXCEPTION in home controller:", e);
            model.addAttribute("userName", "Error");
            model.addAttribute("userEmail", "");
            model.addAttribute("userPicture", "");
            model.addAttribute("documents", new ArrayList<>());
            return "home";
        }
    }
}