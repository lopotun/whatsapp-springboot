package net.kem.whatsapp.chatviewer.zipper.repository;

import net.kem.whatsapp.chatviewer.shared.model.ChatEntry;
import net.kem.whatsapp.chatviewer.shared.model.ChatEntryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for managing ChatEntryEntity entities in the zipper-service database
 */
@Repository
public interface ChatEntryRepository extends JpaRepository<ChatEntryEntity, Long> {

    /**
     * Find chat entries by user ID and chat ID
     */
    List<ChatEntryEntity> findByUserIdAndChatIdOrderByLocalDateTimeAsc(Long userId, String chatId);

    /**
     * Find chat entries by user ID and chat ID with pagination
     */
    Page<ChatEntryEntity> findByUserIdAndChatId(Long userId, String chatId, Pageable pageable);

    /**
     * Find chat entries by user ID
     */
    Page<ChatEntryEntity> findByUserId(Long userId, Pageable pageable);

    /**
     * Find chat entries by user ID and type
     */
    List<ChatEntryEntity> findByUserIdAndType(Long userId, ChatEntry.Type type);

    /**
     * Find chat entries by user ID and author
     */
    List<ChatEntryEntity> findByUserIdAndAuthor(Long userId, String author);

    /**
     * Find chat entries by user ID and date range
     */
    @Query("SELECT ce FROM ChatEntryEntity ce WHERE ce.userId = :userId AND ce.localDateTime BETWEEN :startDate AND :endDate ORDER BY ce.localDateTime ASC")
    List<ChatEntryEntity> findByUserIdAndDateRange(@Param("userId") Long userId,
                                                   @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find chat entries by user ID, chat ID and date range
     */
    @Query("SELECT ce FROM ChatEntryEntity ce WHERE ce.userId = :userId AND ce.chatId = :chatId AND ce.localDateTime BETWEEN :startDate AND :endDate ORDER BY ce.localDateTime ASC")
    List<ChatEntryEntity> findByUserIdAndChatIdAndDateRange(@Param("userId") Long userId,
                                                            @Param("chatId") String chatId, @Param("startDate") LocalDateTime startDate,
                                                            @Param("endDate") LocalDateTime endDate);

    /**
     * Check if a chat entry exists by unique fields
     */
    @Query("SELECT COUNT(ce) > 0 FROM ChatEntryEntity ce WHERE ce.userId = :userId AND ce.localDateTime = :localDateTime AND ce.author = :author AND ce.payload = :payload")
    boolean existsByUniqueFields(@Param("userId") Long userId,
            @Param("localDateTime") LocalDateTime localDateTime, @Param("author") String author,
            @Param("payload") String payload);

    /**
     * Find chat entries with attachments
     */
    @Query("SELECT ce FROM ChatEntryEntity ce WHERE ce.userId = :userId AND ce.attachment IS NOT NULL")
    List<ChatEntryEntity> findEntriesWithAttachments(@Param("userId") Long userId);

    /**
     * Count chat entries by user ID
     */
    long countByUserId(Long userId);

    /**
     * Count chat entries by user ID and chat ID
     */
    long countByUserIdAndChatId(Long userId, String chatId);

    /**
     * Delete chat entries by user ID and chat ID
     */
    void deleteByUserIdAndChatId(Long userId, String chatId);

    /**
     * Delete chat entries by user ID
     */
    void deleteByUserId(Long userId);
}
