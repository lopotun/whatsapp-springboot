package net.kem.whatsapp.chatviewer.whatsappspringboot.repository;

import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatEntryRepository extends JpaRepository<ChatEntryEntity, Long> {
    
    // Basic search methods
    List<ChatEntryEntity> findByAuthor(String author);
    List<ChatEntryEntity> findByType(ChatEntry.Type type);
    List<ChatEntryEntity> findByLocalDateTimeBetween(LocalDateTime start, LocalDateTime end);
    
    // Combined search methods
    List<ChatEntryEntity> findByAuthorAndType(String author, ChatEntry.Type type);
    List<ChatEntryEntity> findByAuthorAndLocalDateTimeBetween(String author, LocalDateTime start, LocalDateTime end);
    List<ChatEntryEntity> findByTypeAndLocalDateTimeBetween(ChatEntry.Type type, LocalDateTime start, LocalDateTime end);
    
    // Full-text search in payload
    @Query("SELECT ce FROM ChatEntryEntity ce WHERE " +
           "LOWER(ce.payload) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(ce.author) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<ChatEntryEntity> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
    
    // Search by attachment hash
    List<ChatEntryEntity> findByAttachmentHash(String attachmentHash);
    
    // Count methods for statistics
    long countByAuthor(String author);
    long countByType(ChatEntry.Type type);
    long countByLocalDateTimeBetween(LocalDateTime start, LocalDateTime end);
} 