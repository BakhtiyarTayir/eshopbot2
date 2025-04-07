package uz.uportal.telegramshop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.uportal.telegramshop.model.ShopSettings;

public interface ShopSettingsRepository extends JpaRepository<ShopSettings, Long> {
    // Метод для получения первой записи настроек (обычно будет только одна запись)
    ShopSettings findFirstByOrderById();
} 