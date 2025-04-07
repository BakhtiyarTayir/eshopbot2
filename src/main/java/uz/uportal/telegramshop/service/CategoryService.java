package uz.uportal.telegramshop.service;

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
     * Удалить категорию
     * @param id ID категории
     * @return true, если категория успешно удалена
     */
    @Transactional
    public boolean deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            return false;
        }
        
        categoryRepository.deleteById(id);
        return true;
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
        Optional<Category> categoryOpt = getCategoryById(categoryId);
        if (categoryOpt.isEmpty()) {
            return false;
        }
        
        Category category = categoryOpt.get();
        List<Product> products = productRepository.findByCategory(category);
        return !products.isEmpty();
    }
    
    /**
     * Проверяет, есть ли подкатегории у категории
     * @param categoryId ID категории
     * @return true, если у категории есть подкатегории, иначе false
     */
    public boolean categoryHasSubcategories(Long categoryId) {
        List<Category> subcategories = getSubcategories(categoryId);
        return !subcategories.isEmpty();
    }
} 