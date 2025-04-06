package uz.uportal.telegramshop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.uportal.telegramshop.model.*;
import uz.uportal.telegramshop.repository.OrderItemRepository;
import uz.uportal.telegramshop.repository.OrderRepository;
import uz.uportal.telegramshop.repository.ProductRepository;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с заказами
 */
@Service
public class OrderService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;
    
    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            CartService cartService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.cartService = cartService;
    }
    
    /**
     * Получить заказ по ID
     * @param id ID заказа
     * @return заказ или пустой Optional, если заказ не найден
     */
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }
    
    /**
     * Получить все заказы пользователя
     * @param user пользователь
     * @return список заказов
     */
    public List<Order> getOrdersByUser(TelegramUser user) {
        return orderRepository.findByUser(user);
    }
    
    /**
     * Получить все заказы пользователя с пагинацией
     * @param user пользователь
     * @param pageable параметры пагинации
     * @return страница заказов
     */
    public Page<Order> getOrdersByUser(TelegramUser user, Pageable pageable) {
        return orderRepository.findByUser(user, pageable);
    }
    
    /**
     * Получить все заказы по статусу
     * @param status статус заказа
     * @return список заказов
     */
    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }
    
    /**
     * Получить все заказы по статусу с пагинацией
     * @param status статус заказа
     * @param pageable параметры пагинации
     * @return страница заказов
     */
    public Page<Order> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatus(status, pageable);
    }
    
    /**
     * Получить все заказы с пагинацией
     * @param pageable параметры пагинации
     * @return страница заказов
     */
    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }
    
    /**
     * Создать заказ из корзины пользователя
     * @param user пользователь
     * @param address адрес доставки
     * @param phoneNumber номер телефона
     * @param comment комментарий к заказу
     * @return созданный заказ или null, если произошла ошибка
     */
    @Transactional
    public Order createOrderFromCart(TelegramUser user, String address, String phoneNumber, String comment) {
        try {
            // Получаем элементы корзины
            List<CartItem> cartItems = cartService.getCartItems(user);
            
            // Проверяем, не пуста ли корзина
            if (cartItems.isEmpty()) {
                logger.warn("Попытка создать заказ с пустой корзиной для пользователя {}", user.getChatId());
                return null;
            }
            
            // Создаем новый заказ
            Order order = new Order(user);
            order.setAddress(address);
            order.setPhoneNumber(phoneNumber);
            order.setComment(comment);
            
            // Сохраняем заказ
            order = orderRepository.save(order);
            
            // Добавляем элементы заказа
            for (CartItem cartItem : cartItems) {
                Product product = cartItem.getProduct();
                
                // Проверяем, есть ли товар в наличии
                if (product.getStock() < cartItem.getQuantity()) {
                    logger.warn("Недостаточно товара {} в наличии. Запрошено: {}, в наличии: {}", 
                        product.getId(), cartItem.getQuantity(), product.getStock());
                    throw new RuntimeException("Недостаточно товара в наличии");
                }
                
                // Создаем элемент заказа
                OrderItem orderItem = new OrderItem(cartItem);
                orderItem.setOrder(order);
                orderItemRepository.save(orderItem);
                
                // Уменьшаем количество товара в наличии
                product.setStock(product.getStock() - cartItem.getQuantity());
                productRepository.save(product);
            }
            
            // Пересчитываем общую сумму заказа
            order.recalculateTotalAmount();
            orderRepository.save(order);
            
            // Очищаем корзину
            cartService.clearCart(user);
            
            return order;
        } catch (Exception e) {
            logger.error("Ошибка при создании заказа: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Обновить статус заказа
     * @param orderId ID заказа
     * @param status новый статус
     * @return обновленный заказ или null, если заказ не найден
     */
    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus status) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                logger.warn("Заказ с ID {} не найден", orderId);
                return null;
            }
            
            Order order = orderOpt.get();
            order.setStatus(status);
            
            return orderRepository.save(order);
        } catch (Exception e) {
            logger.error("Ошибка при обновлении статуса заказа: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Отменить заказ
     * @param orderId ID заказа
     * @return true, если заказ успешно отменен
     */
    @Transactional
    public boolean cancelOrder(Long orderId) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                logger.warn("Заказ с ID {} не найден", orderId);
                return false;
            }
            
            Order order = orderOpt.get();
            
            // Проверяем, можно ли отменить заказ
            if (order.getStatus() == OrderStatus.COMPLETED) {
                logger.warn("Невозможно отменить выполненный заказ с ID {}", orderId);
                return false;
            }
            
            // Возвращаем товары на склад
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                if (product != null) {
                    product.setStock(product.getStock() + item.getQuantity());
                    productRepository.save(product);
                }
            }
            
            // Обновляем статус заказа
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            
            return true;
        } catch (Exception e) {
            logger.error("Ошибка при отмене заказа: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Удалить заказ
     * @param orderId ID заказа
     * @return true, если заказ успешно удален
     */
    @Transactional
    public boolean deleteOrder(Long orderId) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                logger.warn("Заказ с ID {} не найден", orderId);
                return false;
            }
            
            Order order = orderOpt.get();
            
            // Удаляем элементы заказа
            orderItemRepository.deleteByOrder(order);
            
            // Удаляем заказ
            orderRepository.delete(order);
            
            return true;
        } catch (Exception e) {
            logger.error("Ошибка при удалении заказа: {}", e.getMessage(), e);
            return false;
        }
    }
} 