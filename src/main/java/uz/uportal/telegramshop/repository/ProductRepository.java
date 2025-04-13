package uz.uportal.telegramshop.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.model.Product;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    List<Product> findByCategory(Category category);
    
    Page<Product> findByCategory(Category category, Pageable pageable);
    
    // Добавляем методы для работы с полем active
    List<Product> findByActiveTrue();
    
    Page<Product> findByActiveTrue(Pageable pageable);
    
    List<Product> findByCategoryAndActiveTrue(Category category);
    
    Page<Product> findByCategoryAndActiveTrue(Category category, Pageable pageable);
} 