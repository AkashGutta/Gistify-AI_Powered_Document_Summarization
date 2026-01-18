package com.techie.springai.rag;

import java.io.InputStream;
import java.time.LocalDateTime;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.techie.springai.rag.Repository.UserRepository;
import com.techie.springai.rag.entity.Document;
import com.techie.springai.rag.entity.User;
import com.techie.springai.rag.service.DocumentService;

@RestController
@RequestMapping("/api")
public class SummaryController {

    private static final Logger logger = LoggerFactory.getLogger(SummaryController.class);
    
    private static final int MAX_CONTENT_LENGTH = 10_000_000;
    private static final int AI_CONTEXT_LIMIT = 5000;
    private static final int MIN_TEXT_LENGTH = 100;
    
    private final ChatClient chatClient;
    private final DocumentService documentService;
    private final UserRepository userRepository;

    public SummaryController(ChatClient chatClient, DocumentService documentService, UserRepository userRepository) {
        this.chatClient = chatClient;
        this.documentService = documentService;
        this.userRepository = userRepository;
    }

    @PostMapping("/summary")
    public ResponseEntity<String> summarize(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal OAuth2User principal) {

        try {
            if (principal == null) {
                logger.error("❌ User not authenticated");
                return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Error: User not authenticated. Please log in.");
            }

            String email = principal.getAttribute("email");
            if (email == null) {
                logger.error("❌ Email not found");
                return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Error: Unable to identify user.");
            }

            // GET OR CREATE USER
            User user = getOrCreateUser(principal);
            logger.info("📤 User {} uploading file: {}", email, file.getOriginalFilename());

            if (file == null || file.isEmpty()) {
                return ResponseEntity
                    .badRequest()
                    .body("Error: No file uploaded or file is empty.");
            }

            String filename = file.getOriginalFilename() != null 
                ? file.getOriginalFilename() 
                : "unknown";
            
            String contentType = file.getContentType();
            if (!isValidFileType(filename, contentType)) {
                return ResponseEntity
                    .badRequest()
                    .body("Error: Invalid file type. Only PDF, DOCX, and TXT files are supported.");
            }

            if (file.getSize() > 50 * 1024 * 1024) {
                return ResponseEntity
                    .badRequest()
                    .body("Error: File size exceeds 50MB limit.");
            }

            logger.info("Processing file: {} ({})", filename, contentType);

            String extractedText = extractTextWithTika(file);

            if (extractedText == null || extractedText.trim().length() < MIN_TEXT_LENGTH) {
                return ResponseEntity
                    .ok()
                    .body("This document contains insufficient extractable text.");
            }

            logger.info("Extracted {} characters from {}", extractedText.length(), filename);

            String summary = generateSummaryWithAI(extractedText);

            Document savedDocument = documentService.saveDocument(file, summary, user.getId());
            
            logger.info("✅ Saved document ID: {} for user: {}", savedDocument.getId(), user.getEmail());

            String response = String.format("""
                   ✅ Document Saved Successfully!
                   
                   File: %s
                   Size: %.2f KB
                   Document ID: %d
                   User: %s
                   
                   AI-Generated Summary:
                   %s
                   """, 
                   filename, 
                   file.getSize() / 1024.0,
                   savedDocument.getId(),
                   user.getName(),
                   summary);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing document: {}", e.getMessage(), e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error while processing document: " + e.getMessage());
        }
    }

    // NEW METHOD: Get or create user
    private User getOrCreateUser(OAuth2User principal) {
        String email = principal.getAttribute("email");
        String googleId = principal.getAttribute("sub");
        String name = principal.getAttribute("name");
        String picture = principal.getAttribute("picture");

        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    logger.info("✨ Creating user on-the-fly: {}", email);
                    User newUser = new User();
                    newUser.setGoogleId(googleId);
                    newUser.setEmail(email);
                    newUser.setName(name);
                    newUser.setProfilePictureUrl(picture);
                    newUser.setCreatedAt(LocalDateTime.now());
                    User saved = userRepository.save(newUser);
                    logger.info("✅ User created: ID {}", saved.getId());
                    return saved;
                });
    }

    private boolean isValidFileType(String filename, String contentType) {
        if (filename == null) return false;
        
        String lowerFilename = filename.toLowerCase();
        return lowerFilename.endsWith(".pdf") ||
               lowerFilename.endsWith(".docx") ||
               lowerFilename.endsWith(".doc") ||
               lowerFilename.endsWith(".txt");
    }

    private String extractTextWithTika(MultipartFile file) {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(MAX_CONTENT_LENGTH);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            try (InputStream is = file.getInputStream()) {
                parser.parse(is, handler, metadata, context);
            }

            String text = handler.toString().trim();

            if (text.isEmpty()) {
                logger.warn("No text extracted from file: {}", file.getOriginalFilename());
                return null;
            }

            return text;

        } catch (org.apache.tika.exception.TikaException e) {
            logger.error("Tika parsing error: {}", e.getMessage());
            throw new RuntimeException("Document parsing failed.", e);
        } catch (Exception e) {
            logger.error("Unexpected error during text extraction: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract text from document.", e);
        }
    }

    private String generateSummaryWithAI(String content) {
        try {
            String truncatedContent = content.length() > AI_CONTEXT_LIMIT
                    ? content.substring(0, AI_CONTEXT_LIMIT) + "..."
                    : content;

            String prompt = String.format("""
                Task: Summarize the following document professionally.

                Instructions:
                - Write a clear, concise summary in 5-7 sentences.
                - Focus on the main ideas, key points, and purpose of the document.
                - Use professional, neutral language.

                Document Content:
                %s
                """, truncatedContent);

            String summary = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return summary != null ? summary.trim() : "Unable to generate summary.";

        } catch (Exception e) {
            logger.error("AI summarization failed: {}", e.getMessage(), e);
            return "Error: AI summarization failed.";
        }
    }
}