package net.kem.whatsapp.chatviewer.whatsappspringboot.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;

@Repository
public interface ChatEntryRepository extends JpaRepository<ChatEntryEntity, Long> {

        // Basic search methods
        List<ChatEntryEntity> findByAuthor(String author);

        List<ChatEntryEntity> findByType(ChatEntry.Type type);

        List<ChatEntryEntity> findByLocalDateTimeBetween(LocalDateTime start, LocalDateTime end);

        // Combined search methods
        List<ChatEntryEntity> findByAuthorAndType(String author, ChatEntry.Type type);

        List<ChatEntryEntity> findByAuthorAndLocalDateTimeBetween(String author,
                        LocalDateTime start, LocalDateTime end);

        List<ChatEntryEntity> findByTypeAndLocalDateTimeBetween(ChatEntry.Type type,
                        LocalDateTime start, LocalDateTime end);

        // Full-text search in payload
        @Query("""
                        SELECT ce FROM ChatEntryEntity ce WHERE \
                        LOWER(ce.payload) LIKE LOWER(CONCAT('%', :keyword, '%')) OR \
                        LOWER(ce.author) LIKE LOWER(CONCAT('%', :keyword, '%'))""")
        Page<ChatEntryEntity> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

        // Search by attachment hash
        // REMOVED: List<ChatEntryEntity> findByAttachmentHash(String attachmentHash);

        // User-specific methods
        List<ChatEntryEntity> findByUserId(Long userId);

        Page<ChatEntryEntity> findByUserId(Long userId, Pageable pageable);

        // Chat-specific methods
        List<ChatEntryEntity> findByUserIdAndChatId(Long userId, String chatId);

        Page<ChatEntryEntity> findByUserIdAndChatId(Long userId, String chatId, Pageable pageable);

        // Multiple chat methods
        Page<ChatEntryEntity> findByUserIdAndChatIdIn(Long userId, List<String> chatIds,
                        Pageable pageable);

        // Check if chat exists for user
        boolean existsByUserIdAndChatId(Long userId, String chatId);

        // User and chat filtered search methods
        List<ChatEntryEntity> findByUserIdAndAuthor(Long userId, String author);

        Page<ChatEntryEntity> findByUserIdAndAuthor(Long userId, String author, Pageable pageable);

        List<ChatEntryEntity> findByUserIdAndType(Long userId, ChatEntry.Type type);

        Page<ChatEntryEntity> findByUserIdAndType(Long userId, ChatEntry.Type type,
                        Pageable pageable);

        List<ChatEntryEntity> findByUserIdAndLocalDateTimeBetween(Long userId, LocalDateTime start,
                        LocalDateTime end);

        Page<ChatEntryEntity> findByUserIdAndLocalDateTimeBetween(Long userId, LocalDateTime start,
                        LocalDateTime end, Pageable pageable);

        // Combined search methods with user filter
        List<ChatEntryEntity> findByUserIdAndAuthorAndType(Long userId, String author,
                        ChatEntry.Type type);

        List<ChatEntryEntity> findByUserIdAndAuthorAndLocalDateTimeBetween(Long userId,
                        String author, LocalDateTime start, LocalDateTime end);

        List<ChatEntryEntity> findByUserIdAndTypeAndLocalDateTimeBetween(Long userId,
                        ChatEntry.Type type, LocalDateTime start, LocalDateTime end);

        // User-specific full-text search
        @Query("""
                        SELECT ce FROM ChatEntryEntity ce WHERE ce.userId = :userId AND \
                        (LOWER(ce.payload) LIKE LOWER(CONCAT('%', :keyword, '%')) OR \
                        LOWER(ce.author) LIKE LOWER(CONCAT('%', :keyword, '%')))""")
        Page<ChatEntryEntity> searchByUserIdAndKeyword(@Param("userId") Long userId,
                        @Param("keyword") String keyword, Pageable pageable);

        // User and chat filtered full-text search
        @Query("""
                        SELECT ce FROM ChatEntryEntity ce WHERE ce.userId = :userId AND ce.chatId = :chatId AND \
                        (LOWER(ce.payload) LIKE LOWER(CONCAT('%', :keyword, '%')) OR \
                        LOWER(ce.author) LIKE LOWER(CONCAT('%', :keyword, '%')))""")
        Page<ChatEntryEntity> searchByUserIdAndChatIdAndKeyword(@Param("userId") Long userId,
                        @Param("chatId") String chatId, @Param("keyword") String keyword,
                        Pageable pageable);

        // Multiple chats keyword search
        @Query("""
                        SELECT ce FROM ChatEntryEntity ce WHERE ce.userId = :userId AND ce.chatId IN :chatIds AND \
                        (LOWER(ce.payload) LIKE LOWER(CONCAT('%', :keyword, '%')) OR \
                        LOWER(ce.author) LIKE LOWER(CONCAT('%', :keyword, '%')))""")
        Page<ChatEntryEntity> searchByUserIdAndChatIdInAndKeyword(@Param("userId") Long userId,
                        @Param("chatIds") List<String> chatIds, @Param("keyword") String keyword,
                        Pageable pageable);

        // Search by attachment hash with user filter
        // REMOVED: List<ChatEntryEntity> findByUserIdAndAttachmentHash(Long userId, String
        // attachmentHash);

        // Count methods for statistics (user-specific)
        long countByUserId(Long userId);

        long countByUserIdAndAuthor(Long userId, String author);

        long countByUserIdAndType(Long userId, ChatEntry.Type type);

        long countByUserIdAndLocalDateTimeBetween(Long userId, LocalDateTime start,
                        LocalDateTime end);

        long countByUserIdAndChatId(Long userId, String chatId);

        // Delete methods
        void deleteByUserIdAndChatId(Long userId, String chatId);

        void deleteByUserId(Long userId);

        // Get unique chat IDs for a user
        @Query("SELECT DISTINCT ce.chatId FROM ChatEntryEntity ce WHERE ce.userId = :userId")
        List<String> findDistinctChatIdsByUserId(@Param("userId") Long userId);

        // Get chat statistics for a user (updated to not use attachmentHash)
        @Query("""
                        SELECT ce.chatId, COUNT(ce) as messageCount, \
                        COUNT(CASE WHEN ce.fileName IS NOT NULL THEN 1 END) as attachmentCount \
                        FROM ChatEntryEntity ce WHERE ce.userId = :userId GROUP BY ce.chatId""")
        List<Object[]> getChatStatisticsByUserId(@Param("userId") Long userId);
}
