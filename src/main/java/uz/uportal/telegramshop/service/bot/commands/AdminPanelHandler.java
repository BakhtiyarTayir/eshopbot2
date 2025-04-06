package uz.uportal.telegramshop.service.bot.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.model.Product;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.CategoryService;
import uz.uportal.telegramshop.service.ProductService;
import uz.uportal.telegramshop.service.bot.core.UpdateHandler;
import uz.uportal.telegramshop.service.bot.keyboards.KeyboardFactory;
import uz.uportal.telegramshop.service.bot.core.MessageSender;

import java.util.List;

/**
 * Обработчик админ-панели
 */
@Component
public class AdminPanelHandler implements UpdateHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminPanelHandler.class);
    private final TelegramUserRepository telegramUserRepository;
    private final KeyboardFactory keyboardFactory;
    private final ProductService productService;
    private final CategoryService categoryService;
    private final MessageSender messageSender;
    
    // Константы для размера страницы при пагинации
    private static final int PRODUCTS_PAGE_SIZE = 5;
    private static final int CATEGORIES_PAGE_SIZE = 5;
    private static final int USERS_PAGE_SIZE = 10;
    
    public AdminPanelHandler(
            TelegramUserRepository telegramUserRepository,
            KeyboardFactory keyboardFactory,
            ProductService productService,
            CategoryService categoryService,
            MessageSender messageSender) {
        this.telegramUserRepository = telegramUserRepository;
        this.keyboardFactory = keyboardFactory;
        this.productService = productService;
        this.categoryService = categoryService;
        this.messageSender = messageSender;
    }
    
    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }
        
        String text = update.getMessage().getText();
        return text.equals("⚙️ Админ панель") || 
               text.equals("📋 Список товаров") || 
               text.equals("➕ Добавить товар") || 
               text.equals("🗂 Список категорий") || 
               text.equals("➕ Добавить категорию") || 
               text.equals("📦 Управление заказами") || 
               text.equals("👥 Список пользователей") || 
               text.equals("⬅️ Вернуться в главное меню");
    }
    
    @Override
    public BotApiMethod<?> handle(Update update) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String text = message.getText();
        
        logger.info("Handling admin panel button: {} for chatId: {}", text, chatId);
        
        // Получаем пользователя
        TelegramUser user = telegramUserRepository.findById(chatId)
                .orElse(null);
        
        if (user == null) {
            return createTextMessage(chatId, "Пользователь не найден. Пожалуйста, перезапустите бота командой /start");
        }
        
        // Проверяем права доступа
        if (!"ADMIN".equals(user.getRole()) && !"MANAGER".equals(user.getRole())) {
            return createTextMessage(chatId, "У вас нет доступа к панели администратора.");
        }
        
        // Обрабатываем нажатие кнопки
        switch (text) {
            case "⚙️ Админ панель":
                return handleAdminPanel(chatId);
            case "📋 Список товаров":
                return handleProductsList(chatId, 1);
            case "➕ Добавить товар":
                return handleAddProduct(chatId);
            case "🗂 Список категорий":
                return handleCategoriesList(chatId, 1);
            case "➕ Добавить категорию":
                return handleAddCategory(chatId);
            case "📦 Управление заказами":
                return handleOrdersManagement(chatId);
            case "👥 Список пользователей":
                return handleUsersList(chatId, 1);
            case "⬅️ Вернуться в главное меню":
                return handleBackToMainMenu(chatId, user);
            default:
                // Если команда не распознана, отправляем подсказку
                logger.warn("Unrecognized admin panel command: {}", text);
                return createTextMessage(chatId, "Пожалуйста, используйте кнопки меню для навигации.");
        }
    }
    
    /**
     * Обрабатывает нажатие кнопки "Админ панель"
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleAdminPanel(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("⚙️ *Панель администратора*\n\n" +
                "Выберите действие из меню ниже:");
        sendMessage.setParseMode("Markdown");
        sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Список товаров"
     * @param chatId ID чата
     * @param page номер страницы
     * @return сообщение с списком товаров
     */
    private BotApiMethod<?> handleProductsList(Long chatId, int page) {
        Pageable pageable = PageRequest.of(page - 1, PRODUCTS_PAGE_SIZE);
        Page<Product> productsPage = productService.getAllProducts(pageable);
        
        // Если список товаров пуст, отправляем сообщение об этом
        if (productsPage.isEmpty()) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("Список товаров пуст. Добавьте товары, нажав на кнопку '➕ Добавить товар'.");
            return sendMessage;
        }
        
        // Отправляем заголовок списка товаров
        SendMessage headerMessage = new SendMessage();
        headerMessage.setChatId(chatId);
        headerMessage.setText("📋 *Список товаров* (страница " + page + " из " + productsPage.getTotalPages() + ")");
        headerMessage.setParseMode("Markdown");
        
        try {
            // Отправляем заголовок
            messageSender.executeMessage(headerMessage);
            
            // Отправляем каждый товар отдельным сообщением с изображением
            List<Product> products = productsPage.getContent();
            for (Product product : products) {
                StringBuilder productText = new StringBuilder();
                productText.append("*").append(product.getName()).append("*\n\n");
                productText.append("💰 Цена: ").append(product.getPrice()).append(" руб.\n");
                productText.append("📦 В наличии: ").append(product.getStock()).append(" шт.\n");
                productText.append("🗂 Категория: ").append(product.getCategory() != null ? product.getCategory().getName() : "Не указана").append("\n\n");
                productText.append("📝 Описание: ").append(product.getDescription()).append("\n\n");
                
                // Проверяем, есть ли у товара изображение
                if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                    // Отправляем сообщение с изображением
                    SendPhoto sendPhoto = new SendPhoto();
                    sendPhoto.setChatId(chatId);
                    sendPhoto.setPhoto(new InputFile(product.getImageUrl()));
                    sendPhoto.setCaption(productText.toString());
                    sendPhoto.setParseMode("Markdown");
                    sendPhoto.setReplyMarkup(keyboardFactory.createProductManagementKeyboard(product.getId()));
                    messageSender.executePhoto(sendPhoto);
                } else {
                    // Если изображения нет, отправляем обычное текстовое сообщение
                    SendMessage productMessage = new SendMessage();
                    productMessage.setChatId(chatId);
                    productMessage.setText(productText.toString());
                    productMessage.setParseMode("Markdown");
                    productMessage.setReplyMarkup(keyboardFactory.createProductManagementKeyboard(product.getId()));
                    messageSender.executeMessage(productMessage);
                }
                
                // Добавляем небольшую задержку между сообщениями
                Thread.sleep(100);
            }
            
            // Отправляем сообщение с пагинацией
            SendMessage paginationMessage = new SendMessage();
            paginationMessage.setChatId(chatId);
            paginationMessage.setText("Страница " + page + " из " + productsPage.getTotalPages());
            paginationMessage.setReplyMarkup(keyboardFactory.createProductPaginationKeyboard(page, productsPage.getTotalPages()));
            messageSender.executeMessage(paginationMessage);
            
            // Возвращаем пустое сообщение, чтобы бот не отправлял сообщение "Извините, я не понимаю эту команду"
            SendMessage emptyMessage = new SendMessage();
            emptyMessage.setChatId(chatId);
            emptyMessage.setText("");
            return emptyMessage;
            
        } catch (Exception e) {
            logger.error("Ошибка при отправке списка товаров: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при отправке списка товаров. Пожалуйста, попробуйте позже.");
        }
    }
    
    /**
     * Обрабатывает нажатие кнопки "Добавить товар"
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddProduct(Long chatId) {
        // Устанавливаем состояние пользователя для добавления товара
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState("ADDING_PRODUCT_NAME");
            telegramUserRepository.save(user);
        }
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Введите название товара:");
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Список категорий"
     * @param chatId ID чата
     * @param page номер страницы
     * @return ответ бота
     */
    private BotApiMethod<?> handleCategoriesList(Long chatId, int page) {
        Pageable pageable = PageRequest.of(page - 1, CATEGORIES_PAGE_SIZE);
        Page<Category> categoriesPage = categoryService.getAllCategories(pageable);
        
        if (categoriesPage.isEmpty()) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("Список категорий пуст. Добавьте категории, нажав на кнопку '➕ Добавить категорию'.");
            return sendMessage;
        }
        
        StringBuilder messageText = new StringBuilder();
        messageText.append("🗂 *Список категорий* (страница ").append(page).append(" из ").append(categoriesPage.getTotalPages()).append(")\n\n");
        
        List<Category> categories = categoriesPage.getContent();
        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            messageText.append(i + 1).append(". *").append(category.getName()).append("*\n");
            messageText.append("   Описание: ").append(category.getDescription() != null ? category.getDescription() : "Не указано").append("\n\n");
        }
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText.toString());
        sendMessage.setParseMode("Markdown");
        sendMessage.setReplyMarkup(keyboardFactory.createCategoryPaginationKeyboard(page, categoriesPage.getTotalPages(), categories));
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Добавить категорию"
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddCategory(Long chatId) {
        // Устанавливаем состояние пользователя для добавления категории
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState("ADDING_CATEGORY_NAME");
            telegramUserRepository.save(user);
        }
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Введите название категории:");
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Управление заказами"
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleOrdersManagement(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("📦 *Управление заказами*\n\n" +
                "Функция находится в разработке.");
        sendMessage.setParseMode("Markdown");
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Список пользователей"
     * @param chatId ID чата
     * @param page номер страницы
     * @return ответ бота
     */
    private BotApiMethod<?> handleUsersList(Long chatId, int page) {
        Pageable pageable = PageRequest.of(page - 1, USERS_PAGE_SIZE);
        Page<TelegramUser> usersPage = telegramUserRepository.findAll(pageable);
        
        if (usersPage.isEmpty()) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("Список пользователей пуст.");
            return sendMessage;
        }
        
        StringBuilder messageText = new StringBuilder();
        messageText.append("👥 *Список пользователей* (страница ").append(page).append(" из ").append(usersPage.getTotalPages()).append(")\n\n");
        
        List<TelegramUser> users = usersPage.getContent();
        for (int i = 0; i < users.size(); i++) {
            TelegramUser user = users.get(i);
            messageText.append(i + 1).append(". *").append(user.getFirstName()).append(" ").append(user.getLastName() != null ? user.getLastName() : "").append("*\n");
            messageText.append("   Username: @").append(user.getUsername() != null ? user.getUsername() : "Не указан").append("\n");
            messageText.append("   Роль: ").append(user.getRole()).append("\n");
            messageText.append("   Телефон: ").append(user.getPhoneNumber() != null ? user.getPhoneNumber() : "Не указан").append("\n\n");
        }
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText.toString());
        sendMessage.setParseMode("Markdown");
        sendMessage.setReplyMarkup(keyboardFactory.createUserPaginationKeyboard(page, usersPage.getTotalPages()));
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Вернуться в главное меню"
     * @param chatId ID чата
     * @param user пользователь
     * @return ответ бота
     */
    private BotApiMethod<?> handleBackToMainMenu(Long chatId, TelegramUser user) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Вы вернулись в главное меню.");
        
        // Проверяем, является ли пользователь админом или менеджером
        boolean isAdminOrManager = "ADMIN".equals(user.getRole()) || "MANAGER".equals(user.getRole());
        sendMessage.setReplyMarkup(keyboardFactory.createMainMenuKeyboard(isAdminOrManager));
        
        return sendMessage;
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