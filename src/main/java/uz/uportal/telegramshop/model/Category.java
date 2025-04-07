package uz.uportal.telegramshop.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories")
public class Category {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    
    @Column(length = 1000)
    private String description;
    
    @Column(unique = true)
    private String slug;
    
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL)
    private List<Product> products = new ArrayList<>();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;
    
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<Category> subcategories = new ArrayList<>();
    
    // Конструкторы, геттеры и сеттеры
    
    public Category() {
    }
    
    public Category(String name) {
        this.name = name;
        this.slug = generateSlug(name);
    }
    
    public Category(String name, String description) {
        this.name = name;
        this.description = description;
        this.slug = generateSlug(name);
    }
    
    public Category(String name, String description, Category parent) {
        this.name = name;
        this.description = description;
        this.parent = parent;
        this.slug = generateSlug(name);
    }
    
    private String generateSlug(String name) {
        return name.toLowerCase()
                  .replaceAll("[^a-zA-Z0-9\\s]", "")
                  .replaceAll("\\s+", "-");
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getSlug() {
        return slug;
    }
    
    public void setSlug(String slug) {
        this.slug = slug;
    }
    
    public List<Product> getProducts() {
        return products;
    }
    
    public void setProducts(List<Product> products) {
        this.products = products;
    }
    
    public Category getParent() {
        return parent;
    }
    
    public void setParent(Category parent) {
        this.parent = parent;
    }
    
    public List<Category> getSubcategories() {
        return subcategories;
    }
    
    public void setSubcategories(List<Category> subcategories) {
        this.subcategories = subcategories;
    }
    
    public boolean hasSubcategories() {
        return subcategories != null && !subcategories.isEmpty();
    }
    
    public boolean isMainCategory() {
        return parent == null;
    }
} 