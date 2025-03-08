package uz.uportal.telegramshop.service.bot.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.uportal.telegramshop.model.CartItem;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.service.CartService;
import uz.uportal.telegramshop.repository.TelegramUserRepository;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команд корзины
 */
@Component
public class CartHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CartHandler.class);
    
    private final CartService cartService;
    private final OrderHandler orderHandler;
    private final TelegramUserRepository userRepository;
    
    public CartHandler(CartService cartService, OrderHandler orderHandler, TelegramUserRepository userRepository) {
        this.cartService = cartService;
        this.orderHandler = orderHandler;
        this.userRepository = userRepository;
    }
    
    /**
     * Показать корзину пользователя
     * @param chatId ID чата
     * @return сообщение с содержимым корзины
     */
    public SendMessage showCart(Long chatId, TelegramUser user) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        List<CartItem> cartItems = cartService.getCartItems(user);
        
        if (cartItems.isEmpty()) {
            message.setText("Ваша корзина пуста. Добавьте товары из каталога.");
            
            // Добавляем кнопку для перехода в каталог
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            
            List<InlineKeyboardButton> catalogRow = new ArrayList<>();
            InlineKeyboardButton catalogButton = new InlineKeyboardButton();
            catalogButton.setText("Перейти в каталог");
            catalogButton.setCallbackData("catalog");
            catalogRow.add(catalogButton);
            rowsInline.add(catalogRow);
            
            markupInline.setKeyboard(rowsInline);
            message.setReplyMarkup(markupInline);
            
            return message;
        }
        
        // Формируем сообщение с содержимым корзины
        StringBuilder cartText = new StringBuilder("🛒 *Ваша корзина:*\n\n");
        DecimalFormat df = new DecimalFormat("#,##0.00");
        
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem item = cartItems.get(i);
            BigDecimal totalPrice = item.getTotalPrice();
            
            cartText.append(i + 1).append(". *").append(item.getProduct().getName()).append("*\n");
            cartText.append("   Цена: ").append(df.format(item.getProduct().getPrice())).append(" $\n");
            cartText.append("   Количество: ").append(item.getQuantity()).append("\n");
            cartText.append("   Сумма: ").append(df.format(totalPrice)).append(" $\n\n");
        }
        
        // Добавляем общую сумму
        BigDecimal cartTotal = cartService.getCartTotal(user);
        cartText.append("*Общая сумма:* ").append(df.format(cartTotal)).append(" $");
        
        // Добавляем текущее время в миллисекундах, чтобы пользователь видел, что сообщение обновилось
        // Это будет скрыто от пользователя, так как находится в конце сообщения и не видно
        cartText.append("\n\n_").append(System.currentTimeMillis()).append("_");
        
        message.setText(cartText.toString());
        message.setParseMode("Markdown");
        
        // Добавляем кнопки управления корзиной
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        
        // Кнопки для каждого товара (изменить количество, удалить)
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem item = cartItems.get(i);
            
            // Кнопки изменения количества
            List<InlineKeyboardButton> quantityRow = new ArrayList<>();
            
            // Кнопка уменьшения количества
            InlineKeyboardButton minusButton = new InlineKeyboardButton();
            minusButton.setText("➖");
            minusButton.setCallbackData("cart_minus:" + item.getId());
            quantityRow.add(minusButton);
            
            // Кнопка с текущим количеством
            InlineKeyboardButton quantityButton = new InlineKeyboardButton();
            quantityButton.setText(item.getQuantity().toString());
            quantityButton.setCallbackData("cart_info:" + item.getId());
            quantityRow.add(quantityButton);
            
            // Кнопка увеличения количества
            InlineKeyboardButton plusButton = new InlineKeyboardButton();
            plusButton.setText("➕");
            plusButton.setCallbackData("cart_plus:" + item.getId());
            quantityRow.add(plusButton);
            
            // Кнопка удаления товара
            InlineKeyboardButton deleteButton = new InlineKeyboardButton();
            deleteButton.setText("🗑️");
            deleteButton.setCallbackData("cart_remove:" + item.getId());
            quantityRow.add(deleteButton);
            
            rowsInline.add(quantityRow);
        }
        
        // Кнопки действий с корзиной
        List<InlineKeyboardButton> actionsRow = new ArrayList<>();
        
        // Кнопка очистки корзины
        InlineKeyboardButton clearButton = new InlineKeyboardButton();
        clearButton.setText("Очистить корзину");
        clearButton.setCallbackData("cart_clear");
        actionsRow.add(clearButton);
        
        // Кнопка оформления заказа
        InlineKeyboardButton checkoutButton = new InlineKeyboardButton();
        checkoutButton.setText("Оформить заказ");
        checkoutButton.setCallbackData("cart_checkout");
        actionsRow.add(checkoutButton);
        
        rowsInline.add(actionsRow);
        
        // Кнопка возврата в каталог
        List<InlineKeyboardButton> catalogRow = new ArrayList<>();
        InlineKeyboardButton catalogButton = new InlineKeyboardButton();
        catalogButton.setText("Вернуться в каталог");
        catalogButton.setCallbackData("catalog");
        catalogRow.add(catalogButton);
        
        rowsInline.add(catalogRow);
        
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);
        
        return message;
    }
    
    /**
     * Обработать добавление товара в корзину
     * @param chatId ID чата
     * @param user пользователь
     * @param productId ID товара
     * @return сообщение с результатом
     */
    public SendMessage handleAddToCart(Long chatId, TelegramUser user, Long productId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        boolean success = cartService.addToCart(user, productId, 1);
        
        if (success) {
            message.setText("Товар успешно добавлен в корзину!");
            
            // Добавляем кнопки для перехода в корзину или продолжения покупок
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            
            // Кнопка перехода в корзину
            InlineKeyboardButton cartButton = new InlineKeyboardButton();
            cartButton.setText("Перейти в корзину");
            cartButton.setCallbackData("cart");
            row.add(cartButton);
            
            // Кнопка продолжения покупок
            InlineKeyboardButton continueButton = new InlineKeyboardButton();
            continueButton.setText("Продолжить покупки");
            continueButton.setCallbackData("catalog");
            row.add(continueButton);
            
            rowsInline.add(row);
            markupInline.setKeyboard(rowsInline);
            message.setReplyMarkup(markupInline);
        } else {
            message.setText("Не удалось добавить товар в корзину. Возможно, товар закончился или не существует.");
        }
        
        return message;
    }
    
    /**
     * Обработать изменение количества товара в корзине
     * @param chatId ID чата
     * @param user пользователь
     * @param cartItemId ID элемента корзины
     * @param delta изменение количества (может быть положительным или отрицательным)
     * @return сообщение с обновленной корзиной
     */
    public SendMessage handleUpdateCartItemQuantity(Long chatId, TelegramUser user, Long cartItemId, int delta) {
        List<CartItem> cartItems = cartService.getCartItems(user);
        
        // Находим элемент корзины
        CartItem targetItem = null;
        for (CartItem item : cartItems) {
            if (item.getId().equals(cartItemId)) {
                targetItem = item;
                break;
            }
        }
        
        if (targetItem != null) {
            int newQuantity = targetItem.getQuantity() + delta;
            cartService.updateCartItemQuantity(user, cartItemId, newQuantity);
        }
        
        // Возвращаем обновленную корзину
        return showCart(chatId, user);
    }
    
    /**
     * Обработать удаление товара из корзины
     * @param chatId ID чата
     * @param user пользователь
     * @param cartItemId ID элемента корзины
     * @return сообщение с обновленной корзиной
     */
    public SendMessage handleRemoveFromCart(Long chatId, TelegramUser user, Long cartItemId) {
        cartService.removeFromCart(user, cartItemId);
        
        // Возвращаем обновленную корзину
        return showCart(chatId, user);
    }
    
    /**
     * Обработать очистку корзины
     * @param chatId ID чата
     * @param user пользователь
     * @return сообщение с результатом
     */
    public SendMessage handleClearCart(Long chatId, TelegramUser user) {
        cartService.clearCart(user);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Корзина очищена.\n\n_" + System.currentTimeMillis() + "_");
        message.setParseMode("Markdown");
        
        // Добавляем кнопку для перехода в каталог
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        
        List<InlineKeyboardButton> catalogRow = new ArrayList<>();
        InlineKeyboardButton catalogButton = new InlineKeyboardButton();
        catalogButton.setText("Перейти в каталог");
        catalogButton.setCallbackData("catalog");
        catalogRow.add(catalogButton);
        rowsInline.add(catalogRow);
        
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);
        
        return message;
    }
    
    /**
     * Обработать оформление заказа из корзины
     * @param chatId ID чата
     * @param user пользователь
     * @return сообщение с результатом
     */
    public SendMessage handleCartCheckout(Long chatId, TelegramUser user) {
        logger.info("Handling cart checkout for user {}", chatId);
        
        if (cartService.isCartEmpty(user)) {
            logger.info("Cart is empty for user {}", chatId);
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Ваша корзина пуста. Добавьте товары из каталога.");
            return message;
        }
        
        // Получаем товары из корзины
        List<CartItem> cartItems = cartService.getCartItems(user);
        logger.info("Cart items for user {}: {}", chatId, cartItems.size());
        
        // Создаем данные заказа
        Map<String, String> orderData = new HashMap<>();
        orderData.put("fromCart", "true");
        
        // Сохраняем данные заказа
        orderHandler.saveOrderData(chatId, orderData);
        logger.info("Saved order data for user {}: {}", chatId, orderData);
        
        // Если у пользователя уже есть номер телефона, пропускаем этот шаг и переходим к адресу
        if (user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty()) {
            logger.info("User {} already has phone number, skipping to address", chatId);
            
            // Сохраняем номер телефона в данных заказа
            orderData.put("phone", user.getPhoneNumber());
            
            // Устанавливаем состояние для запроса адреса
            user.setState("ORDERING_ADDRESS");
            userRepository.save(user);
            logger.info("Set state to ORDERING_ADDRESS for user {}", chatId);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Для оформления заказа, пожалуйста, введите адрес доставки:");
            
            // Добавляем кнопку отмены
            ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
            List<KeyboardRow> keyboard = new ArrayList<>();
            KeyboardRow cancelRow = new KeyboardRow();
            cancelRow.add(new KeyboardButton("Отмена"));
            keyboard.add(cancelRow);
            keyboardMarkup.setKeyboard(keyboard);
            keyboardMarkup.setResizeKeyboard(true);
            keyboardMarkup.setOneTimeKeyboard(true);
            message.setReplyMarkup(keyboardMarkup);
            
            return message;
        } else {
            logger.info("Requesting phone number from user {}", chatId);
            
            // Устанавливаем состояние для запроса номера телефона
            user.setState("ORDERING_PHONE");
            userRepository.save(user);
            logger.info("Set state to ORDERING_PHONE for user {}", chatId);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Для оформления заказа, пожалуйста, введите ваш номер телефона:");
            
            // Добавляем кнопку для отправки номера телефона
            ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
            List<KeyboardRow> keyboard = new ArrayList<>();
            
            // Кнопка для отправки контакта
            KeyboardRow row = new KeyboardRow();
            KeyboardButton button = new KeyboardButton("Отправить номер телефона");
            button.setRequestContact(true);
            row.add(button);
            keyboard.add(row);
            
            // Кнопка отмены
            KeyboardRow cancelRow = new KeyboardRow();
            cancelRow.add(new KeyboardButton("Отмена"));
            keyboard.add(cancelRow);
            
            keyboardMarkup.setKeyboard(keyboard);
            keyboardMarkup.setResizeKeyboard(true);
            keyboardMarkup.setOneTimeKeyboard(true);
            message.setReplyMarkup(keyboardMarkup);
            
            return message;
        }
    }
    
    /**
     * Обработать прямую покупку товара (без добавления в корзину)
     * @param chatId ID чата
     * @param user пользователь
     * @param productId ID товара
     * @return сообщение с запросом данных для оформления заказа
     */
    public SendMessage handleDirectPurchase(Long chatId, TelegramUser user, Long productId) {
        logger.info("Handling direct purchase for user {} with product ID: {}", chatId, productId);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        // Сохраняем ID товара в данных заказа
        Map<String, String> orderData = new HashMap<>();
        orderData.put("productId", productId.toString());
        orderData.put("quantity", "1");
        
        // Сохраняем данные заказа
        orderHandler.saveOrderData(chatId, orderData);
        logger.info("Saved order data for user {}: {}", chatId, orderData);
        
        // Если у пользователя уже есть номер телефона, пропускаем этот шаг
        if (user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty()) {
            logger.info("User {} already has phone number, skipping to address", chatId);
            
            // Сохраняем номер телефона в данных заказа
            orderData.put("phone", user.getPhoneNumber());
            
            // Устанавливаем состояние для запроса адреса
            user.setState("ORDERING_ADDRESS");
            userRepository.save(user);
            logger.info("Set state to ORDERING_ADDRESS for user {}", chatId);
            
            message.setText("Для оформления заказа, пожалуйста, введите адрес доставки:");
            
            // Добавляем кнопку отмены
            ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
            List<KeyboardRow> keyboard = new ArrayList<>();
            KeyboardRow cancelRow = new KeyboardRow();
            cancelRow.add(new KeyboardButton("Отмена"));
            keyboard.add(cancelRow);
            keyboardMarkup.setKeyboard(keyboard);
            keyboardMarkup.setResizeKeyboard(true);
            keyboardMarkup.setOneTimeKeyboard(true);
            message.setReplyMarkup(keyboardMarkup);
        } else {
            logger.info("Requesting phone number from user {}", chatId);
            
            // Устанавливаем состояние для запроса номера телефона
            user.setState("ORDERING_PHONE");
            userRepository.save(user);
            logger.info("Set state to ORDERING_PHONE for user {}", chatId);
            
            message.setText("Для оформления заказа, пожалуйста, введите ваш номер телефона:");
            
            // Добавляем кнопку для отправки номера телефона
            ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
            List<KeyboardRow> keyboard = new ArrayList<>();
            
            // Кнопка для отправки контакта
            KeyboardRow row = new KeyboardRow();
            KeyboardButton button = new KeyboardButton("Отправить номер телефона");
            button.setRequestContact(true);
            row.add(button);
            keyboard.add(row);
            
            // Кнопка отмены
            KeyboardRow cancelRow = new KeyboardRow();
            cancelRow.add(new KeyboardButton("Отмена"));
            keyboard.add(cancelRow);
            
            keyboardMarkup.setKeyboard(keyboard);
            keyboardMarkup.setResizeKeyboard(true);
            keyboardMarkup.setOneTimeKeyboard(true);
            message.setReplyMarkup(keyboardMarkup);
        }
        
        return message;
    }
} 