package uz.uportal.telegramshop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.model.Product;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategory(Category category);
} 