package uz.uportal.telegramshop.model;

/**
 * Перечисление статусов заказа
 */
public enum OrderStatus {
    NEW("Новый", "🆕"),
    PROCESSING("В обработке", "⏳"),
    COMPLETED("Выполнен", "✅"),
    CANCELLED("Отменен", "❌");
    
    private final String displayName;
    private final String emoji;
    
    OrderStatus(String displayName, String emoji) {
        this.displayName = displayName;
        this.emoji = emoji;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getEmoji() {
        return emoji;
    }
    
    public String getDisplayText() {
        return emoji + " " + displayName;
    }
} 