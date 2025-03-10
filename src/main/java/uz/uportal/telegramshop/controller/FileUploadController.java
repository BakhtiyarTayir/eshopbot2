package uz.uportal.telegramshop.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.uportal.telegramshop.service.FileStorageService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {
    
    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);
    
    private final FileStorageService fileStorageService;
    
    public FileUploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }
    
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String filename = fileStorageService.storeFile(file);
            String fileUrl = fileStorageService.getFileUrl(filename);
            
            Map<String, String> response = new HashMap<>();
            response.put("filename", filename);
            response.put("url", fileUrl);
            
            logger.info("File uploaded: {}", fileUrl);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("Error uploading file", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/upload-from-url")
    public ResponseEntity<Map<String, String>> uploadFileFromUrl(@RequestParam("url") String imageUrl) {
        try {
            String filename = fileStorageService.storeFileFromUrl(imageUrl);
            String fileUrl = fileStorageService.getFileUrl(filename);
            
            Map<String, String> response = new HashMap<>();
            response.put("filename", filename);
            response.put("url", fileUrl);
            
            logger.info("File uploaded from URL: {}", fileUrl);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("Error uploading file from URL", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @DeleteMapping("/{filename}")
    public ResponseEntity<Void> deleteFile(@PathVariable String filename) {
        boolean deleted = fileStorageService.deleteFile(filename);
        if (deleted) {
            logger.info("File deleted: {}", filename);
            return ResponseEntity.ok().build();
        } else {
            logger.warn("File not found: {}", filename);
            return ResponseEntity.notFound().build();
        }
    }
} 