package uz.uportal.telegramshop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.uportal.telegramshop.model.Order;
import uz.uportal.telegramshop.model.OrderItem;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    
    List<OrderItem> findByOrder(Order order);
    
    void deleteByOrder(Order order);
} 