package uz.uportal.telegramshop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.uportal.telegramshop.model.CartItem;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.model.Product;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с элементами корзины
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    
    /**
     * Найти все элементы корзины пользователя
     * @param user пользователь
     * @return список элементов корзины
     */
    List<CartItem> findByUser(TelegramUser user);
    
    /**
     * Найти элемент корзины по пользователю и товару
     * @param user пользователь
     * @param product товар
     * @return элемент корзины (если есть)
     */
    Optional<CartItem> findByUserAndProduct(TelegramUser user, Product product);
    
    /**
     * Удалить все элементы корзины пользователя
     * @param user пользователь
     */
    void deleteByUser(TelegramUser user);
} 