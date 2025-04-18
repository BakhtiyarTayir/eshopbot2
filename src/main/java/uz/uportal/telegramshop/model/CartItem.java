package uz.uportal.telegramshop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Модель элемента корзины
 */
@Entity
@Table(name = "cart_items")
public class CartItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private TelegramUser user;
    
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;
    
    private Integer quantity;
    
    @Column(name = "price_per_item")
    private BigDecimal pricePerItem;
    
    // Конструкторы
    public CartItem() {
    }
    
    public CartItem(TelegramUser user, Product product, Integer quantity) {
        this.user = user;
        this.product = product;
        this.quantity = quantity;
        this.pricePerItem = product.getPrice();
    }
    
    // Геттеры и сеттеры
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public TelegramUser getUser() {
        return user;
    }
    
    public void setUser(TelegramUser user) {
        this.user = user;
    }
    
    public Product getProduct() {
        return product;
    }
    
    public void setProduct(Product product) {
        this.product = product;
        if (product != null) {
            this.pricePerItem = product.getPrice();
        }
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public BigDecimal getPricePerItem() {
        return pricePerItem;
    }
    
    public void setPricePerItem(BigDecimal pricePerItem) {
        this.pricePerItem = pricePerItem;
    }
    
    // Методы
    public BigDecimal getTotalPrice() {
        return pricePerItem.multiply(new BigDecimal(quantity));
    }
    
    public void incrementQuantity() {
        this.quantity++;
    }
    
    public void decrementQuantity() {
        if (this.quantity > 1) {
            this.quantity--;
        }
    }
} 