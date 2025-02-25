package uz.uportal.telegramshop.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_users")
public class TelegramUser {
    
    @Id
    private Long chatId;
    
    private String username;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private LocalDateTime registeredAt;
    private String state;
    private String role;
    
    // Конструкторы, геттеры и сеттеры
    
    public TelegramUser() {
    }
    
    public TelegramUser(Long chatId, String username, String firstName, String lastName) {
        this.chatId = chatId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.registeredAt = LocalDateTime.now();
        this.state = "NEW";
        this.role = "USER";
    }
    
    // Геттеры и сеттеры
    public Long getChatId() {
        return chatId;
    }
    
    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return lastName;
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }
    
    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }
    
    public String getState() {
        return state;
    }
    
    public void setState(String state) {
        this.state = state;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
} 