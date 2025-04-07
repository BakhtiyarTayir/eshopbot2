package uz.uportal.telegramshop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.uportal.telegramshop.model.Category;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Category findByName(String name);
    Category findBySlug(String slug);
    List<Category> findByParentIsNull();
    List<Category> findByParentId(Long parentId);
} 