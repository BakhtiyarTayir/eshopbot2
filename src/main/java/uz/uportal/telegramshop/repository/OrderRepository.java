package uz.uportal.telegramshop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.uportal.telegramshop.model.Order;
import uz.uportal.telegramshop.model.TelegramUser;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser(TelegramUser user);
    List<Order> findByUserOrderByCreatedAtDesc(TelegramUser user);
    List<Order> findByStatus(String status);
    List<Order> findAllByOrderByCreatedAtDesc();
} 