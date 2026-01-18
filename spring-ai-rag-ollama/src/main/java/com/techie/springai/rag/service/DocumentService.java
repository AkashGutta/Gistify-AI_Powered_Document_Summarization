package com.techie.springai.rag.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.techie.springai.rag.Repository.DocumentRepository;
import com.techie.springai.rag.Repository.SummaryRepository;
import com.techie.springai.rag.Repository.UserRepository;
import com.techie.springai.rag.entity.Document;
import com.techie.springai.rag.entity.Summary;
import com.techie.springai.rag.entity.User;

@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private final DocumentRepository documentRepository;
    private final SummaryRepository summaryRepository;
    private final UserRepository userRepository;

    public DocumentService(DocumentRepository documentRepository, 
                          SummaryRepository summaryRepository,
                          UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.summaryRepository = summaryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Document saveDocument(MultipartFile file, String summaryText, Long userId) throws IOException {
        logger.info("===========================================");
        logger.info("💾 SAVING DOCUMENT");
        logger.info("   File: {}", file.getOriginalFilename());
        logger.info("   User ID: {}", userId);
        logger.info("   Summary length: {} chars", summaryText.length());
        logger.info("===========================================");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        logger.info("✅ User found: {} ({})", user.getName(), user.getEmail());

        // Save original file to disk
        String filePath = saveFileToDisk(file, userId);
        logger.info("📁 Original file saved: {}", filePath);
        
        // Save summary as .txt file
        String summaryFilePath = saveSummaryToFile(file.getOriginalFilename(), summaryText, userId);
        logger.info("📄 Summary file saved: {}", summaryFilePath);

        // Create document entity
        Document document = new Document();
        document.setUser(user);
        document.setFilename(file.getOriginalFilename());
        document.setFilePath(filePath);
        document.setFileType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setUploadDate(LocalDateTime.now());

        // Save document to database
        Document savedDocument = documentRepository.save(document);
        logger.info("✅ Document saved to DB - ID: {}", savedDocument.getId());

        // Create and save summary
        Summary summary = new Summary();
        summary.setDocument(savedDocument);
        summary.setSummaryText(summaryText);
        summary.setCreatedAt(LocalDateTime.now());
        Summary savedSummary = summaryRepository.save(summary);
        
        logger.info("✅ Summary saved to DB - ID: {}", savedSummary.getId());
        logger.info("===========================================");

        return savedDocument;
    }

    private String saveFileToDisk(MultipartFile file, Long userId) throws IOException {
        // Create user-specific directory
        Path uploadPath = Paths.get(uploadDir, String.valueOf(userId));
        
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            logger.info("📁 Created directory: {}", uploadPath.toAbsolutePath());
        }

        // Generate unique filename with timestamp
        String originalFilename = file.getOriginalFilename();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String filename = timestamp + "_" + originalFilename;
        
        Path filePath = uploadPath.resolve(filename);

        // Copy file to disk
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        logger.info("💾 File saved: {}", filePath.toAbsolutePath());

        return filePath.toString();
    }

    private String saveSummaryToFile(String originalFilename, String summaryText, Long userId) throws IOException {
        // Create user-specific directory
        Path uploadPath = Paths.get(uploadDir, String.valueOf(userId));
        
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate summary filename: original_name_summary.txt
        String baseFilename;
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            baseFilename = originalFilename.substring(0, dotIndex);
        } else {
            baseFilename = originalFilename;
        }
        
        String summaryFilename = baseFilename + "_summary.txt";
        Path summaryPath = uploadPath.resolve(summaryFilename);

        // Write summary to file
        Files.write(summaryPath, summaryText.getBytes(StandardCharsets.UTF_8));
        
        logger.info("📄 Summary file created: {}", summaryPath.toAbsolutePath());

        return summaryPath.toString();
    }

    public List<Document> getUserDocuments(Long userId) {
        logger.info("📚 Fetching documents for user ID: {}", userId);
        List<Document> documents = documentRepository.findByUserIdOrderByUploadDateDesc(userId);
        logger.info("📚 Found {} documents", documents.size());
        
        for (Document doc : documents) {
            logger.info("   - {} (Summary: {})", 
                doc.getFilename(), 
                doc.getSummary() != null ? "YES" : "NO");
        }
        
        return documents;
    }
}