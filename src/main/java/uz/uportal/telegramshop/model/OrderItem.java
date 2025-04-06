package uz.uportal.telegramshop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Модель элемента заказа
 */
@Entity
@Table(name = "order_items")
public class OrderItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;
    
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;
    
    private Integer quantity;
    
    @Column(name = "price_per_item")
    private BigDecimal pricePerItem;
    
    @Column(name = "product_name")
    private String productName;
    
    // Конструкторы
    public OrderItem() {
    }
    
    public OrderItem(Product product, Integer quantity) {
        this.product = product;
        this.quantity = quantity;
        this.pricePerItem = product.getPrice();
        this.productName = product.getName();
    }
    
    public OrderItem(CartItem cartItem) {
        this.product = cartItem.getProduct();
        this.quantity = cartItem.getQuantity();
        this.pricePerItem = cartItem.getPricePerItem();
        this.productName = cartItem.getProduct().getName();
    }
    
    // Геттеры и сеттеры
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Order getOrder() {
        return order;
    }
    
    public void setOrder(Order order) {
        this.order = order;
    }
    
    public Product getProduct() {
        return product;
    }
    
    public void setProduct(Product product) {
        this.product = product;
        if (product != null) {
            this.productName = product.getName();
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
    
    public String getProductName() {
        return productName;
    }
    
    public void setProductName(String productName) {
        this.productName = productName;
    }
    
    // Методы
    public BigDecimal getTotalPrice() {
        return pricePerItem.multiply(new BigDecimal(quantity));
    }
} 