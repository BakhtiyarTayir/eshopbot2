package uz.uportal.telegramshop.service.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.uportal.telegramshop.model.Order;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.bot.handler.CommandHandler;
import uz.uportal.telegramshop.service.bot.handler.OrderHandler;
import uz.uportal.telegramshop.service.bot.handler.StateHandler;
import uz.uportal.telegramshop.service.bot.handler.CatalogHandler;
import uz.uportal.telegramshop.service.CategoryService;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.service.bot.handler.CartHandler;
import uz.uportal.telegramshop.model.CartItem;
import uz.uportal.telegramshop.service.CartService;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

/**
 * Основной класс Telegram бота
 */
@Service("newTelegramBotService")
public class TelegramBotService extends AbstractTelegramBot {
    
    private final TelegramUserRepository userRepository;
    private final CommandHandler commandHandler;
    private final StateHandler stateHandler;
    private final OrderHandler orderHandler;
    private final CatalogHandler catalogHandler;
    private final CategoryService categoryService;
    private final CartHandler cartHandler;
    private final CartService cartService;
    
    public TelegramBotService(
            TelegramUserRepository userRepository,
            CommandHandler commandHandler,
            StateHandler stateHandler,
            OrderHandler orderHandler,
            CatalogHandler catalogHandler,
            CategoryService categoryService,
            CartHandler cartHandler,
            CartService cartService,
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${telegram.bot.webhook-path}") String botPath) {
        super(botToken, botUsername, botPath);
        this.userRepository = userRepository;
        this.commandHandler = commandHandler;
        this.stateHandler = stateHandler;
        this.orderHandler = orderHandler;
        this.catalogHandler = catalogHandler;
        this.categoryService = categoryService;
        this.cartHandler = cartHandler;
        this.cartService = cartService;
    }
    
    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                return handleCallbackQuery(update);
            }
            
            if (update.hasMessage()) {
                return handleMessage(update);
            }
            
            return null;
        } catch (Exception e) {
            logger.error("Error processing update", e);
            return null;
        }
    }
    
    /**
     * Обработка сообщений
     * @param update обновление
     * @return ответ бота
     */
    private BotApiMethod<?> handleMessage(Update update) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        
        // Сохраняем пользователя, если он новый
        TelegramUser user = userRepository.findById(chatId).orElseGet(() -> {
            TelegramUser newUser = new TelegramUser(
                chatId,
                message.getFrom().getUserName(),
                message.getFrom().getFirstName(),
                message.getFrom().getLastName()
            );
            return userRepository.save(newUser);
        });
        
        // Проверяем, есть ли контакт в сообщении
        if (message.hasContact() && user.getState() != null && user.getState().equals("ORDERING_PHONE")) {
            // Получаем номер телефона из контакта
            String phoneNumber = message.getContact().getPhoneNumber();
            logger.info("Received contact with phone number: {}", phoneNumber);
            
            // Обрабатываем полученный номер телефона
            return stateHandler.handlePhoneNumber(chatId, phoneNumber, user);
        }
        
        // Проверяем, ожидаем ли мы изображение
        if (user.getState() != null && user.getState().equals("ADDING_PRODUCT_IMAGE")) {
            if (message.hasPhoto()) {
                // Обрабатываем полученное фото
                return stateHandler.handlePhotoUpload(chatId, message.getPhoto(), user);
            }
        }
        
        if (message.hasText()) {
            String messageText = message.getText();
            
            // Специальная обработка кнопки "Назад"
            if (messageText.equals("Назад") && user.getState() != null && !user.getState().equals("NORMAL")) {
                // Сбрасываем состояние пользователя
                user.setState("NORMAL");
                userRepository.save(user);
                logger.info("User {} pressed 'Back' button, resetting state to NORMAL", chatId);
                
                // Возвращаем пользователя в главное меню или админ-панель
                if (user.isAdmin()) {
                    return commandHandler.handleCommand(chatId, "/admin");
                } else {
                    return commandHandler.handleCommand(chatId, "/start");
                }
            }
            
            if (messageText.startsWith("/")) {
                // Обработка команд
                return commandHandler.handleCommand(chatId, messageText);
            } else {
                // Обработка текстовых сообщений
                if (user.getState() != null && !user.getState().equals("NORMAL")) {
                    // Если у пользователя есть состояние, обрабатываем его
                    BotApiMethod<?> response = stateHandler.handleState(message);
                    
                    // Если пользователь только что отредактировал категорию, отправляем обновленный список категорий
                    if (user.getState().equals("NORMAL") && response instanceof SendMessage) {
                        SendMessage responseMessage = (SendMessage) response;
                        if (responseMessage.getText().contains("Категория успешно обновлена")) {
                            try {
                                // Отправляем сообщение об успешном обновлении
                                execute(responseMessage);
                                
                                // Удаляем предыдущие сообщения с категориями
                                List<Integer> messageIds = commandHandler.getCategoryMessageIds(chatId);
                                for (Integer messageId : messageIds) {
                                    try {
                                        // Создаем запрос на удаление сообщения
                                        DeleteMessage deleteMessage = new DeleteMessage();
                                        deleteMessage.setChatId(chatId);
                                        deleteMessage.setMessageId(messageId);
                                        execute(deleteMessage);
                                    } catch (TelegramApiException e) {
                                        logger.error("Error deleting message {}", messageId, e);
                                    }
                                }
                                
                                // Очищаем список идентификаторов сообщений
                                commandHandler.clearCategoryMessageIds(chatId);
                                
                                // Показываем обновленный список категорий
                                List<Object> messages = commandHandler.showCategoriesList(chatId);
                                
                                // Отправляем все сообщения по порядку
                                for (int i = 0; i < messages.size(); i++) {
                                    Object msg = messages.get(i);
                                    try {
                                        Message sentMessage = null;
                                        if (msg instanceof SendMessage) {
                                            sentMessage = execute((SendMessage) msg);
                                        } else if (msg instanceof SendPhoto) {
                                            sentMessage = execute((SendPhoto) msg);
                                        }
                                        
                                        // Сохраняем идентификатор отправленного сообщения
                                        if (sentMessage != null) {
                                            commandHandler.saveCategoryMessageId(chatId, sentMessage.getMessageId());
                                        }
                                        
                                        // Добавляем небольшую задержку между сообщениями, чтобы они отображались в правильном порядке
                                        if (i < messages.size() - 1) {
                                            Thread.sleep(100);
                                        }
                                    } catch (TelegramApiException e) {
                                        logger.error("Error sending message", e);
                                    } catch (InterruptedException e) {
                                        logger.error("Thread sleep interrupted", e);
                                        Thread.currentThread().interrupt();
                                    }
                                }
                                
                                // Возвращаем null, так как мы уже отправили все сообщения
                                return null;
                            } catch (TelegramApiException e) {
                                logger.error("Error sending message", e);
                            }
                        }
                    }
                    
                    // Если это сообщение об успешном обновлении категории, но мы не смогли отправить список категорий
                    if (response instanceof SendMessage) {
                        SendMessage respMsg = (SendMessage) response;
                        if (respMsg.getText().contains("Категория успешно обновлена")) {
                            // Восстанавливаем клавиатуру с основными кнопками
                            try {
                                // Создаем сообщение с клавиатурой
                                SendMessage keyboardMessage = new SendMessage();
                                keyboardMessage.setChatId(chatId);
                                keyboardMessage.setText("Выберите действие:");
                                
                                // Создаем клавиатуру для админ-панели
                                ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
                                List<KeyboardRow> keyboard = new ArrayList<>();
                                
                                // Первый ряд кнопок
                                KeyboardRow row1 = new KeyboardRow();
                                row1.add(new KeyboardButton("Добавить категорию"));
                                row1.add(new KeyboardButton("Добавить товар"));
                                keyboard.add(row1);
                                
                                // Второй ряд кнопок
                                KeyboardRow row2 = new KeyboardRow();
                                row2.add(new KeyboardButton("Управление заказами"));
                                row2.add(new KeyboardButton("Список категорий"));
                                keyboard.add(row2);
                                
                                // Третий ряд кнопок
                                KeyboardRow row3 = new KeyboardRow();
                                row3.add(new KeyboardButton("Назад"));
                                keyboard.add(row3);
                                
                                keyboardMarkup.setKeyboard(keyboard);
                                keyboardMarkup.setResizeKeyboard(true);
                                keyboardMarkup.setOneTimeKeyboard(false);
                                keyboardMessage.setReplyMarkup(keyboardMarkup);
                                
                                // Отправляем сообщение с клавиатурой
                                execute(keyboardMessage);
                            } catch (TelegramApiException e) {
                                logger.error("Error sending keyboard message", e);
                            }
                        }
                    }
                    
                    return response;
                } else {
                    // Обработка обычных сообщений
                    return handleTextMessage(chatId, messageText);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Обработка текстовых сообщений
     * @param chatId ID чата
     * @param text текст сообщения
     * @return ответ бота
     */
    private BotApiMethod<?> handleTextMessage(Long chatId, String text) {
        // Получаем пользователя из базы данных
        TelegramUser user = userRepository.findById(chatId).orElse(null);
        if (user == null) {
            return createErrorMessage(chatId, "Пользователь не найден.");
        }
        
        // Обработка стандартных кнопок меню
        switch (text) {
            case "Каталог":
                // Сбрасываем состояние пользователя
                user.setState("NORMAL");
                userRepository.save(user);
                return commandHandler.handleCommand(chatId, "/catalog");
            case "Корзина":
                // Сбрасываем состояние пользователя
                user.setState("NORMAL");
                userRepository.save(user);
                return commandHandler.handleCommand(chatId, "/cart");
            case "Заказы":
                // Сбрасываем состояние пользователя
                user.setState("NORMAL");
                userRepository.save(user);
                return orderHandler.showUserOrders(chatId);
            case "Информация":
                // Сбрасываем состояние пользователя
                user.setState("NORMAL");
                userRepository.save(user);
                return createInfoMessage(chatId);
            case "Помощь":
                // Сбрасываем состояние пользователя
                user.setState("NORMAL");
                userRepository.save(user);
                return commandHandler.handleCommand(chatId, "/help");
            case "Админ-панель":
                // Сбрасываем состояние пользователя
                user.setState("NORMAL");
                userRepository.save(user);
                return commandHandler.handleCommand(chatId, "/admin");
            case "Отмена":
                // Если пользователь находится в состоянии оформления заказа или добавления товара/категории
                if (user.getState() != null && !user.getState().equals("NORMAL")) {
                    // Очищаем данные заказа
                    orderHandler.removeOrderData(chatId);
                    
                    // Сбрасываем состояние пользователя
                    user.setState("NORMAL");
                    userRepository.save(user);
                    
                    // Отправляем сообщение об отмене
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId);
                    message.setText("Действие отменено. Вы можете продолжить покупки.");
                    
                    // Добавляем клавиатуру главного меню
                    ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
                    List<KeyboardRow> keyboard = new ArrayList<>();
                    
                    // Первый ряд кнопок
                    KeyboardRow row1 = new KeyboardRow();
                    row1.add(new KeyboardButton("Каталог"));
                    row1.add(new KeyboardButton("Корзина"));
                    keyboard.add(row1);
                    
                    // Второй ряд кнопок
                    KeyboardRow row2 = new KeyboardRow();
                    row2.add(new KeyboardButton("Заказы"));
                    row2.add(new KeyboardButton("Информация"));
                    keyboard.add(row2);
                    
                    // Если пользователь админ, добавляем кнопку админ-панели
                    if (user.isAdmin()) {
                        KeyboardRow row3 = new KeyboardRow();
                        row3.add(new KeyboardButton("Админ-панель"));
                        keyboard.add(row3);
                    }
                    
                    keyboardMarkup.setKeyboard(keyboard);
                    keyboardMarkup.setResizeKeyboard(true);
                    keyboardMarkup.setOneTimeKeyboard(false);
                    message.setReplyMarkup(keyboardMarkup);
                    
                    return message;
                } else {
                    // Если пользователь не в специальном состоянии, отправляем сообщение с инструкцией
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId);
                    message.setText("Вы написали: Отмена\nИспользуйте кнопки или команды для взаимодействия с ботом.");
                    return message;
                }
            case "Управление заказами":
                if (user.isAdmin()) {
                    // Сбрасываем состояние пользователя
                    user.setState("NORMAL");
                    userRepository.save(user);
                    return orderHandler.showAllOrders(chatId);
                } else {
                    return createNoAccessMessage(chatId);
                }
            case "Добавить категорию":
                if (user.isAdmin()) {
                    // Устанавливаем состояние пользователя
                    user.setState("ADDING_CATEGORY");
                    userRepository.save(user);
                    
                    // Отправляем сообщение с запросом названия категории
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId);
                    message.setText("Введите название новой категории:");
                    return message;
                } else {
                    return createNoAccessMessage(chatId);
                }
            case "Добавить товар":
                if (user.isAdmin()) {
                    // Устанавливаем состояние пользователя
                    user.setState("ADDING_PRODUCT_CATEGORY");
                    userRepository.save(user);
                    
                    // Отправляем сообщение с запросом категории товара
                    return stateHandler.showCategoriesForProductAddition(chatId);
                } else {
                    return createNoAccessMessage(chatId);
                }
            case "Список категорий":
                if (user.isAdmin()) {
                    // Сбрасываем состояние пользователя
                    user.setState("NORMAL");
                    userRepository.save(user);
                    
                    // Очищаем предыдущие идентификаторы сообщений с категориями
                    commandHandler.clearCategoryMessageIds(chatId);
                    
                    // Получаем список сообщений с категориями
                    List<Object> messages = commandHandler.showCategoriesList(chatId);
                    
                    // Отправляем все сообщения по порядку
                    for (int i = 0; i < messages.size(); i++) {
                        Object message = messages.get(i);
                        try {
                            if (message instanceof SendMessage) {
                                execute((SendMessage) message);
                            } else if (message instanceof SendPhoto) {
                                execute((SendPhoto) message);
                            }
                            
                            // Сохраняем идентификатор отправленного сообщения
                            if (message instanceof SendMessage) {
                                Message sentMessage = execute((SendMessage) message);
                                commandHandler.saveCategoryMessageId(chatId, sentMessage.getMessageId());
                            } else if (message instanceof SendPhoto) {
                                execute((SendPhoto) message);
                            }
                            
                            // Добавляем небольшую задержку между сообщениями, чтобы они отображались в правильном порядке
                            if (i < messages.size() - 1) {
                                Thread.sleep(100);
                            }
                        } catch (TelegramApiException e) {
                            logger.error("Error sending message", e);
                        } catch (InterruptedException e) {
                            logger.error("Thread sleep interrupted", e);
                            Thread.currentThread().interrupt();
                        }
                    }
                    
                    // Возвращаем пустой ответ
                    return null;
                } else {
                    return createNoAccessMessage(chatId);
                }
            case "Назад":
                // Возвращаем пользователя в главное меню
                return commandHandler.handleCommand(chatId, "/start");
            default:
                return createDefaultMessage(chatId, text);
        }
    }
    
    /**
     * Обработка callback-запросов
     * @param update обновление
     * @return ответ бота
     */
    private BotApiMethod<?> handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        
        logger.info("Handling callback query: {} from user {}", callbackData, chatId);
        
        // Получаем пользователя
        TelegramUser user = userRepository.findById(chatId).orElse(null);
        if (user == null) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId);
            errorMessage.setText("Пользователь не найден. Пожалуйста, начните с команды /start");
            return errorMessage;
        }
        
        // Обработка различных типов callback-запросов
        if (callbackData.startsWith("category:")) {
            // Получаем слаг категории
            String categorySlug = callbackData.substring("category:".length());
            logger.info("Selected category slug: {}", categorySlug);
            
            // Отправляем товары категории
            List<Object> messages = catalogHandler.showCategoryProducts(chatId, categorySlug, 0);
            
            // Если есть сообщения, отправляем их
            if (!messages.isEmpty()) {
                // Отправляем все сообщения по порядку
                for (int i = 0; i < messages.size(); i++) {
                    Object message = messages.get(i);
                    try {
                        if (message instanceof SendMessage) {
                            execute((SendMessage) message);
                        } else if (message instanceof SendPhoto) {
                            execute((SendPhoto) message);
                        }
                        
                        // Добавляем небольшую задержку между сообщениями, чтобы они отображались в правильном порядке
                        if (i < messages.size() - 1) {
                            Thread.sleep(100);
                        }
                    } catch (TelegramApiException e) {
                        logger.error("Error sending message", e);
                    } catch (InterruptedException e) {
                        logger.error("Thread sleep interrupted", e);
                        Thread.currentThread().interrupt();
                    }
                }
                
                // Возвращаем пустой ответ
                AnswerCallbackQuery answer = new AnswerCallbackQuery();
                answer.setCallbackQueryId(update.getCallbackQuery().getId());
                return answer;
            } else {
                // Если сообщений нет, возвращаем сообщение об ошибке
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(chatId);
                errorMessage.setText("Не удалось загрузить товары для выбранной категории.");
                return errorMessage;
            }
        } else if (callbackData.startsWith("select_category:")) {
            // Обработка выбора категории при создании товара
            Long categoryId = Long.parseLong(callbackData.substring("select_category:".length()));
            logger.info("Selected category ID for product creation: {}", categoryId);
            
            // Проверяем, что пользователь находится в состоянии добавления товара
            if (!"ADDING_PRODUCT_CATEGORY".equals(user.getState())) {
                user.setState("ADDING_PRODUCT_CATEGORY");
                userRepository.save(user);
            }
            
            // Получаем категорию по ID
            Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
            
            if (categoryOpt.isPresent()) {
                Category category = categoryOpt.get();
                // Инициализируем данные для создания продукта
                Map<String, String> productData = new HashMap<>();
                productData.put("categoryId", category.getId().toString());
                stateHandler.getProductCreationData().put(chatId, productData);
                
                // Обновляем состояние пользователя
                user.setState("ADDING_PRODUCT_NAME");
                userRepository.save(user);
                
                // Отправляем сообщение с запросом названия товара
                SendMessage message = new SendMessage();
                message.setChatId(chatId);
                message.setText("Выбрана категория: " + category.getName() + "\nВведите название товара:");
                
                // Добавляем клавиатуру с кнопкой "Отмена"
                ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
                List<KeyboardRow> keyboard = new ArrayList<>();
                KeyboardRow row = new KeyboardRow();
                row.add(new KeyboardButton("Отмена"));
                keyboard.add(row);
                keyboardMarkup.setKeyboard(keyboard);
                keyboardMarkup.setResizeKeyboard(true);
                keyboardMarkup.setOneTimeKeyboard(false);
                message.setReplyMarkup(keyboardMarkup);
                
                // Отвечаем на callback-запрос
                AnswerCallbackQuery answer = new AnswerCallbackQuery();
                answer.setCallbackQueryId(update.getCallbackQuery().getId());
                
                try {
                    execute(answer);
                    execute(message);
                } catch (TelegramApiException e) {
                    logger.error("Error sending message", e);
                }
                
                return null;
            } else {
                // Если категория не найдена, отправляем сообщение об ошибке
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(chatId);
                errorMessage.setText("Категория не найдена. Пожалуйста, выберите категорию из списка:");
                return errorMessage;
            }
        } else if (callbackData.equals("cart")) {
            // Показать корзину
            return cartHandler.showCart(chatId, user);
        } else if (callbackData.startsWith("cart_minus:") || callbackData.startsWith("cart_plus:") || 
                  callbackData.startsWith("cart_remove:") || callbackData.equals("cart_clear")) {
            
            // Обрабатываем запрос
            SendMessage response;
            if (callbackData.startsWith("cart_minus:")) {
                // Уменьшить количество товара в корзине
                Long cartItemId = Long.parseLong(callbackData.substring("cart_minus:".length()));
                response = cartHandler.handleUpdateCartItemQuantity(chatId, user, cartItemId, -1);
            } else if (callbackData.startsWith("cart_plus:")) {
                // Увеличить количество товара в корзине
                Long cartItemId = Long.parseLong(callbackData.substring("cart_plus:".length()));
                response = cartHandler.handleUpdateCartItemQuantity(chatId, user, cartItemId, 1);
            } else if (callbackData.startsWith("cart_remove:")) {
                // Удалить товар из корзины
                Long cartItemId = Long.parseLong(callbackData.substring("cart_remove:".length()));
                response = cartHandler.handleRemoveFromCart(chatId, user, cartItemId);
            } else {
                // Очистить корзину
                response = cartHandler.handleClearCart(chatId, user);
            }
            
            // Отвечаем на callback query, чтобы убрать индикатор загрузки
            try {
                AnswerCallbackQuery answer = new AnswerCallbackQuery();
                answer.setCallbackQueryId(update.getCallbackQuery().getId());
                execute(answer);
            } catch (TelegramApiException e) {
                logger.error("Error answering callback query for user {}", chatId, e);
            }

                        
             // Пытаемся удалить предыдущее сообщение
            try {
                MaybeInaccessibleMessage maybeMessage = update.getCallbackQuery().getMessage();
                
                // Проверяем, доступно ли сообщение
                if (maybeMessage instanceof Message) {
                    Message message = (Message) maybeMessage;
                    int messageId = message.getMessageId();
                    
                    // Удаляем сообщение
                    DeleteMessage deleteMessage = new DeleteMessage();
                    deleteMessage.setChatId(chatId);
                    deleteMessage.setMessageId(messageId);
                    execute(deleteMessage);
                    logger.info("Deleted previous cart message for user {}", chatId);
                } else {
                    logger.warn("Message is inaccessible, cannot delete for user {}", chatId);
                }
            } catch (Exception e) {
                logger.error("Error deleting previous cart message for user {}", chatId, e);
            }
            
            return response;
        } else if (callbackData.equals("cart_checkout")) {
            // Оформить заказ из корзины
            return cartHandler.handleCartCheckout(chatId, user);
        } else if (callbackData.equals("catalog")) {
            // Показать каталог
            return catalogHandler.showCatalog(chatId);
        } else if (callbackData.startsWith("direct_buy:")) {
            // Прямая покупка товара (оформление заказа на один товар)
            Long productId = Long.parseLong(callbackData.substring("direct_buy:".length()));
            return cartHandler.handleDirectPurchase(chatId, user, productId);
        } else if (callbackData.startsWith("add_to_cart:")) {
            // Добавить товар в корзину
            Long productId = Long.parseLong(callbackData.substring("add_to_cart:".length()));
            return cartHandler.handleAddToCart(chatId, user, productId);
        } else if (callbackData.startsWith("buy:")) {
            // Обратная совместимость - добавить товар в корзину
            Long productId = Long.parseLong(callbackData.substring("buy:".length()));
            return cartHandler.handleAddToCart(chatId, user, productId);
        } else if (callbackData.startsWith("order_details:")) {
            Long orderId = Long.parseLong(callbackData.substring("order_details:".length()));
            return orderHandler.showOrderDetails(chatId, orderId);
        } else if (callbackData.equals("my_orders")) {
            return orderHandler.showUserOrders(chatId);
        } else if (callbackData.equals("all_orders")) {
            return orderHandler.showAllOrders(chatId);
        } else if (callbackData.startsWith("accept_order:")) {
            Long orderId = Long.parseLong(callbackData.substring("accept_order:".length()));
            orderHandler.updateOrderStatus(orderId, "PROCESSING");
            return orderHandler.showOrderDetails(chatId, orderId);
        } else if (callbackData.startsWith("complete_order:")) {
            Long orderId = Long.parseLong(callbackData.substring("complete_order:".length()));
            orderHandler.updateOrderStatus(orderId, "COMPLETED");
            return orderHandler.showOrderDetails(chatId, orderId);
        } else if (callbackData.startsWith("admin_cancel_order:")) {
            Long orderId = Long.parseLong(callbackData.substring("admin_cancel_order:".length()));
            orderHandler.cancelOrder(orderId);
            return orderHandler.showOrderDetails(chatId, orderId);
        } else if (callbackData.equals("confirm_order")) {
            logger.info("Received confirm_order callback from user {}", chatId);
            return handleOrderConfirmation(chatId);
        } else if (callbackData.equals("cancel_order")) {
            logger.info("Received cancel_order callback from user {}", chatId);
            return handleOrderCancellation(chatId);
        } else if (callbackData.equals("back")) {
            return commandHandler.handleCommand(chatId, "/start");
        } else if (callbackData.equals("admin")) {
            return commandHandler.handleCommand(chatId, "/admin");
        } else if (callbackData.equals("home")) {
            return commandHandler.handleCommand(chatId, "/start");
        } else if (callbackData.equals("more")) {
            logger.info("'More' button clicked by user {}", chatId);
            
            // Получаем текущую категорию и страницу
            String categorySlug = catalogHandler.getCurrentCategory(chatId);
            Integer currentPage = catalogHandler.getCurrentPage(chatId);
            
            if (categorySlug != null && currentPage != null) {
                // Увеличиваем номер страницы
                int nextPage = currentPage + 1;
                logger.info("Loading next page: {} for category: {}", nextPage, categorySlug);
                
                // Обновляем текущую страницу
                catalogHandler.setCurrentPage(chatId, nextPage);
                
                // Отправляем товары для следующей страницы
                List<Object> messages = catalogHandler.showCategoryProducts(chatId, categorySlug, nextPage);
                
                // Если есть сообщения, отправляем их
                if (!messages.isEmpty()) {
                    // Отправляем все сообщения по порядку
                    for (int i = 0; i < messages.size(); i++) {
                        Object message = messages.get(i);
                        try {
                            if (message instanceof SendMessage) {
                                execute((SendMessage) message);
                            } else if (message instanceof SendPhoto) {
                                execute((SendPhoto) message);
                            }
                            
                            // Добавляем небольшую задержку между сообщениями, чтобы они отображались в правильном порядке
                            if (i < messages.size() - 1) {
                                Thread.sleep(100);
                            }
                        } catch (TelegramApiException e) {
                            logger.error("Error sending message", e);
                        } catch (InterruptedException e) {
                            logger.error("Thread sleep interrupted", e);
                            Thread.currentThread().interrupt();
                        }
                    }
                    
                    // Возвращаем ответ на callback query
                    AnswerCallbackQuery answer = new AnswerCallbackQuery();
                    answer.setCallbackQueryId(update.getCallbackQuery().getId());
                    return answer;
                } else {
                    // Если сообщений нет, возвращаем сообщение об ошибке
                    SendMessage errorMessage = new SendMessage();
                    errorMessage.setChatId(chatId);
                    errorMessage.setText("Не удалось загрузить товары для следующей страницы.");
                    return errorMessage;
                }
            } else {
                logger.warn("No current category or page found for user: {}", chatId);
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(chatId);
                errorMessage.setText("Произошла ошибка. Пожалуйста, выберите категорию снова.");
                return errorMessage;
            }
        } else if (callbackData.startsWith("edit_category:")) {
            // Обработка кнопки "Редактировать категорию"
            Long categoryId = Long.parseLong(callbackData.substring("edit_category:".length()));
            logger.info("Edit category button clicked for category ID: {}", categoryId);
            
            // Проверяем, что пользователь администратор
            if (!user.isAdmin()) {
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(chatId);
                errorMessage.setText("У вас нет прав для редактирования категорий.");
                return errorMessage;
            }
            
            // Сохраняем ID категории в состоянии пользователя
            Map<String, String> categoryData = new HashMap<>();
            categoryData.put("categoryId", categoryId.toString());
            stateHandler.saveCategoryData(chatId, categoryData);
            
            // Устанавливаем состояние пользователя
            user.setState("EDITING_CATEGORY");
            userRepository.save(user);
            
            // Запрашиваем новое название категории
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Введите новое название для категории:");
            
            // Добавляем кнопку отмены
            ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
            List<KeyboardRow> keyboard = new ArrayList<>();
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton("Отмена"));
            keyboard.add(row);
            keyboardMarkup.setKeyboard(keyboard);
            keyboardMarkup.setResizeKeyboard(true);
            keyboardMarkup.setOneTimeKeyboard(true);
            message.setReplyMarkup(keyboardMarkup);
            
            return message;
        } else if (callbackData.startsWith("delete_category:")) {
            // Обработка кнопки "Удалить категорию"
            Long categoryId = Long.parseLong(callbackData.substring("delete_category:".length()));
            logger.info("Delete category button clicked for category ID: {}", categoryId);
            
            // Проверяем, что пользователь администратор
            if (!user.isAdmin()) {
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(chatId);
                errorMessage.setText("У вас нет прав для удаления категорий.");
                return errorMessage;
            }
            
            try {
                // Удаляем категорию
                categoryService.deleteCategory(categoryId);
                
                // Отправляем сообщение об успешном удалении
                SendMessage message = new SendMessage();
                message.setChatId(chatId);
                message.setText("Категория успешно удалена!");
                execute(message);
                
                // Удаляем предыдущие сообщения с категориями
                List<Integer> messageIds = commandHandler.getCategoryMessageIds(chatId);
                for (Integer messageId : messageIds) {
                    try {
                        // Создаем запрос на удаление сообщения
                        DeleteMessage deleteMessage = new DeleteMessage();
                        deleteMessage.setChatId(chatId);
                        deleteMessage.setMessageId(messageId);
                        execute(deleteMessage);
                    } catch (TelegramApiException e) {
                        logger.error("Error deleting message {}", messageId, e);
                    }
                }
                
                // Очищаем список идентификаторов сообщений
                commandHandler.clearCategoryMessageIds(chatId);
                
                // Показываем обновленный список категорий
                List<Object> messages = commandHandler.showCategoriesList(chatId);
                
                // Отправляем все сообщения по порядку
                for (int i = 0; i < messages.size(); i++) {
                    Object msg = messages.get(i);
                    try {
                        Message sentMessage = null;
                        if (msg instanceof SendMessage) {
                            sentMessage = execute((SendMessage) msg);
                        } else if (msg instanceof SendPhoto) {
                            sentMessage = execute((SendPhoto) msg);
                        }
                        
                        // Сохраняем идентификатор отправленного сообщения
                        if (sentMessage != null) {
                            commandHandler.saveCategoryMessageId(chatId, sentMessage.getMessageId());
                        }
                        
                        // Добавляем небольшую задержку между сообщениями, чтобы они отображались в правильном порядке
                        if (i < messages.size() - 1) {
                            Thread.sleep(100);
                        }
                    } catch (TelegramApiException e) {
                        logger.error("Error sending message", e);
                    } catch (InterruptedException e) {
                        logger.error("Thread sleep interrupted", e);
                        Thread.currentThread().interrupt();
                    }
                }
                
                // Восстанавливаем клавиатуру с основными кнопками
                try {
                    // Создаем сообщение с клавиатурой
                    SendMessage keyboardMessage = new SendMessage();
                    keyboardMessage.setChatId(chatId);
                    keyboardMessage.setText("Выберите действие:");
                    
                    // Создаем клавиатуру для админ-панели
                    ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
                    List<KeyboardRow> keyboard = new ArrayList<>();
                    
                    // Первый ряд кнопок
                    KeyboardRow row1 = new KeyboardRow();
                    row1.add(new KeyboardButton("Добавить категорию"));
                    row1.add(new KeyboardButton("Добавить товар"));
                    keyboard.add(row1);
                    
                    // Второй ряд кнопок
                    KeyboardRow row2 = new KeyboardRow();
                    row2.add(new KeyboardButton("Управление заказами"));
                    row2.add(new KeyboardButton("Список категорий"));
                    keyboard.add(row2);
                    
                    // Третий ряд кнопок
                    KeyboardRow row3 = new KeyboardRow();
                    row3.add(new KeyboardButton("Назад"));
                    keyboard.add(row3);
                    
                    keyboardMarkup.setKeyboard(keyboard);
                    keyboardMarkup.setResizeKeyboard(true);
                    keyboardMarkup.setOneTimeKeyboard(false);
                    keyboardMessage.setReplyMarkup(keyboardMarkup);
                    
                    // Отправляем сообщение с клавиатурой
                    execute(keyboardMessage);
                } catch (TelegramApiException e) {
                    logger.error("Error sending keyboard message", e);
                }
                
                // Возвращаем ответ на callback query
                AnswerCallbackQuery answer = new AnswerCallbackQuery();
                answer.setCallbackQueryId(update.getCallbackQuery().getId());
                return answer;
            } catch (Exception e) {
                logger.error("Error deleting category", e);
                
                // Отправляем сообщение об ошибке
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(chatId);
                errorMessage.setText("Произошла ошибка при удалении категории. Пожалуйста, попробуйте еще раз.");
                return errorMessage;
            }
        }
        
        return null;
    }
    
    /**
     * Обработка подтверждения заказа
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleOrderConfirmation(Long chatId) {
        logger.info("Handling order confirmation for user {}", chatId);
        
        TelegramUser user = userRepository.findById(chatId).orElse(null);
        if (user == null) {
            logger.error("User not found for chat ID: {}", chatId);
            return createErrorMessage(chatId, "Пользователь не найден.");
        }
        
        // Получаем данные заказа
        Map<String, String> orderData = orderHandler.getOrderData(chatId);
        logger.info("Order data for user {}: {}", chatId, orderData);
        
        if (orderData == null || !orderData.containsKey("phone") || !orderData.containsKey("address")) {
            logger.error("Invalid order data for user {}: {}", chatId, orderData);
            return createErrorMessage(chatId, "Данные заказа не найдены.");
        }
        
        String phoneNumber = orderData.get("phone");
        String address = orderData.get("address");
        
        // Проверяем, заказ из корзины или прямая покупка
        if (orderData.containsKey("fromCart") && "true".equals(orderData.get("fromCart"))) {
            logger.info("Creating order from cart for user {}", chatId);
            
            // Получаем товары из корзины
            List<CartItem> cartItems = cartService.getCartItems(user);
            if (cartItems.isEmpty()) {
                logger.error("Cart is empty for user {}", chatId);
                return createErrorMessage(chatId, "Ваша корзина пуста. Добавьте товары из каталога.");
            }
            
            // Создаем заказы для каждого товара в корзине
            List<Order> orders = new ArrayList<>();
            for (CartItem item : cartItems) {
                Order order = orderHandler.createOrder(user, item.getProduct().getId(), phoneNumber, address, item.getQuantity());
                if (order != null) {
                    orders.add(order);
                    logger.info("Order created successfully for user {} and product {}: {}", 
                               chatId, item.getProduct().getId(), order.getId());
                }
            }
            
            if (orders.isEmpty()) {
                logger.error("Failed to create any orders for user {}", chatId);
                return createErrorMessage(chatId, "Не удалось создать заказы. Пожалуйста, попробуйте снова.");
            }
            
            // Очищаем корзину
            cartService.clearCart(user);
            logger.info("Cart cleared for user {}", chatId);
            
            // Очищаем данные заказа
            orderHandler.removeOrderData(chatId);
            
            // Сбрасываем состояние пользователя
            user.setState("NORMAL");
            userRepository.save(user);
            
            // Отправляем уведомления администраторам о каждом заказе
            for (Order order : orders) {
                sendOrderNotificationToAdmins(order);
            }
            
            // Отправляем подтверждение пользователю
            return createCartOrdersConfirmationMessage(chatId, orders);
        } else if (orderData.containsKey("productId")) {
            // Прямая покупка одного товара
            Long productId = Long.parseLong(orderData.get("productId"));
            
            logger.info("Creating direct order for user {} with product ID: {}, phone: {}, address: {}", 
                       chatId, productId, phoneNumber, address);
            
            // Получаем количество товара (по умолчанию 1)
            int quantity = 1;
            if (orderData.containsKey("quantity")) {
                quantity = Integer.parseInt(orderData.get("quantity"));
            }
            
            Order order = orderHandler.createOrder(user, productId, phoneNumber, address, quantity);
            if (order == null) {
                logger.error("Failed to create order for user {}", chatId);
                return createErrorMessage(chatId, "Не удалось создать заказ. Пожалуйста, попробуйте снова.");
            }
            
            logger.info("Order created successfully for user {}: {}", chatId, order.getId());
            
            // Очищаем данные заказа
            orderHandler.removeOrderData(chatId);
            
            // Сбрасываем состояние пользователя
            user.setState("NORMAL");
            userRepository.save(user);
            
            // Отправляем уведомление администраторам
            sendOrderNotificationToAdmins(order);
            
            // Отправляем подтверждение пользователю
            return createOrderConfirmationMessage(chatId, order);
        } else {
            logger.error("Unknown order type for user {}", chatId);
            return createErrorMessage(chatId, "Неизвестный тип заказа. Пожалуйста, попробуйте снова.");
        }
    }
    
    /**
     * Создание сообщения с подтверждением заказов из корзины
     * @param chatId ID чата
     * @param orders список заказов
     * @return сообщение с подтверждением
     */
    private SendMessage createCartOrdersConfirmationMessage(Long chatId, List<Order> orders) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        StringBuilder text = new StringBuilder("Спасибо за заказы! Ваши заказы приняты и будут обработаны в ближайшее время.\n\n");
        text.append("Номера заказов: ");
        
        for (int i = 0; i < orders.size(); i++) {
            text.append("#").append(orders.get(i).getId());
            if (i < orders.size() - 1) {
                text.append(", ");
            }
        }
        
        text.append("\n\nНаш менеджер свяжется с вами по указанному номеру телефона.");
        
        message.setText(text.toString());
        
        // Добавляем клавиатуру главного меню
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        
        // Первый ряд кнопок
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Каталог"));
        row1.add(new KeyboardButton("Корзина"));
        keyboard.add(row1);
        
        // Второй ряд кнопок
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Заказы"));
        row2.add(new KeyboardButton("Информация"));
        keyboard.add(row2);
        
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        message.setReplyMarkup(keyboardMarkup);
        
        return message;
    }
    
    /**
     * Отправка уведомления о заказе администраторам
     * @param order заказ
     */
    private void sendOrderNotificationToAdmins(Order order) {
        List<TelegramUser> admins = userRepository.findByRole("ADMIN");
        for (TelegramUser admin : admins) {
            try {
                SendMessage adminNotification = createAdminOrderNotification(admin.getChatId(), order);
                execute(adminNotification);
                logger.info("Order notification sent to admin {}", admin.getChatId());
            } catch (TelegramApiException e) {
                logger.error("Error sending order notification to admin {}", admin.getChatId(), e);
            }
        }
    }
    
    /**
     * Создание уведомления о заказе для администратора
     * @param chatId ID чата администратора
     * @param order заказ
     * @return сообщение с уведомлением
     */
    private SendMessage createAdminOrderNotification(Long chatId, Order order) {
        StringBuilder adminMessage = new StringBuilder();
        adminMessage.append("🔔 НОВЫЙ ЗАКАЗ #").append(order.getId()).append(" 🔔\n\n");
        adminMessage.append("Товар: ").append(order.getProduct().getName()).append("\n");
        adminMessage.append("Артикул: ").append(String.format("%08d", order.getProduct().getId())).append("\n");
        adminMessage.append("Цена: ").append(order.getProduct().getPrice()).append(" $\n\n");
        adminMessage.append("Клиент: ").append(order.getUser().getFirstName());
        if (order.getUser().getLastName() != null) {
            adminMessage.append(" ").append(order.getUser().getLastName());
        }
        adminMessage.append("\n");
        adminMessage.append("Телефон: ").append(order.getPhoneNumber()).append("\n");
        adminMessage.append("Адрес: ").append(order.getAddress()).append("\n");
        adminMessage.append("Время заказа: ").append(order.getFormattedCreatedAt());
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(adminMessage.toString());
        
        // Добавляем кнопки для управления заказом
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        
        // Кнопка "Принять заказ"
        List<InlineKeyboardButton> acceptRow = new ArrayList<>();
        InlineKeyboardButton acceptButton = new InlineKeyboardButton();
        acceptButton.setText("✅ Принять заказ");
        acceptButton.setCallbackData("accept_order:" + order.getId());
        acceptRow.add(acceptButton);
        rowsInline.add(acceptRow);
        
        // Кнопка "Отменить заказ"
        List<InlineKeyboardButton> cancelRow = new ArrayList<>();
        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отменить заказ");
        cancelButton.setCallbackData("admin_cancel_order:" + order.getId());
        cancelRow.add(cancelButton);
        rowsInline.add(cancelRow);
        
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);
        
        return message;
    }
    
    /**
     * Создание сообщения с подтверждением заказа
     * @param chatId ID чата
     * @param order заказ
     * @return сообщение с подтверждением
     */
    private SendMessage createOrderConfirmationMessage(Long chatId, Order order) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Спасибо за заказ! Ваш заказ #" + order.getId() + " принят и будет обработан в ближайшее время. Наш менеджер свяжется с вами по указанному номеру телефона.");
        
        // Добавляем клавиатуру главного меню
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        
        // Первый ряд кнопок
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Каталог"));
        row1.add(new KeyboardButton("Корзина"));
        keyboard.add(row1);
        
        // Второй ряд кнопок
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Заказы"));
        row2.add(new KeyboardButton("Информация"));
        keyboard.add(row2);
        
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        message.setReplyMarkup(keyboardMarkup);
        
        return message;
    }
    
    /**
     * Обработка отмены заказа
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleOrderCancellation(Long chatId) {
        TelegramUser user = userRepository.findById(chatId).orElse(null);
        if (user == null) {
            return createErrorMessage(chatId, "Пользователь не найден.");
        }
        
        // Очищаем данные заказа
        orderHandler.removeOrderData(chatId);
        
        // Сбрасываем состояние пользователя
        user.setState("NORMAL");
        userRepository.save(user);
        
        // Отправляем сообщение об отмене
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Заказ отменен. Вы можете продолжить покупки.");
        
        // Добавляем клавиатуру главного меню
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        
        // Первый ряд кнопок
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Каталог"));
        row1.add(new KeyboardButton("Корзина"));
        keyboard.add(row1);
        
        // Второй ряд кнопок
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Заказы"));
        row2.add(new KeyboardButton("Информация"));
        keyboard.add(row2);
        
        // Если пользователь админ, добавляем кнопку админ-панели
        if (user.isAdmin()) {
            KeyboardRow row3 = new KeyboardRow();
            row3.add(new KeyboardButton("Админ-панель"));
            keyboard.add(row3);
        }
        
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        message.setReplyMarkup(keyboardMarkup);
        
        return message;
    }
    
    /**
     * Создание сообщения с информацией
     * @param chatId ID чата
     * @return сообщение с информацией
     */
    private SendMessage createInfoMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Наш магазин предлагает широкий ассортимент товаров.\n\n" +
                       "Контактная информация:\n" +
                       "Телефон: +7 (123) 456-78-90\n" +
                       "Email: info@example.com\n" +
                       "Адрес: г. Москва, ул. Примерная, д. 123");
        return message;
    }
    
    /**
     * Создание сообщения об отсутствии доступа
     * @param chatId ID чата
     * @return сообщение об отсутствии доступа
     */
    private SendMessage createNoAccessMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("У вас нет прав доступа к этому разделу.");
        return message;
    }
    
    /**
     * Создание сообщения об ошибке
     * @param chatId ID чата
     * @param errorText текст ошибки
     * @return сообщение об ошибке
     */
    private SendMessage createErrorMessage(Long chatId, String errorText) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(errorText);
        return message;
    }
    
    /**
     * Создание стандартного сообщения
     * @param chatId ID чата
     * @param text текст сообщения
     * @return стандартное сообщение
     */
    private SendMessage createDefaultMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Вы написали: " + text + "\nИспользуйте кнопки или команды для взаимодействия с ботом.");
        return message;
    }
}