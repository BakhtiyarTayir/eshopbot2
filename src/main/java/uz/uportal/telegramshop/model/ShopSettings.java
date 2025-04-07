package uz.uportal.telegramshop.model;

import jakarta.persistence.*;

/**
 * Модель для хранения настроек магазина
 */
@Entity
@Table(name = "shop_settings")
public class ShopSettings {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String phone;
    private String email;
    private String website;
    
    @Column(columnDefinition = "TEXT")
    private String supportInfo;
    
    @Column(columnDefinition = "TEXT")
    private String aboutInfo;
    
    private String workingHours;
    
    // Конструкторы
    public ShopSettings() {
    }
    
    public ShopSettings(String phone, String email, String website, String supportInfo, String aboutInfo, String workingHours) {
        this.phone = phone;
        this.email = email;
        this.website = website;
        this.supportInfo = supportInfo;
        this.aboutInfo = aboutInfo;
        this.workingHours = workingHours;
    }
    
    // Геттеры и сеттеры
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getWebsite() {
        return website;
    }
    
    public void setWebsite(String website) {
        this.website = website;
    }
    
    public String getSupportInfo() {
        return supportInfo;
    }
    
    public void setSupportInfo(String supportInfo) {
        this.supportInfo = supportInfo;
    }
    
    public String getAboutInfo() {
        return aboutInfo;
    }
    
    public void setAboutInfo(String aboutInfo) {
        this.aboutInfo = aboutInfo;
    }
    
    public String getWorkingHours() {
        return workingHours;
    }
    
    public void setWorkingHours(String workingHours) {
        this.workingHours = workingHours;
    }
} 