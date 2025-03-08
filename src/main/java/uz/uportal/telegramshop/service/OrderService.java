package uz.uportal.telegramshop.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uz.uportal.telegramshop.model.Order;
import uz.uportal.telegramshop.model.Product;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.OrderRepository;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    
    @Autowired
    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
    
    // Создание нового заказа
    public Order createOrder(TelegramUser user, Product product, Integer quantity, String address, String phoneNumber) {
        Order order = new Order(user, product, quantity, address, phoneNumber);
        return orderRepository.save(order);
    }
    
    // Получение заказа по ID
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }
    
    // Получение всех заказов пользователя
    public List<Order> getUserOrders(TelegramUser user) {
        return orderRepository.findByUserOrderByCreatedAtDesc(user);
    }
    
    // Получение всех заказов
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }
    
    // Получение заказов по статусу
    public List<Order> getOrdersByStatus(String status) {
        return orderRepository.findByStatus(status);
    }
    
    // Обновление статуса заказа
    public Order updateOrderStatus(Long orderId, String status) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            order.setStatus(status);
            return orderRepository.save(order);
        }
        return null;
    }
    
    // Добавление комментария к заказу
    public Order addComment(Long orderId, String comment) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            order.setComment(comment);
            order.setUpdatedAt(java.time.LocalDateTime.now());
            return orderRepository.save(order);
        }
        return null;
    }
    
    // Отмена заказа
    public Order cancelOrder(Long orderId) {
        return updateOrderStatus(orderId, "CANCELLED");
    }
} 