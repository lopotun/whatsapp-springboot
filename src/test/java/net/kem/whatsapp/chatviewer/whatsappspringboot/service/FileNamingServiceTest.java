package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest(properties = "spring.profiles.active=test")
@TestPropertySource(properties = {
    "app.multimedia.storage.path=./test-multimedia-files"
})
class FileNamingServiceTest {

    @Autowired
    private FileNamingService fileNamingService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Clean up test directory before each test
        try {
            Files.walk(Paths.get("./test-multimedia-files"))
                    .sorted(Comparator.reverseOrder()) // Delete files first, then directories
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        } catch (IOException e) {
            // Directory might not exist, which is fine
        }
    }

    @Test
    void generateFilePath_shouldCreateConsistentPath() {
        // Given
        byte[] content = "test content".getBytes();
        String originalFilename = "test-image.jpg";

        // When
        Path path1 = fileNamingService.generateFilePath(content, originalFilename);
        Path path2 = fileNamingService.generateFilePath(content, originalFilename);

        // Then
        assertEquals(path1, path2, "Same content should generate same path");
        assertTrue(path1.toString().endsWith(".jpg"), "Should preserve file extension");
        assertTrue(path1.toString().contains("/"), "Should create directory structure");
    }

    @Test
    void generateFilePath_shouldCreateDifferentPathsForDifferentContent() {
        // Given
        byte[] content1 = "test content 1".getBytes();
        byte[] content2 = "test content 2".getBytes();
        String originalFilename = "test-image.jpg";

        // When
        Path path1 = fileNamingService.generateFilePath(content1, originalFilename);
        Path path2 = fileNamingService.generateFilePath(content2, originalFilename);

        // Then
        assertNotEquals(path1, path2, "Different content should generate different paths");
    }

    @Test
    void moveToFinalLocation_shouldMoveFileToContentBasedLocation() throws IOException {
        // Given
        String content = "test file content";
        String originalFilename = "test-document.pdf";
        Path tempFile = tempDir.resolve("temp-file");
        Files.write(tempFile, content.getBytes());

        // When
        Path finalPath = fileNamingService.moveToFinalLocation(tempFile, originalFilename);

        // Then
        assertFalse(Files.exists(tempFile), "Temp file should be moved");
        assertTrue(Files.exists(finalPath), "File should exist at final location");
        assertTrue(finalPath.toString().endsWith(".pdf"), "Should preserve file extension");
        
        // Verify content is preserved
        String finalContent = new String(Files.readAllBytes(finalPath));
        assertEquals(content, finalContent, "Content should be preserved");
    }

    @Test
    void moveToFinalLocation_shouldCreateDirectoryStructure() throws IOException {
        // Given
        String content = "test content";
        String originalFilename = "test.txt";
        Path tempFile = tempDir.resolve("temp-file");
        Files.write(tempFile, content.getBytes());

        // When
        Path finalPath = fileNamingService.moveToFinalLocation(tempFile, originalFilename);

        // Then
        Path parentDir = finalPath.getParent();
        assertTrue(Files.exists(parentDir), "Parent directory should be created");
        assertTrue(parentDir.toString().contains("/"), "Should have directory structure");
    }

    @Test
    void moveToFinalLocation_shouldHandleDuplicateContent() throws IOException {
        // Given
        String content = "duplicate content";
        String filename1 = "file1.txt";
        String filename2 = "file2.txt";
        
        Path tempFile1 = tempDir.resolve("temp-file1");
        Path tempFile2 = tempDir.resolve("temp-file2");
        Files.write(tempFile1, content.getBytes());
        Files.write(tempFile2, content.getBytes());

        // When
        Path finalPath1 = fileNamingService.moveToFinalLocation(tempFile1, filename1);
        Path finalPath2 = fileNamingService.moveToFinalLocation(tempFile2, filename2);

        // Then
        assertEquals(finalPath1, finalPath2, "Same content should result in same final path");
        assertTrue(Files.exists(finalPath1), "File should exist at final location");
    }
    
    @Test
    void moveToFinalLocationWithHash_shouldMoveFileUsingPreCalculatedHash() throws IOException {
        // Given
        String content = "test content for hash";
        String originalFilename = "test-video.mp4";
        Path tempFile = tempDir.resolve("temp-file");
        Files.write(tempFile, content.getBytes());
        
        // Calculate hash manually for testing
        String expectedHash = "b79f8c07798dcc75d6f288e6a620644a88a9c67e74019a57b88a5bfd918e4b0f";

        // When
        Path finalPath = fileNamingService.moveToFinalLocationWithHash(tempFile, originalFilename, expectedHash);

        // Then
        assertFalse(Files.exists(tempFile), "Temp file should be moved");
        assertTrue(Files.exists(finalPath), "File should exist at final location");
        assertTrue(finalPath.toString().endsWith(".mp4"), "Should preserve file extension");
        assertTrue(finalPath.toString().contains("b79/f8c"), "Should use hash for directory structure");
        
        // Verify content is preserved
        String finalContent = new String(Files.readAllBytes(finalPath));
        assertEquals(content, finalContent, "Content should be preserved");
    }
    
    @Test
    void generateFilePathFromHash_shouldCreateConsistentPath() {
        // Given
        String hash = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        String originalFilename = "test-image.jpg";

        // When
        Path path1 = fileNamingService.generateFilePathFromHash(hash, originalFilename);
        Path path2 = fileNamingService.generateFilePathFromHash(hash, originalFilename);

        // Then
        assertEquals(path1, path2, "Same hash should generate same path");
        assertTrue(path1.toString().endsWith(".jpg"), "Should preserve file extension");
        assertTrue(path1.toString().contains("abc/def"), "Should use hash for directory structure");
    }
} 