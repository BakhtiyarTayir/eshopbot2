package uz.uportal.telegramshop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.uportal.telegramshop.model.TelegramUser;
import java.util.List;

public interface TelegramUserRepository extends JpaRepository<TelegramUser, Long> {
    // Здесь можно добавить дополнительные методы запросов
    List<TelegramUser> findByRole(String role);
} 