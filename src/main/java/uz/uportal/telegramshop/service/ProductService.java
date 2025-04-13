package uz.uportal.telegramshop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.model.Product;
import uz.uportal.telegramshop.repository.ProductRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с товарами
 */
@Service
public class ProductService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    
    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;
    
    @Autowired
    public ProductService(ProductRepository productRepository, FileStorageService fileStorageService) {
        this.productRepository = productRepository;
        this.fileStorageService = fileStorageService;
    }
    
    /**
     * Получить все товары
     * @return список всех активных товаров
     */
    public List<Product> getAllProducts() {
        return productRepository.findByActiveTrue();
    }
    
    /**
     * Получить все товары с пагинацией
     * @param pageable параметры пагинации
     * @return страница активных товаров
     */
    public Page<Product> getAllProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable);
    }
    
    /**
     * Получить товар по ID
     * @param id ID товара
     * @return товар или пустой Optional, если товар не найден
     */
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }
    
    /**
     * Получить товары по категории
     * @param category категория товаров
     * @return список активных товаров в категории
     */
    public List<Product> getProductsByCategory(Category category) {
        return productRepository.findByCategoryAndActiveTrue(category);
    }
    
    /**
     * Получить товары по категории с пагинацией
     * @param category категория товаров
     * @param pageable параметры пагинации
     * @return страница активных товаров в категории
     */
    public Page<Product> getProductsByCategory(Category category, Pageable pageable) {
        return productRepository.findByCategoryAndActiveTrue(category, pageable);
    }
    
    /**
     * Создать новый товар
     * @param name название товара
     * @param description описание товара
     * @param price цена товара
     * @param stock количество товара в наличии
     * @param category категория товара
     * @return созданный товар
     */
    public Product createProduct(String name, String description, BigDecimal price, Integer stock, Category category) {
        Product product = new Product(name, description, price, stock, category);
        return productRepository.save(product);
    }
    
    /**
     * Создать новый товар с изображением
     * @param name название товара
     * @param description описание товара
     * @param price цена товара
     * @param imageUrl URL изображения товара
     * @param stock количество товара в наличии
     * @param category категория товара
     * @return созданный товар
     */
    public Product createProduct(String name, String description, BigDecimal price, String imageUrl, Integer stock, Category category) {
        Product product = new Product(name, description, price, imageUrl, stock, category);
        return productRepository.save(product);
    }
    
    /**
     * Обновить товар
     * @param id ID товара
     * @param name новое название товара
     * @param description новое описание товара
     * @param price новая цена товара
     * @param stock новое количество товара в наличии
     * @param category новая категория товара
     * @return обновленный товар или null, если товар не найден
     */
    public Product updateProduct(Long id, String name, String description, BigDecimal price, Integer stock, Category category) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isEmpty()) {
            return null;
        }
        
        Product product = productOpt.get();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setStock(stock);
        product.setCategory(category);
        
        return productRepository.save(product);
    }
    
    /**
     * Обновить изображение товара
     * @param id ID товара
     * @param imageUrl новый URL изображения товара
     * @return обновленный товар или null, если товар не найден
     */
    public Product updateProductImage(Long id, String imageUrl) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isEmpty()) {
            return null;
        }
        
        Product product = productOpt.get();
        product.setImageUrl(imageUrl);
        
        return productRepository.save(product);
    }
    
    /**
     * Удаляет продукт по ID
     * @param id ID продукта
     * @return true если продукт успешно удален, false если продукт не найден
     */
    public boolean deleteProduct(Long id) {
        Optional<Product> productOptional = productRepository.findById(id);
        if (productOptional.isEmpty()) {
            return false;
        }
        
        // Получаем продукт
        Product product = productOptional.get();
        
        // Вместо физического удаления, устанавливаем active = false
        product.setActive(false);
        productRepository.save(product);
        
        logger.info("Товар с ID {} помечен как неактивный", id);
        return true;
    }
    
    /**
     * Создает новый продукт с загрузкой изображения
     * @param name название продукта
     * @param description описание продукта
     * @param price цена продукта
     * @param imageFile файл изображения
     * @param stock количество на складе
     * @param category категория продукта
     * @return созданный продукт
     * @throws IOException если произошла ошибка при сохранении изображения
     */
    public Product createProductWithImage(String name, String description, BigDecimal price, 
                                         MultipartFile imageFile, Integer stock, Category category) throws IOException {
        String imageUrl = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            imageUrl = saveImageFile(imageFile);
            logger.info("Создан продукт '{}' с изображением", name);
        } else {
            logger.info("Создан продукт '{}' без изображения", name);
        }
        
        return createProduct(name, description, price, imageUrl, stock, category);
    }
    
    /**
     * Обновляет изображение продукта
     * @param productId ID продукта
     * @param imageFile файл изображения
     * @return обновленный продукт
     * @throws IOException если произошла ошибка при сохранении изображения
     */
    public Product updateProductImage(Long productId, MultipartFile imageFile) throws IOException {
        Product product = getProductOrThrow(productId);
        
        // Удаляем старое изображение
        deleteProductImage(product);
        
        // Сохраняем новое изображение
        String imageUrl = saveImageFile(imageFile);
        product.setImageUrl(imageUrl);
        
        logger.info("Обновлено изображение для продукта с ID {}", productId);
        return productRepository.save(product);
    }
    
    /**
     * Обновляет изображение продукта из URL
     * @param productId ID продукта
     * @param imageUrl URL изображения
     * @return обновленный продукт
     * @throws IOException если произошла ошибка при сохранении изображения
     */
    public Product updateProductImageFromUrl(Long productId, String imageUrl) throws IOException {
        Product product = getProductOrThrow(productId);
        
        // Удаляем старое изображение
        deleteProductImage(product);
        
        // Сохраняем новое изображение
        String newImageUrl = saveImageFromUrl(imageUrl);
        product.setImageUrl(newImageUrl);
        
        logger.info("Обновлено изображение из URL для продукта с ID {}", productId);
        return productRepository.save(product);
    }
    
    /**
     * Удаляет изображение продукта
     * @param productId ID продукта
     * @return обновленный продукт без изображения
     */
    public Product removeProductImage(Long productId) {
        Product product = getProductOrThrow(productId);
        
        // Удаляем изображение
        boolean deleted = deleteProductImage(product);
        
        if (deleted) {
            product.setImageUrl(null);
            logger.info("Удалено изображение для продукта с ID {}", productId);
        }
        
        return productRepository.save(product);
    }
    
    // Вспомогательные методы
    
    /**
     * Получает продукт по ID или выбрасывает исключение
     * @param productId ID продукта
     * @return найденный продукт
     * @throws IllegalArgumentException если продукт не найден
     */
    private Product getProductOrThrow(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Продукт с ID " + productId + " не найден"));
    }
    
    /**
     * Сохраняет файл изображения
     * @param imageFile файл изображения
     * @return URL сохраненного изображения
     * @throws IOException если произошла ошибка при сохранении
     */
    private String saveImageFile(MultipartFile imageFile) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("Файл изображения не может быть пустым");
        }
        
        String filename = fileStorageService.storeFile(imageFile);
        return fileStorageService.getFileUrl(filename);
    }
    
    /**
     * Сохраняет изображение из URL
     * @param imageUrl URL изображения
     * @return URL сохраненного изображения
     * @throws IOException если произошла ошибка при сохранении
     */
    private String saveImageFromUrl(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("URL изображения не может быть пустым");
        }
        
        String filename = fileStorageService.storeFileFromUrl(imageUrl);
        return fileStorageService.getFileUrl(filename);
    }
    
    /**
     * Удаляет изображение продукта
     * @param product продукт
     * @return true, если изображение было удалено
     */
    private boolean deleteProductImage(Product product) {
        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            String filename = extractFilenameFromUrl(product.getImageUrl());
            return fileStorageService.deleteFile(filename);
        }
        return false;
    }
    
    /**
     * Извлекает имя файла из URL
     * @param url URL файла
     * @return имя файла
     */
    private String extractFilenameFromUrl(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }
} 