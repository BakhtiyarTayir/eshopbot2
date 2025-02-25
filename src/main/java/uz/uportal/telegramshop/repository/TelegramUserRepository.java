package uz.uportal.telegramshop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.uportal.telegramshop.model.TelegramUser;

public interface TelegramUserRepository extends JpaRepository<TelegramUser, Long> {
    // Здесь можно добавить дополнительные методы запросов
} 