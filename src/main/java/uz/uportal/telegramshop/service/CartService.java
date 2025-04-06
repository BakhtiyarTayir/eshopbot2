package uz.uportal.telegramshop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.uportal.telegramshop.model.CartItem;
import uz.uportal.telegramshop.model.Product;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.CartItemRepository;
import uz.uportal.telegramshop.repository.ProductRepository;
import uz.uportal.telegramshop.repository.TelegramUserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с корзиной
 */
@Service
public class CartService {
    
    private static final Logger logger = LoggerFactory.getLogger(CartService.class);
    
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final TelegramUserRepository telegramUserRepository;
    
    public CartService(
            CartItemRepository cartItemRepository, 
            ProductRepository productRepository,
            TelegramUserRepository telegramUserRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.telegramUserRepository = telegramUserRepository;
    }
    
    /**
     * Получить все элементы корзины пользователя
     * @param user пользователь
     * @return список элементов корзины
     */
    public List<CartItem> getCartItems(TelegramUser user) {
        return cartItemRepository.findByUser(user);
    }
    
    /**
     * Добавить товар в корзину
     * @param user пользователь
     * @param productId ID товара
     * @param quantity количество
     * @return true, если товар успешно добавлен
     */
    @Transactional
    public boolean addToCart(TelegramUser user, Long productId, Integer quantity) {
        try {
            // Проверяем, существует ли товар
            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                logger.warn("Товар с ID {} не найден", productId);
                return false;
            }
            
            Product product = productOpt.get();
            
            // Проверяем, есть ли товар в наличии
            if (product.getStock() < quantity) {
                logger.warn("Недостаточно товара в наличии. Запрошено: {}, в наличии: {}", quantity, product.getStock());
                return false;
            }
            
            // Проверяем, есть ли уже такой товар в корзине
            Optional<CartItem> existingItemOpt = cartItemRepository.findByUserAndProduct(user, product);
            
            if (existingItemOpt.isPresent()) {
                // Если товар уже есть в корзине, увеличиваем количество
                CartItem existingItem = existingItemOpt.get();
                int newQuantity = existingItem.getQuantity() + quantity;
                
                // Проверяем, есть ли товар в наличии с учетом нового количества
                if (product.getStock() < newQuantity) {
                    logger.warn("Недостаточно товара в наличии. Запрошено: {}, в наличии: {}", newQuantity, product.getStock());
                    return false;
                }
                
                existingItem.setQuantity(newQuantity);
                cartItemRepository.save(existingItem);
            } else {
                // Если товара нет в корзине, создаем новый элемент
                CartItem cartItem = new CartItem(user, product, quantity);
                cartItemRepository.save(cartItem);
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Ошибка при добавлении товара в корзину: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Удалить товар из корзины
     * @param user пользователь
     * @param productId ID товара
     * @return true, если товар успешно удален
     */
    @Transactional
    public boolean removeFromCart(TelegramUser user, Long productId) {
        try {
            // Проверяем, существует ли товар
            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                logger.warn("Товар с ID {} не найден", productId);
                return false;
            }
            
            Product product = productOpt.get();
            
            // Удаляем товар из корзины
            cartItemRepository.deleteByUserAndProduct(user, product);
            
            return true;
        } catch (Exception e) {
            logger.error("Ошибка при удалении товара из корзины: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Изменить количество товара в корзине
     * @param user пользователь
     * @param productId ID товара
     * @param quantity новое количество
     * @return true, если количество успешно изменено
     */
    @Transactional
    public boolean updateQuantity(TelegramUser user, Long productId, Integer quantity) {
        try {
            // Проверяем, существует ли товар
            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                logger.warn("Товар с ID {} не найден", productId);
                return false;
            }
            
            Product product = productOpt.get();
            
            // Проверяем, есть ли товар в наличии
            if (product.getStock() < quantity) {
                logger.warn("Недостаточно товара в наличии. Запрошено: {}, в наличии: {}", quantity, product.getStock());
                return false;
            }
            
            // Проверяем, есть ли товар в корзине
            Optional<CartItem> cartItemOpt = cartItemRepository.findByUserAndProduct(user, product);
            if (cartItemOpt.isEmpty()) {
                logger.warn("Товар с ID {} не найден в корзине пользователя {}", productId, user.getChatId());
                return false;
            }
            
            CartItem cartItem = cartItemOpt.get();
            
            if (quantity <= 0) {
                // Если количество <= 0, удаляем товар из корзины
                cartItemRepository.delete(cartItem);
            } else {
                // Иначе обновляем количество
                cartItem.setQuantity(quantity);
                cartItemRepository.save(cartItem);
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Ошибка при изменении количества товара в корзине: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Очистить корзину пользователя
     * @param user пользователь
     * @return true, если корзина успешно очищена
     */
    @Transactional
    public boolean clearCart(TelegramUser user) {
        try {
            cartItemRepository.deleteByUser(user);
            return true;
        } catch (Exception e) {
            logger.error("Ошибка при очистке корзины: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Получить общую стоимость корзины
     * @param user пользователь
     * @return общая стоимость корзины
     */
    public BigDecimal getTotalPrice(TelegramUser user) {
        List<CartItem> cartItems = getCartItems(user);
        return cartItems.stream()
            .map(CartItem::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Проверить, пуста ли корзина
     * @param user пользователь
     * @return true, если корзина пуста
     */
    public boolean isCartEmpty(TelegramUser user) {
        return getCartItems(user).isEmpty();
    }
    
    /**
     * Получить информацию о корзине пользователя в виде текста
     * @param chatId ID чата пользователя
     * @return текстовая информация о корзине
     */
    public String getCartInfo(Long chatId) {
        Optional<TelegramUser> userOpt = telegramUserRepository.findById(chatId);
        if (userOpt.isEmpty()) {
            return "";
        }
        
        TelegramUser user = userOpt.get();
        List<CartItem> cartItems = getCartItems(user);
        
        if (cartItems.isEmpty()) {
            return "";
        }
        
        StringBuilder cartInfo = new StringBuilder();
        int index = 1;
        
        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            cartInfo.append(index).append(". ")
                   .append(product.getName())
                   .append(" - ")
                   .append(item.getQuantity())
                   .append(" шт. x ")
                   .append(product.getPrice())
                   .append(" = ")
                   .append(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                   .append(" руб.\n");
            index++;
        }
        
        cartInfo.append("\nИтого: ").append(getTotalPrice(user)).append(" руб.");
        
        return cartInfo.toString();
    }
} 