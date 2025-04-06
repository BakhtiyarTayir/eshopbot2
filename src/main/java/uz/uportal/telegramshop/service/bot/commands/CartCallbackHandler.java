package uz.uportal.telegramshop.service.bot.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import uz.uportal.telegramshop.model.CartItem;
import uz.uportal.telegramshop.model.Product;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.CartService;
import uz.uportal.telegramshop.service.ProductService;
import uz.uportal.telegramshop.service.bot.core.MessageSender;
import uz.uportal.telegramshop.service.bot.core.UpdateHandler;
import uz.uportal.telegramshop.service.bot.keyboards.KeyboardFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Обработчик callback-запросов, связанных с корзиной покупок
 */
@Component
public class CartCallbackHandler implements UpdateHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CartCallbackHandler.class);
    
    private final TelegramUserRepository telegramUserRepository;
    private final CartService cartService;
    private final ProductService productService;
    private final KeyboardFactory keyboardFactory;
    private final MessageSender messageSender;
    
    public CartCallbackHandler(
            TelegramUserRepository telegramUserRepository,
            CartService cartService,
            ProductService productService,
            KeyboardFactory keyboardFactory,
            MessageSender messageSender) {
        this.telegramUserRepository = telegramUserRepository;
        this.cartService = cartService;
        this.productService = productService;
        this.keyboardFactory = keyboardFactory;
        this.messageSender = messageSender;
    }
    
    @Override
    public boolean canHandle(Update update) {
        if (!update.hasCallbackQuery()) {
            return false;
        }
        
        String callbackData = update.getCallbackQuery().getData();
        return callbackData.startsWith("add_to_cart_") || 
               callbackData.equals("clear_cart") ||
               callbackData.equals("checkout") ||
               callbackData.startsWith("remove_from_cart_") ||
               callbackData.startsWith("update_quantity_") ||
               callbackData.equals("main_menu_cart");
    }
    
    @Override
    public BotApiMethod<?> handle(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        String callbackData = callbackQuery.getData();
        
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = null;
        
        try {
            MaybeInaccessibleMessage maybeMessage = callbackQuery.getMessage();
            
            // Проверяем, доступно ли сообщение
            if (maybeMessage instanceof Message) {
                Message message = (Message) maybeMessage;
                messageId = message.getMessageId();
            }
        } catch (Exception e) {
            logger.error("Error getting messageId from callback query", e);
        }
        
        logger.info("Handling cart callback: {} for chatId: {}", callbackData, chatId);
        
        try {
            if (callbackData.startsWith("add_to_cart_")) {
                return handleAddToCart(chatId, callbackData);
            } else if (callbackData.equals("clear_cart")) {
                return handleClearCart(chatId, messageId);
            } else if (callbackData.equals("checkout")) {
                return handleCheckout(chatId, messageId);
            } else if (callbackData.startsWith("remove_from_cart_")) {
                return handleRemoveFromCart(chatId, messageId, callbackData);
            } else if (callbackData.startsWith("update_quantity_")) {
                return handleUpdateQuantity(chatId, messageId, callbackData);
            } else if (callbackData.equals("main_menu_cart")) {
                return handleShowCart(chatId);
            }
        } catch (Exception e) {
            logger.error("Error handling cart callback: {}", e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Обрабатывает добавление товара в корзину
     * 
     * @param chatId ID чата
     * @param callbackData данные callback-запроса
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddToCart(Long chatId, String callbackData) {
        try {
            // Извлекаем ID товара из callback-данных
            String productIdStr = callbackData.replace("add_to_cart_", "");
            Long productId = Long.parseLong(productIdStr);
            
            // Получаем пользователя
            Optional<TelegramUser> userOpt = telegramUserRepository.findById(chatId);
            if (userOpt.isEmpty()) {
                logger.warn("User with chatId {} not found", chatId);
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, попробуйте позже.");
            }
            
            TelegramUser user = userOpt.get();
            
            // Добавляем товар в корзину (по умолчанию 1 шт)
            boolean success = cartService.addToCart(user, productId, 1);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            
            if (success) {
                // Получаем информацию о товаре
                Optional<Product> productOpt = productService.getProductById(productId);
                if (productOpt.isEmpty()) {
                    return createTextMessage(chatId, "Товар успешно добавлен в корзину.");
                }
                
                Product product = productOpt.get();
                message.setText("✅ Товар \"" + product.getName() + "\" добавлен в корзину.\n\n" +
                                "Хотите перейти в корзину или продолжить покупки?");
                
                // Создаем клавиатуру с кнопками "Перейти в корзину" и "Продолжить покупки"
                InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                
                List<InlineKeyboardButton> row1 = new ArrayList<>();
                InlineKeyboardButton cartButton = new InlineKeyboardButton();
                cartButton.setText("🛒 Перейти в корзину");
                cartButton.setCallbackData("main_menu_cart");
                row1.add(cartButton);
                keyboard.add(row1);
                
                List<InlineKeyboardButton> row2 = new ArrayList<>();
                InlineKeyboardButton continueButton = new InlineKeyboardButton();
                continueButton.setText("🔍 Продолжить покупки");
                continueButton.setCallbackData("catalog_categories");
                row2.add(continueButton);
                keyboard.add(row2);
                
                keyboardMarkup.setKeyboard(keyboard);
                message.setReplyMarkup(keyboardMarkup);
            } else {
                message.setText("❌ Не удалось добавить товар в корзину. Возможно, товара нет в наличии.");
            }
            
            return message;
        } catch (Exception e) {
            logger.error("Error adding product to cart: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при добавлении товара в корзину.");
        }
    }
    
    /**
     * Обрабатывает очистку корзины
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @return ответ бота
     */
    private BotApiMethod<?> handleClearCart(Long chatId, Integer messageId) {
        try {
            // Получаем пользователя
            Optional<TelegramUser> userOpt = telegramUserRepository.findById(chatId);
            if (userOpt.isEmpty()) {
                logger.warn("User with chatId {} not found", chatId);
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, попробуйте позже.");
            }
            
            TelegramUser user = userOpt.get();
            
            // Очищаем корзину
            boolean success = cartService.clearCart(user);
            
            if (messageId != null) {
                EditMessageText editMessage = new EditMessageText();
                editMessage.setChatId(chatId);
                editMessage.setMessageId(messageId);
                
                if (success) {
                    editMessage.setText("🛒 Ваша корзина очищена.");
                    
                    // Создаем клавиатуру с кнопкой "Вернуться в каталог"
                    InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                    
                    List<InlineKeyboardButton> row = new ArrayList<>();
                    InlineKeyboardButton backButton = new InlineKeyboardButton();
                    backButton.setText("⬅️ Вернуться в каталог");
                    backButton.setCallbackData("catalog_categories");
                    row.add(backButton);
                    keyboard.add(row);
                    
                    keyboardMarkup.setKeyboard(keyboard);
                    editMessage.setReplyMarkup(keyboardMarkup);
                } else {
                    editMessage.setText("❌ Не удалось очистить корзину. Пожалуйста, попробуйте позже.");
                }
                
                return editMessage;
            } else {
                SendMessage message = new SendMessage();
                message.setChatId(chatId);
                
                if (success) {
                    message.setText("🛒 Ваша корзина очищена.");
                    
                    // Создаем клавиатуру с кнопкой "Вернуться в каталог"
                    InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                    
                    List<InlineKeyboardButton> row = new ArrayList<>();
                    InlineKeyboardButton backButton = new InlineKeyboardButton();
                    backButton.setText("⬅️ Вернуться в каталог");
                    backButton.setCallbackData("catalog_categories");
                    row.add(backButton);
                    keyboard.add(row);
                    
                    keyboardMarkup.setKeyboard(keyboard);
                    message.setReplyMarkup(keyboardMarkup);
                } else {
                    message.setText("❌ Не удалось очистить корзину. Пожалуйста, попробуйте позже.");
                }
                
                return message;
            }
        } catch (Exception e) {
            logger.error("Error clearing cart: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при очистке корзины.");
        }
    }
    
    /**
     * Обрабатывает оформление заказа
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @return ответ бота
     */
    private BotApiMethod<?> handleCheckout(Long chatId, Integer messageId) {
        try {
            // Получаем пользователя
            Optional<TelegramUser> userOpt = telegramUserRepository.findById(chatId);
            if (userOpt.isEmpty()) {
                logger.warn("User with chatId {} not found", chatId);
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, попробуйте позже.");
            }
            
            TelegramUser user = userOpt.get();
            
            // Проверяем, не пуста ли корзина
            if (cartService.isCartEmpty(user)) {
                return createTextMessage(chatId, "Ваша корзина пуста. Добавьте товары перед оформлением заказа.");
            }
            
            // Устанавливаем состояние пользователя для сбора адреса доставки
            user.setState("WAITING_FOR_ADDRESS");
            telegramUserRepository.save(user);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Для оформления заказа, пожалуйста, укажите адрес доставки:");
            
            return message;
        } catch (Exception e) {
            logger.error("Error starting checkout process: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при оформлении заказа.");
        }
    }
    
    /**
     * Обрабатывает удаление товара из корзины
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param callbackData данные callback-запроса
     * @return ответ бота
     */
    private BotApiMethod<?> handleRemoveFromCart(Long chatId, Integer messageId, String callbackData) {
        try {
            // Извлекаем ID товара из callback-данных
            String productIdStr = callbackData.replace("remove_from_cart_", "");
            Long productId = Long.parseLong(productIdStr);
            
            // Получаем пользователя
            Optional<TelegramUser> userOpt = telegramUserRepository.findById(chatId);
            if (userOpt.isEmpty()) {
                logger.warn("User with chatId {} not found", chatId);
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, попробуйте позже.");
            }
            
            TelegramUser user = userOpt.get();
            
            // Удаляем товар из корзины
            boolean success = cartService.removeFromCart(user, productId);
            
            // Получаем обновленную информацию о корзине
            String cartInfo = cartService.getCartInfo(chatId);
            
            if (messageId != null) {
                EditMessageText editMessage = new EditMessageText();
                editMessage.setChatId(chatId);
                editMessage.setMessageId(messageId);
                
                if (success) {
                    if (cartInfo.isEmpty()) {
                        editMessage.setText("Ваша корзина пуста.");
                        
                        // Создаем клавиатуру с кнопкой "Вернуться в каталог"
                        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                        
                        List<InlineKeyboardButton> row = new ArrayList<>();
                        InlineKeyboardButton backButton = new InlineKeyboardButton();
                        backButton.setText("⬅️ Вернуться в каталог");
                        backButton.setCallbackData("catalog_categories");
                        row.add(backButton);
                        keyboard.add(row);
                        
                        keyboardMarkup.setKeyboard(keyboard);
                        editMessage.setReplyMarkup(keyboardMarkup);
                    } else {
                        editMessage.setText("🛒 Ваша корзина:\n\n" + cartInfo);
                        editMessage.setReplyMarkup(keyboardFactory.createCartKeyboard());
                    }
                } else {
                    editMessage.setText("❌ Не удалось удалить товар из корзины. Пожалуйста, попробуйте позже.");
                }
                
                return editMessage;
            } else {
                SendMessage message = new SendMessage();
                message.setChatId(chatId);
                
                if (success) {
                    if (cartInfo.isEmpty()) {
                        message.setText("Ваша корзина пуста.");
                        
                        // Создаем клавиатуру с кнопкой "Вернуться в каталог"
                        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                        
                        List<InlineKeyboardButton> row = new ArrayList<>();
                        InlineKeyboardButton backButton = new InlineKeyboardButton();
                        backButton.setText("⬅️ Вернуться в каталог");
                        backButton.setCallbackData("catalog_categories");
                        row.add(backButton);
                        keyboard.add(row);
                        
                        keyboardMarkup.setKeyboard(keyboard);
                        message.setReplyMarkup(keyboardMarkup);
                    } else {
                        message.setText("🛒 Ваша корзина:\n\n" + cartInfo);
                        message.setReplyMarkup(keyboardFactory.createCartKeyboard());
                    }
                } else {
                    message.setText("❌ Не удалось удалить товар из корзины. Пожалуйста, попробуйте позже.");
                }
                
                return message;
            }
        } catch (Exception e) {
            logger.error("Error removing product from cart: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при удалении товара из корзины.");
        }
    }
    
    /**
     * Обрабатывает изменение количества товара в корзине
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param callbackData данные callback-запроса
     * @return ответ бота
     */
    private BotApiMethod<?> handleUpdateQuantity(Long chatId, Integer messageId, String callbackData) {
        try {
            // Извлекаем данные из callback (формат: update_quantity_productId_quantity)
            String[] parts = callbackData.split("_");
            if (parts.length != 4) {
                logger.warn("Invalid callback data format: {}", callbackData);
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, попробуйте позже.");
            }
            
            Long productId = Long.parseLong(parts[2]);
            Integer quantity = Integer.parseInt(parts[3]);
            
            // Получаем пользователя
            Optional<TelegramUser> userOpt = telegramUserRepository.findById(chatId);
            if (userOpt.isEmpty()) {
                logger.warn("User with chatId {} not found", chatId);
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, попробуйте позже.");
            }
            
            TelegramUser user = userOpt.get();
            
            // Обновляем количество товара в корзине
            boolean success = cartService.updateQuantity(user, productId, quantity);
            
            // Получаем обновленную информацию о корзине
            String cartInfo = cartService.getCartInfo(chatId);
            
            if (messageId != null) {
                EditMessageText editMessage = new EditMessageText();
                editMessage.setChatId(chatId);
                editMessage.setMessageId(messageId);
                
                if (success) {
                    editMessage.setText("🛒 Ваша корзина:\n\n" + cartInfo);
                    editMessage.setReplyMarkup(keyboardFactory.createCartKeyboard());
                } else {
                    editMessage.setText("❌ Не удалось обновить количество товара. Возможно, товара нет в наличии.");
                }
                
                return editMessage;
            } else {
                SendMessage message = new SendMessage();
                message.setChatId(chatId);
                
                if (success) {
                    message.setText("🛒 Ваша корзина:\n\n" + cartInfo);
                    message.setReplyMarkup(keyboardFactory.createCartKeyboard());
                } else {
                    message.setText("❌ Не удалось обновить количество товара. Возможно, товара нет в наличии.");
                }
                
                return message;
            }
        } catch (Exception e) {
            logger.error("Error updating product quantity: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при обновлении количества товара.");
        }
    }
    
    /**
     * Обрабатывает показ корзины
     * 
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleShowCart(Long chatId) {
        try {
            // Получаем пользователя
            Optional<TelegramUser> userOpt = telegramUserRepository.findById(chatId);
            if (userOpt.isEmpty()) {
                logger.warn("User with chatId {} not found", chatId);
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, попробуйте позже.");
            }
            
            TelegramUser user = userOpt.get();
            
            // Получаем информацию о корзине
            String cartInfo = cartService.getCartInfo(chatId);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            
            if (cartInfo.isEmpty()) {
                message.setText("Ваша корзина пуста. Добавьте товары из каталога.");
                
                // Создаем клавиатуру с кнопкой "Вернуться в каталог"
                InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton backButton = new InlineKeyboardButton();
                backButton.setText("⬅️ Вернуться в каталог");
                backButton.setCallbackData("catalog_categories");
                row.add(backButton);
                keyboard.add(row);
                
                keyboardMarkup.setKeyboard(keyboard);
                message.setReplyMarkup(keyboardMarkup);
            } else {
                message.setText("🛒 Ваша корзина:\n\n" + cartInfo);
                message.setReplyMarkup(keyboardFactory.createCartKeyboard());
            }
            
            return message;
        } catch (Exception e) {
            logger.error("Error showing cart: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при отображении корзины.");
        }
    }
    
    /**
     * Создает объект текстового сообщения
     * @param chatId ID чата
     * @param text текст сообщения
     * @return объект сообщения
     */
    private SendMessage createTextMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        return message;
    }
} 