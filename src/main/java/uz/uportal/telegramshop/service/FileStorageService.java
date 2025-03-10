package uz.uportal.telegramshop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);
    
    @Value("${app.upload.dir}")
    private String uploadDir;
    
    @Value("${app.upload.url}")
    private String uploadUrl;
    
    /**
     * Сохраняет файл в хранилище
     * @param file файл для сохранения
     * @return путь к сохраненному файлу
     * @throws IOException если произошла ошибка при сохранении файла
     */
    public String storeFile(MultipartFile file) throws IOException {
        // Создаем директорию, если она не существует
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // Генерируем уникальное имя файла
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String filename = UUID.randomUUID().toString() + extension;
        
        // Сохраняем файл
        Path filePath = uploadPath.resolve(filename);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
        
        logger.info("Saved file: {}", filePath);
        return filename;
    }
    
    /**
     * Сохраняет файл из URL в хранилище
     * @param imageUrl URL изображения
     * @return путь к сохраненному файлу
     * @throws IOException если произошла ошибка при сохранении файла
     */
    public String storeFileFromUrl(String imageUrl) throws IOException {
        // Создаем директорию, если она не существует
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // Генерируем уникальное имя файла
        String extension = "";
        if (imageUrl.contains(".")) {
            extension = imageUrl.substring(imageUrl.lastIndexOf("."));
        }
        String filename = UUID.randomUUID().toString() + extension;
        
        // Сохраняем файл
        Path filePath = uploadPath.resolve(filename);
        try (InputStream inputStream = new URL(imageUrl).openStream()) {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (MalformedURLException e) {
            logger.error("Invalid URL: {}", imageUrl, e);
            throw e;
        }
        
        logger.info("Saved file from URL: {}", filePath);
        return filename;
    }
    
    /**
     * Получает полный URL для доступа к файлу
     * @param filename имя файла
     * @return полный URL для доступа к файлу
     */
    public String getFileUrl(String filename) {
        return uploadUrl + "/" + filename;
    }
    
    /**
     * Удаляет файл из хранилища
     * @param filename имя файла
     * @return true, если файл успешно удален
     */
    public boolean deleteFile(String filename) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(filename);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            logger.error("Error deleting file: {}", filename, e);
            return false;
        }
    }
} 