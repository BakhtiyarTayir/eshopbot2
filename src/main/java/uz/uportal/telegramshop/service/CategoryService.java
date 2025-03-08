package uz.uportal.telegramshop.service;

import org.springframework.stereotype.Service;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.repository.CategoryRepository;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {
    
    private final CategoryRepository categoryRepository;
    
    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }
    
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }
    
    public Optional<Category> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }
    
    public Category getCategoryByName(String name) {
        return categoryRepository.findByName(name);
    }
    
    public Category createCategory(String name) {
        // Проверяем, что имя не пустое
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя категории не может быть пустым");
        }
        
        // Проверяем, существует ли уже категория с таким именем
        Category existingCategory = categoryRepository.findByName(name);
        if (existingCategory != null) {
            return existingCategory;
        }
        
        // Создаем новую категорию
        Category newCategory = new Category(name);
        
        // Проверяем, что slug не пустой
        String slug = newCategory.getSlug();
        if (slug == null || slug.trim().isEmpty()) {
            // Генерируем уникальный slug
            slug = name.toLowerCase()
                      .replaceAll("[^a-zA-Z0-9\\s]", "")
                      .replaceAll("\\s+", "-");
            
            // Если slug все еще пустой, используем timestamp
            if (slug.trim().isEmpty()) {
                slug = "category-" + System.currentTimeMillis();
            }
            
            newCategory.setSlug(slug);
        }
        
        return categoryRepository.save(newCategory);
    }
    
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }
    
    public Category getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug);
    }
    
    public Category updateCategory(Category category) {
        return categoryRepository.save(category);
    }
} 