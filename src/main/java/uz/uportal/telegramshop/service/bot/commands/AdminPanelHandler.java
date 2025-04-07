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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.model.Product;
import uz.uportal.telegramshop.model.ShopSettings;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.CategoryService;
import uz.uportal.telegramshop.service.ProductService;
import uz.uportal.telegramshop.service.ShopSettingsService;
import uz.uportal.telegramshop.service.bot.core.UpdateHandler;
import uz.uportal.telegramshop.service.bot.keyboards.KeyboardFactory;
import uz.uportal.telegramshop.service.bot.core.MessageSender;

import java.util.ArrayList;
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
    private final ShopSettingsService shopSettingsService;
    
    // Константы для размера страницы при пагинации
    private static final int PRODUCTS_PAGE_SIZE = 5;
    private static final int CATEGORIES_PAGE_SIZE = 5;
    private static final int USERS_PAGE_SIZE = 10;
    
    public AdminPanelHandler(
            TelegramUserRepository telegramUserRepository,
            KeyboardFactory keyboardFactory,
            ProductService productService,
            CategoryService categoryService,
            MessageSender messageSender,
            ShopSettingsService shopSettingsService) {
        this.telegramUserRepository = telegramUserRepository;
        this.keyboardFactory = keyboardFactory;
        this.productService = productService;
        this.categoryService = categoryService;
        this.messageSender = messageSender;
        this.shopSettingsService = shopSettingsService;
    }
    
    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }
        
        String text = update.getMessage().getText();
        boolean canHandle = text.equals("⚙️ Админ панель") || 
               text.equals("📋 Список товаров") || 
               text.equals("➕ Добавить товар") || 
               text.equals("🗂 Список категорий") || 
               text.equals("➕ Добавить категорию") || 
               text.equals("📦 Управление заказами") || 
               text.equals("👥 Список пользователей") || 
               text.equals("⚙️ Настройки магазина") ||
               text.contains("Список пользователей") ||
               text.equals("⬅️ Вернуться в главное меню");
        
        if (text.contains("пользователей")) {
            logger.info("Button text contains 'пользователей': '{}', canHandle: {}", text, canHandle);
        }
        
        return canHandle;
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
                return handleAddingProduct(chatId);
            case "🗂 Список категорий":
                return handleCategoriesList(chatId, 1);
            case "➕ Добавить категорию":
                return handleAddingCategory(chatId);
            case "📦 Управление заказами":
                return handleOrdersManagement(chatId);
            case "👥 Список пользователей":
                return handleUsersList(chatId, 1);
            case "⚙️ Настройки магазина":
                return handleShopSettings(chatId);
            case "⬅️ Вернуться в главное меню":
                return handleReturnToMainMenu(chatId);
            default:
                if (text.contains("Список пользователей")) {
                    try {
                        int page = Integer.parseInt(text.replaceAll("[^0-9]", ""));
                        return handleUsersList(chatId, page);
                    } catch (NumberFormatException e) {
                        logger.error("Ошибка при парсинге номера страницы: {}", e.getMessage());
                        return handleUsersList(chatId, 1);
                    }
                }
                return createTextMessage(chatId, "Неизвестная команда. Пожалуйста, используйте кнопки меню.");
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
    private BotApiMethod<?> handleAddingProduct(Long chatId) {
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
    private BotApiMethod<?> handleAddingCategory(Long chatId) {
        // Устанавливаем состояние пользователя "добавление категории"
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState("ADDING_CATEGORY");
            telegramUserRepository.save(user);
        }
        
        // Отправляем сообщение с инструкцией
        return createTextMessage(chatId, "Введите название новой категории:");
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
                "Выберите фильтр для просмотра заказов:");
        sendMessage.setParseMode("Markdown");
        
        // Создаем клавиатуру с кнопками фильтрации заказов
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Первый ряд кнопок
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton allButton = new InlineKeyboardButton();
        allButton.setText("Все заказы");
        allButton.setCallbackData("orders_all");
        row1.add(allButton);
        
        keyboard.add(row1);
        
        // Второй ряд кнопок
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        
        InlineKeyboardButton newButton = new InlineKeyboardButton();
        newButton.setText("🆕 Новые");
        newButton.setCallbackData("orders_new");
        row2.add(newButton);
        
        InlineKeyboardButton processingButton = new InlineKeyboardButton();
        processingButton.setText("🔄 В обработке");
        processingButton.setCallbackData("orders_processing");
        row2.add(processingButton);
        
        keyboard.add(row2);
        
        // Третий ряд кнопок
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        
        InlineKeyboardButton completedButton = new InlineKeyboardButton();
        completedButton.setText("✅ Выполненные");
        completedButton.setCallbackData("orders_completed");
        row3.add(completedButton);
        
        InlineKeyboardButton cancelledButton = new InlineKeyboardButton();
        cancelledButton.setText("❌ Отмененные");
        cancelledButton.setCallbackData("orders_cancelled");
        row3.add(cancelledButton);
        
        keyboard.add(row3);
        
        // Четвертый ряд с кнопкой возврата
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Назад в админ панель");
        backButton.setCallbackData("back_to_admin");
        row4.add(backButton);
        
        keyboard.add(row4);
        
        keyboardMarkup.setKeyboard(keyboard);
        sendMessage.setReplyMarkup(keyboardMarkup);
        
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
            
            // Экранируем спецсимволы Markdown для безопасного вывода данных пользователя
            String firstName = escapeMarkdown(user.getFirstName());
            String lastName = user.getLastName() != null ? escapeMarkdown(user.getLastName()) : "";
            String username = user.getUsername() != null ? escapeMarkdown(user.getUsername()) : "Не указан";
            String role = escapeMarkdown(user.getRole());
            String phone = user.getPhoneNumber() != null ? escapeMarkdown(user.getPhoneNumber()) : "Не указан";
            
            messageText.append(i + 1).append(". *").append(firstName).append(" ").append(lastName).append("*\n");
            messageText.append("   Username: @").append(username).append("\n");
            messageText.append("   Роль: ").append(role).append("\n");
            messageText.append("   Телефон: ").append(phone).append("\n\n");
        }
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText.toString());
        sendMessage.setParseMode("Markdown");
        sendMessage.setReplyMarkup(keyboardFactory.createUserPaginationKeyboard(page, usersPage.getTotalPages()));
        
        try {
            // Напрямую отправляем сообщение через messageSender
            messageSender.executeMessage(sendMessage);
            
            // Возвращаем пустое сообщение, чтобы избежать двойной отправки
            SendMessage emptyMessage = new SendMessage();
            emptyMessage.setChatId(chatId);
            emptyMessage.setText("");
            return emptyMessage;
        } catch (Exception e) {
            logger.error("Ошибка при отправке списка пользователей: {}", e.getMessage(), e);
            
            // В случае ошибки возвращаем сообщение об ошибке
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId);
            errorMessage.setText("Произошла ошибка при получении списка пользователей. Пожалуйста, попробуйте позже.");
            return errorMessage;
        }
    }
    
    /**
     * Экранирует специальные символы Markdown
     * @param text текст для экранирования
     * @return экранированный текст
     */
    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        // Экранируем специальные символы Markdown: * _ ` [ ]
        return text.replace("*", "\\*")
                  .replace("_", "\\_")
                  .replace("`", "\\`")
                  .replace("[", "\\[")
                  .replace("]", "\\]");
    }
    
    /**
     * Обрабатывает нажатие кнопки "Вернуться в главное меню"
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleReturnToMainMenu(Long chatId) {
        // Получаем пользователя
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        boolean isAdminOrManager = user != null && (user.getRole().equals("ADMIN") || user.getRole().equals("MANAGER"));
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Вы вернулись в главное меню.");
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
    
    /**
     * Обрабатывает кнопку "Настройки магазина"
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleShopSettings(Long chatId) {
        // Получаем текущие настройки магазина
        ShopSettings settings = shopSettingsService.getShopSettings();
        
        // Формируем сообщение
        StringBuilder messageText = new StringBuilder();
        messageText.append("⚙️ *Настройки магазина*\n\n");
        messageText.append("*Текущие настройки:*\n\n");
        messageText.append("📞 *Телефон:* ").append(settings.getPhone()).append("\n");
        messageText.append("📧 *Email:* ").append(settings.getEmail()).append("\n");
        messageText.append("🌐 *Сайт:* ").append(settings.getWebsite()).append("\n\n");
        messageText.append("*Сообщение поддержки:*\n").append(settings.getSupportInfo()).append("\n\n");
        messageText.append("*Информация о магазине:*\n").append(settings.getAboutInfo()).append("\n\n");
        messageText.append("*Режим работы:*\n").append(settings.getWorkingHours()).append("\n\n");
        messageText.append("Выберите, что хотите изменить:");
        
        // Создаем клавиатуру для выбора настроек
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопка изменения контактной информации
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton contactsButton = new InlineKeyboardButton();
        contactsButton.setText("📞 Изменить контактную информацию");
        contactsButton.setCallbackData("edit_shop_contacts");
        row1.add(contactsButton);
        keyboard.add(row1);
        
        // Кнопка изменения сообщения поддержки
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton supportButton = new InlineKeyboardButton();
        supportButton.setText("❓ Изменить сообщение поддержки");
        supportButton.setCallbackData("edit_shop_support");
        row2.add(supportButton);
        keyboard.add(row2);
        
        // Кнопка изменения информации о магазине
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton aboutButton = new InlineKeyboardButton();
        aboutButton.setText("ℹ️ Изменить информацию о магазине");
        aboutButton.setCallbackData("edit_shop_about");
        row3.add(aboutButton);
        keyboard.add(row3);
        
        // Кнопка изменения режима работы
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton workingHoursButton = new InlineKeyboardButton();
        workingHoursButton.setText("🕒 Изменить режим работы");
        workingHoursButton.setCallbackData("edit_shop_hours");
        row4.add(workingHoursButton);
        keyboard.add(row4);
        
        // Кнопка возврата в админ-панель
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Назад");
        backButton.setCallbackData("back_to_admin");
        row5.add(backButton);
        keyboard.add(row5);
        
        keyboardMarkup.setKeyboard(keyboard);
        
        // Отправляем сообщение
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText.toString());
        sendMessage.setParseMode("Markdown");
        sendMessage.setReplyMarkup(keyboardMarkup);
        
        return sendMessage;
    }
} 