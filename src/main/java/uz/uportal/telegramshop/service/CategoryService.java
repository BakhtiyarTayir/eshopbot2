package uz.uportal.telegramshop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.model.Product;
import uz.uportal.telegramshop.repository.CategoryRepository;
import uz.uportal.telegramshop.repository.ProductRepository;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с категориями
 */
@Service
public class CategoryService {
    
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private static final Logger logger = LoggerFactory.getLogger(CategoryService.class);
    
    /**
     * Результат операции удаления
     */
    public static class DeleteResult {
        private final boolean success;
        private final String message;
        
        public DeleteResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }
    
    /**
     * Получить все категории
     * @return список всех категорий
     */
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }
    
    /**
     * Получить все основные категории (без родительской категории)
     * @return список основных категорий
     */
    public List<Category> getMainCategories() {
        return categoryRepository.findByParentIsNull();
    }
    
    /**
     * Получить подкатегории для указанной родительской категории
     * @param parentId ID родительской категории
     * @return список подкатегорий
     */
    public List<Category> getSubcategories(Long parentId) {
        return categoryRepository.findByParentId(parentId);
    }
    
    /**
     * Получить все категории с пагинацией
     * @param pageable параметры пагинации
     * @return страница категорий
     */
    public Page<Category> getAllCategories(Pageable pageable) {
        return categoryRepository.findAll(pageable);
    }
    
    /**
     * Получить категорию по ID
     * @param id ID категории
     * @return категория или пустой Optional, если категория не найдена
     */
    public Optional<Category> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }
    
    public Category getCategoryByName(String name) {
        return categoryRepository.findByName(name);
    }
    
    /**
     * Создать новую категорию
     * @param name название категории
     * @param description описание категории
     * @return созданная категория
     */
    @Transactional
    public Category createCategory(String name, String description) {
        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        return categoryRepository.save(category);
    }
    
    /**
     * Создать новую подкатегорию
     * @param name название подкатегории
     * @param description описание подкатегории
     * @param parentId ID родительской категории
     * @return созданная подкатегория или null, если родительская категория не найдена
     */
    @Transactional
    public Category createSubcategory(String name, String description, Long parentId) {
        Optional<Category> parentOpt = categoryRepository.findById(parentId);
        if (parentOpt.isEmpty()) {
            return null;
        }
        
        Category parent = parentOpt.get();
        Category subcategory = new Category(name, description, parent);
        return categoryRepository.save(subcategory);
    }
    
    /**
     * Обновить категорию
     * @param id ID категории
     * @param name новое название категории
     * @param description новое описание категории
     * @return обновленная категория или null, если категория не найдена
     */
    @Transactional
    public Category updateCategory(Long id, String name, String description) {
        Optional<Category> categoryOpt = categoryRepository.findById(id);
        if (categoryOpt.isEmpty()) {
            return null;
        }
        
        Category category = categoryOpt.get();
        category.setName(name);
        category.setDescription(description);
        
        return categoryRepository.save(category);
    }
    
    /**
     * Обновить родительскую категорию для подкатегории
     * @param id ID категории
     * @param parentId ID новой родительской категории (null для превращения в основную категорию)
     * @return обновленная категория или null, если категория не найдена
     */
    @Transactional
    public Category updateCategoryParent(Long id, Long parentId) {
        Optional<Category> categoryOpt = categoryRepository.findById(id);
        if (categoryOpt.isEmpty()) {
            return null;
        }
        
        Category category = categoryOpt.get();
        
        if (parentId == null) {
            category.setParent(null);
        } else {
            Optional<Category> parentOpt = categoryRepository.findById(parentId);
            if (parentOpt.isEmpty()) {
                return null;
            }
            category.setParent(parentOpt.get());
        }
        
        return categoryRepository.save(category);
    }
    
    /**
     * Удаляет категорию по ID
     * @param id ID категории
     * @return результат операции удаления с информацией об успехе или ошибке
     */
    @Transactional
    public DeleteResult deleteCategoryWithResult(Long id) {
        logger.info("Выполняем deleteCategoryWithResult для категории с ID={}", id);
        
        if (!categoryRepository.existsById(id)) {
            logger.info("Категория с ID={} не найдена", id);
            return new DeleteResult(false, "Категория не найдена. Возможно, она уже была удалена.");
        }
        
        Optional<Category> categoryOpt = getCategoryById(id);
        Category category = categoryOpt.get();
        logger.info("Категория найдена: ID={}, Имя='{}', Описание='{}'", 
                category.getId(), category.getName(), category.getDescription());
        
        if (category.getParent() != null) {
            logger.info("Родительская категория: ID={}, Имя='{}'", 
                    category.getParent().getId(), category.getParent().getName());
        } else {
            logger.info("Родительская категория: нет (основная категория)");
        }
        
        // Проверяем, есть ли в категории товары
        List<Product> products = productRepository.findByCategory(category);
        if (!products.isEmpty()) {
            logger.warn("Невозможно удалить категорию ID={}, т.к. в ней есть {} товаров:", id, products.size());
            for (int i = 0; i < Math.min(5, products.size()); i++) {
                Product product = products.get(i);
                logger.warn("  - Товар {}: ID={}, Имя='{}'", i+1, product.getId(), product.getName());
            }
            if (products.size() > 5) {
                logger.warn("  ... и ещё {} товаров", products.size() - 5);
            }
            return new DeleteResult(false, "Категорию нельзя удалить, так как в ней есть товары. Сначала удалите или переместите все товары из этой категории.");
        }
        
        // Проверяем, есть ли у категории подкатегории
        List<Category> subcategories = getSubcategories(id);
        if (!subcategories.isEmpty()) {
            logger.warn("Невозможно удалить категорию ID={}, т.к. у неё есть {} подкатегорий:", id, subcategories.size());
            for (int i = 0; i < Math.min(5, subcategories.size()); i++) {
                Category subcat = subcategories.get(i);
                logger.warn("  - Подкатегория {}: ID={}, Имя='{}'", i+1, subcat.getId(), subcat.getName());
            }
            if (subcategories.size() > 5) {
                logger.warn("  ... и ещё {} подкатегорий", subcategories.size() - 5);
            }
            return new DeleteResult(false, "Категорию нельзя удалить, так как у неё есть подкатегории. Сначала удалите все подкатегории.");
        }
        
        logger.info("Все проверки пройдены, выполняем удаление категории ID={}", id);
        
        try {
            categoryRepository.deleteById(id);
            logger.info("Категория с ID={} успешно удалена", id);
            return new DeleteResult(true, "Категория успешно удалена.");
        } catch (Exception e) {
            logger.error("Ошибка при удалении категории ID={}: {}", id, e.getMessage(), e);
            return new DeleteResult(false, "Не удалось удалить категорию. Пожалуйста, попробуйте позже. Ошибка: " + e.getMessage());
        }
    }
    
    /**
     * Удаляет категорию по ID
     * @param id ID категории
     * @return true, если категория успешно удалена
     */
    @Transactional
    public boolean deleteCategory(Long id) {
        DeleteResult result = deleteCategoryWithResult(id);
        return result.isSuccess();
    }
    
    public Category getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug);
    }
    
    /**
     * Проверяет, есть ли товары в категории
     * @param categoryId ID категории
     * @return true, если в категории есть товары, иначе false
     */
    public boolean categoryHasProducts(Long categoryId) {
        logger.info("Проверяем наличие товаров в категории ID={}", categoryId);
        
        Optional<Category> categoryOpt = getCategoryById(categoryId);
        if (categoryOpt.isEmpty()) {
            logger.info("Категория с ID={} не найдена при проверке товаров", categoryId);
            return false;
        }
        
        Category category = categoryOpt.get();
        List<Product> products = productRepository.findByCategory(category);
        logger.info("Категория ID={} содержит {} товаров", categoryId, products.size());
        return !products.isEmpty();
    }
    
    /**
     * Проверяет, есть ли подкатегории у категории
     * @param categoryId ID категории
     * @return true, если у категории есть подкатегории, иначе false
     */
    public boolean categoryHasSubcategories(Long categoryId) {
        logger.info("Проверяем наличие подкатегорий у категории ID={}", categoryId);
        
        List<Category> subcategories = getSubcategories(categoryId);
        logger.info("Категория ID={} имеет {} подкатегорий", categoryId, subcategories.size());
        return !subcategories.isEmpty();
    }
} 