package uz.uportal.telegramshop.service;

import org.springframework.stereotype.Service;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.model.Product;
import uz.uportal.telegramshop.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {
    
    private final ProductRepository productRepository;
    
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }
    
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
    
    public List<Product> getProductsByCategory(Category category) {
        return productRepository.findByCategory(category);
    }
    
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }
    
    public Product createProduct(String name, String description, BigDecimal price, String imageUrl, Integer stock, Category category) {
        Product product = new Product(name, description, price, imageUrl, stock, category);
        return productRepository.save(product);
    }
    
    public Product createProduct(String name, String description, BigDecimal price, Integer stock, Category category) {
        return createProduct(name, description, price, null, stock, category);
    }
    
    public Product updateProduct(Product product) {
        return productRepository.save(product);
    }
    
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }
} 