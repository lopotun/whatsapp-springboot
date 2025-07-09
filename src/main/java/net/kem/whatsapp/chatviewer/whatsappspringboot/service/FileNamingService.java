package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service
public class FileNamingService {
    
    @Value("${app.multimedia.storage.path}")
    private String multimediaStoragePath;
    
    /**
     * Generates a content-based file path using SHA-256 hash with two-level directory structure.
     * 
     * @param content The file content bytes
     * @param originalFilename The original filename (for extension)
     * @return The target path where the file should be stored
     */
    public Path generateFilePath(byte[] content, String originalFilename) {
        String hash = generateSHA256Hash(content);
        String extension = getFileExtension(originalFilename);
        
        // Two-level directory structure: 3 chars + 3 chars
        String level1Dir = hash.substring(0, 3);
        String level2Dir = hash.substring(3, 6);
        String filename = hash.substring(6); // Rest of hash
        
        // Create path: /storage/abc/def/restofhash.ext
        String finalFilename = filename + (extension.isEmpty() ? "" : "." + extension);
        Path targetPath = Paths.get(multimediaStoragePath, level1Dir, level2Dir, finalFilename);
        
        return targetPath;
    }
    
    /**
     * Moves a file from temporary location to its final content-based location.
     * 
     * @param tempFile The temporary file path
     * @param originalFilename The original filename
     * @return The final file path
     * @throws IOException If file operations fail
     */
    public Path moveToFinalLocation(Path tempFile, String originalFilename) throws IOException {
        // Read the file content to calculate hash
        byte[] content = Files.readAllBytes(tempFile);
        Path finalPath = generateFilePath(content, originalFilename);
        
        // Create parent directories if they don't exist
        Path parentDir = finalPath.getParent();
        if (!Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            log.debug("Created directory: {}", parentDir);
        }
        
        // Move the file to final location
        Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Moved file to final location: {}", finalPath);
        
        return finalPath;
    }
    
    /**
     * Moves a file from temporary location to its final content-based location using pre-calculated hash.
     * 
     * @param tempFile The temporary file path
     * @param originalFilename The original filename
     * @param contentHash The pre-calculated SHA-256 hash of the file content
     * @return The final file path
     * @throws IOException If file operations fail
     */
    public Path moveToFinalLocationWithHash(Path tempFile, String originalFilename, String contentHash) throws IOException {
        Path finalPath = generateFilePathFromHash(contentHash, originalFilename);
        
        // Create parent directories if they don't exist
        Path parentDir = finalPath.getParent();
        if (!Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            log.debug("Created directory: {}", parentDir);
        }
        
        // Move the file to final location
        Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Moved file to final location: {}", finalPath);
        
        return finalPath;
    }
    
    /**
     * Generates a file path using a pre-calculated hash with two-level directory structure.
     * 
     * @param contentHash The SHA-256 hash of the file content
     * @param originalFilename The original filename (for extension)
     * @return The target path where the file should be stored
     */
    public Path generateFilePathFromHash(String contentHash, String originalFilename) {
        String extension = getFileExtension(originalFilename);
        
        // Two-level directory structure: 3 chars + 3 chars
        String level1Dir = contentHash.substring(0, 3);
        String level2Dir = contentHash.substring(3, 6);
        String filename = contentHash;// Keep the full hash as filename
        
        // Create path: /storage/abc/def/restofhash.ext
        String finalFilename = filename + (extension.isEmpty() ? "" : "." + extension);
        Path targetPath = Paths.get(multimediaStoragePath, level1Dir, level2Dir, finalFilename);
        
        return targetPath;
    }
    
    /**
     * Generates SHA-256 hash of the given content.
     */
    private String generateSHA256Hash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    /**
     * Converts byte array to hexadecimal string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Extracts file extension from filename.
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }
}