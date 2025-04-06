package uz.uportal.telegramshop.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.uportal.telegramshop.model.Order;
import uz.uportal.telegramshop.model.OrderStatus;
import uz.uportal.telegramshop.model.TelegramUser;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser(TelegramUser user);
    Page<Order> findByUser(TelegramUser user, Pageable pageable);
    List<Order> findByStatus(OrderStatus status);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    List<Order> findByUserAndStatus(TelegramUser user, OrderStatus status);
    Page<Order> findByUserAndStatus(TelegramUser user, OrderStatus status, Pageable pageable);
} 