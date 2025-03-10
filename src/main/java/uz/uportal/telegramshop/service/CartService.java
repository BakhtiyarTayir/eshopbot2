package uz.uportal.telegramshop.service;

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
     * Добавить товар в корзину
     * @param user пользователь
     * @param productId ID товара
     * @param quantity количество
     * @return true, если товар успешно добавлен
     */
    @Transactional
    public boolean addToCart(TelegramUser user, Long productId, Integer quantity) {
        if (quantity <= 0) {
            return false;
        }
        
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            return false;
        }
        
        Product product = productOpt.get();
        
        // Проверяем, есть ли товар в наличии
        if (product.getStock() < quantity) {
            return false;
        }
        
        // Проверяем, есть ли уже такой товар в корзине
        Optional<CartItem> existingItemOpt = cartItemRepository.findByUserAndProduct(user, product);
        
        if (existingItemOpt.isPresent()) {
            // Если товар уже есть в корзине, увеличиваем количество
            CartItem existingItem = existingItemOpt.get();
            int newQuantity = existingItem.getQuantity() + quantity;
            
            // Проверяем, не превышает ли новое количество доступное количество товара
            if (newQuantity > product.getStock()) {
                return false;
            }
            
            existingItem.setQuantity(newQuantity);
            cartItemRepository.save(existingItem);
        } else {
            // Если товара нет в корзине, создаем новый элемент
            CartItem newItem = new CartItem(user, product, quantity);
            cartItemRepository.save(newItem);
        }
        
        return true;
    }
    
    /**
     * Обновить количество товара в корзине
     * @param user пользователь
     * @param cartItemId ID элемента корзины
     * @param quantity новое количество
     * @return true, если количество успешно обновлено
     */
    @Transactional
    public boolean updateCartItemQuantity(TelegramUser user, Long cartItemId, Integer quantity) {
        if (quantity <= 0) {
            // Если количество <= 0, удаляем товар из корзины
            return removeFromCart(user, cartItemId);
        }
        
        Optional<CartItem> cartItemOpt = cartItemRepository.findById(cartItemId);
        if (cartItemOpt.isEmpty() || !cartItemOpt.get().getUser().getChatId().equals(user.getChatId())) {
            return false;
        }
        
        CartItem cartItem = cartItemOpt.get();
        Product product = cartItem.getProduct();
        
        // Проверяем, есть ли товар в наличии
        if (product.getStock() < quantity) {
            return false;
        }
        
        cartItem.setQuantity(quantity);
        cartItemRepository.save(cartItem);
        return true;
    }
    
    /**
     * Удалить товар из корзины
     * @param user пользователь
     * @param cartItemId ID элемента корзины
     * @return true, если товар успешно удален
     */
    @Transactional
    public boolean removeFromCart(TelegramUser user, Long cartItemId) {
        Optional<CartItem> cartItemOpt = cartItemRepository.findById(cartItemId);
        if (cartItemOpt.isEmpty() || !cartItemOpt.get().getUser().getChatId().equals(user.getChatId())) {
            return false;
        }
        
        cartItemRepository.delete(cartItemOpt.get());
        return true;
    }
    
    /**
     * Очистить корзину пользователя
     * @param user пользователь
     */
    @Transactional
    public void clearCart(TelegramUser user) {
        cartItemRepository.deleteByUser(user);
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
     * Получить общую стоимость корзины
     * @param user пользователь
     * @return общая стоимость
     */
    public BigDecimal getCartTotal(TelegramUser user) {
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
        
        cartInfo.append("\nИтого: ").append(getCartTotal(user)).append(" руб.");
        
        return cartInfo.toString();
    }
} 