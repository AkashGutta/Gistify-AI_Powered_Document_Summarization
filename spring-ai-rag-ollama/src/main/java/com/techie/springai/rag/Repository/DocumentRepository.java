package com.techie.springai.rag.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.techie.springai.rag.entity.Document;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUserIdOrderByUploadDateDesc(Long userId);
    List<Document> findByUserId(Long userId);
    long countByUserId(Long userId);
}