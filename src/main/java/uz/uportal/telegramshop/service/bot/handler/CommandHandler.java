package uz.uportal.telegramshop.service.bot.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.OrderService;
import uz.uportal.telegramshop.service.CategoryService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команд бота
 */
@Component
public class CommandHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CommandHandler.class);
    
    private final TelegramUserRepository userRepository;
    private final OrderHandler orderHandler;
    private final CatalogHandler catalogHandler;
    private final CategoryService categoryService;
    private final CartHandler cartHandler;
    
    // Хранение идентификаторов сообщений с категориями для каждого пользователя
    private final Map<Long, List<Integer>> categoryMessagesIds = new HashMap<>();
    
    public CommandHandler(TelegramUserRepository userRepository, 
                         OrderHandler orderHandler,
                         CatalogHandler catalogHandler,
                         CategoryService categoryService,
                         CartHandler cartHandler) {
        this.userRepository = userRepository;
        this.orderHandler = orderHandler;
        this.catalogHandler = catalogHandler;
        this.categoryService = categoryService;
        this.cartHandler = cartHandler;
    }
    
    /**
     * Обработка команд бота
     * @param chatId ID чата
     * @param command команда
     * @return ответ бота
     */
    public BotApiMethod<?> handleCommand(Long chatId, String command) {
        // Получаем пользователя из базы данных
        TelegramUser user = userRepository.findById(chatId).orElse(null);
        boolean isAdmin = user != null && user.isAdmin();
        
        // Обработка команд
        switch (command) {
            case "/start":
                return createStartMessage(chatId);
            case "/help":
                return createHelpMessage(chatId);
            case "/orders":
                return orderHandler.showUserOrders(chatId);
            case "/catalog":
                return catalogHandler.showCatalog(chatId);
            case "/cart":
                if (user != null) {
                    return cartHandler.showCart(chatId, user);
                } else {
                    SendMessage errorMessage = new SendMessage();
                    errorMessage.setChatId(chatId);
                    errorMessage.setText("Пользователь не найден. Пожалуйста, начните с команды /start");
                    return errorMessage;
                }
            case "/admin":
                if (isAdmin) {
                    return createAdminMessage(chatId);
                } else {
                    return createNoAccessMessage(chatId);
                }
            default:
                return createDefaultMessage(chatId);
        }
    }
    
    /**
     * Создание стартового сообщения
     * @param chatId ID чата
     * @return стартовое сообщение
     */
    private SendMessage createStartMessage(Long chatId) {
        // Получаем пользователя из базы данных
        TelegramUser user = userRepository.findById(chatId).orElse(null);
        boolean isAdmin = user != null && user.isAdmin();
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Добро пожаловать в магазин! Используйте меню для навигации.");
        
        // Создаем клавиатуру с кнопками
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
        
        // Третий ряд кнопок
        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("Помощь"));
        if (isAdmin) {
            row3.add(new KeyboardButton("Админ-панель"));
        }
        keyboard.add(row3);
        
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        message.setReplyMarkup(keyboardMarkup);
        
        return message;
    }
    
    /**
     * Создание сообщения с помощью
     * @param chatId ID чата
     * @return сообщение с помощью
     */
    private SendMessage createHelpMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Доступные команды:\n\n" +
                "/start - начать работу с ботом\n" +
                "/help - показать справку\n" +
                "/catalog - просмотр каталога товаров\n" +
                "Корзина - просмотр добавленных товаров\n" +
                "/orders - просмотр ваших заказов\n" +
                "/admin - панель администратора (только для администраторов)");
        return message;
    }
    
    /**
     * Создание сообщения с панелью администратора
     * @param chatId ID чата
     * @return сообщение с панелью администратора
     */
    private SendMessage createAdminMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        // Создаем клавиатуру с кнопками
        ReplyKeyboardMarkup adminKeyboard = new ReplyKeyboardMarkup();
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
        
        adminKeyboard.setKeyboard(keyboard);
        adminKeyboard.setResizeKeyboard(true);
        adminKeyboard.setOneTimeKeyboard(false);
        
        message.setReplyMarkup(adminKeyboard);
        message.setText("Панель администратора. Выберите действие:");
        
        return message;
    }
    
    /**
     * Показать список категорий
     * @param chatId ID чата
     * @return список сообщений с категориями
     */
    public List<Object> showCategoriesList(Long chatId) {
        List<Object> messages = new ArrayList<>();
        
        // Получаем список категорий
        List<Category> categories = categoryService.getAllCategories();
        
        if (categories.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Нет доступных категорий.");
            messages.add(message);
            return messages;
        }
        
        // Создаем сообщение со списком категорий
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Список категорий:");
        messages.add(message);
        
        // Создаем сообщения для каждой категории
        for (Category category : categories) {
            SendMessage categoryMessage = new SendMessage();
            categoryMessage.setChatId(chatId);
            categoryMessage.setText("ID: " + category.getId() + "\n" +
                    "Название: " + category.getName() + "\n" +
                    "Слаг: " + category.getSlug());
            
            // Добавляем кнопки для редактирования и удаления категории
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            
            List<InlineKeyboardButton> rowInline = new ArrayList<>();
            
            // Кнопка редактирования
            InlineKeyboardButton editButton = new InlineKeyboardButton();
            editButton.setText("Редактировать");
            editButton.setCallbackData("edit_category:" + category.getId());
            rowInline.add(editButton);
            
            // Кнопка удаления
            InlineKeyboardButton deleteButton = new InlineKeyboardButton();
            deleteButton.setText("Удалить");
            deleteButton.setCallbackData("delete_category:" + category.getId());
            rowInline.add(deleteButton);
            
            rowsInline.add(rowInline);
            markupInline.setKeyboard(rowsInline);
            categoryMessage.setReplyMarkup(markupInline);
            
            messages.add(categoryMessage);
        }
        
        return messages;
    }
    
    /**
     * Сохранить идентификатор сообщения с категорией
     * @param chatId ID чата
     * @param messageId ID сообщения
     */
    public void saveCategoryMessageId(Long chatId, Integer messageId) {
        categoryMessagesIds.computeIfAbsent(chatId, k -> new ArrayList<>()).add(messageId);
    }
    
    /**
     * Получить идентификаторы сообщений с категориями
     * @param chatId ID чата
     * @return список идентификаторов сообщений
     */
    public List<Integer> getCategoryMessageIds(Long chatId) {
        return categoryMessagesIds.getOrDefault(chatId, new ArrayList<>());
    }
    
    /**
     * Очистить идентификаторы сообщений с категориями
     * @param chatId ID чата
     */
    public void clearCategoryMessageIds(Long chatId) {
        categoryMessagesIds.remove(chatId);
    }
    
    /**
     * Создание сообщения с отказом в доступе
     * @param chatId ID чата
     * @return сообщение с отказом в доступе
     */
    private SendMessage createNoAccessMessage(Long chatId) {
        SendMessage noAccessMessage = new SendMessage();
        noAccessMessage.setChatId(chatId);
        noAccessMessage.setText("У вас нет прав доступа к этой команде.");
        return noAccessMessage;
    }
    
    /**
     * Создание сообщения по умолчанию
     * @param chatId ID чата
     * @return сообщение по умолчанию
     */
    private SendMessage createDefaultMessage(Long chatId) {
        SendMessage unknownCommandMessage = new SendMessage();
        unknownCommandMessage.setChatId(chatId);
        unknownCommandMessage.setText("Неизвестная команда. Используйте /help для получения списка доступных команд.");
        return unknownCommandMessage;
    }
}
