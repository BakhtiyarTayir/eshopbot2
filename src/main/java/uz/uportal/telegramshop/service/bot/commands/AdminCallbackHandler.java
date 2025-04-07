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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.model.Product;
import uz.uportal.telegramshop.model.ShopSettings;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.CategoryService;
import uz.uportal.telegramshop.service.ProductService;
import uz.uportal.telegramshop.service.ShopSettingsService;
import uz.uportal.telegramshop.service.bot.core.MessageSender;
import uz.uportal.telegramshop.service.bot.core.UpdateHandler;
import uz.uportal.telegramshop.service.bot.keyboards.KeyboardFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Обработчик callback-запросов для админ-панели
 */
@Component
public class AdminCallbackHandler implements UpdateHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminCallbackHandler.class);
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
    
    public AdminCallbackHandler(
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
        if (!update.hasCallbackQuery()) {
            return false;
        }
        
        String callbackData = update.getCallbackQuery().getData();
        
        return callbackData.startsWith("admin_") ||
               callbackData.startsWith("shop_") ||
               callbackData.startsWith("products_") ||
               callbackData.startsWith("categories_") ||
               callbackData.startsWith("user_") ||
               callbackData.startsWith("edit_product_") ||
               callbackData.startsWith("delete_product_") ||
               callbackData.startsWith("edit_category_") ||
               callbackData.startsWith("delete_category_") ||
               callbackData.startsWith("confirm_delete_category_") ||
               callbackData.startsWith("edit_shop_") ||
               callbackData.startsWith("change_user_role") ||
               callbackData.equals("add_manager") ||
               callbackData.equals("back_to_admin");
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
        
        // Проверяем права доступа
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user == null || (!"ADMIN".equals(user.getRole()) && !"MANAGER".equals(user.getRole()))) {
            return createTextMessage(chatId, "У вас нет доступа к административным функциям.");
        }
        
        try {
            if (callbackData.equals("admin_products")) {
                return createTextMessage(chatId, "Управление товарами временно недоступно.");
            } else if (callbackData.equals("admin_categories")) {
                return createTextMessage(chatId, "Управление категориями временно недоступно.");
            } else if (callbackData.equals("admin_orders")) {
                return createTextMessage(chatId, "Функция управления заказами находится в разработке.");
            } else if (callbackData.equals("admin_users")) {
                return createTextMessage(chatId, "Управление пользователями временно недоступно.");
            } else if (callbackData.equals("admin_settings")) {
                return handleAdminSettings(chatId, messageId);
            } else if (callbackData.startsWith("products_page_")) {
                return handleProductsPage(chatId, messageId, callbackData);
            } else if (callbackData.equals("add_product")) {
                return createTextMessage(chatId, "Добавление товаров временно недоступно.");
            } else if (callbackData.startsWith("edit_product_")) {
                Long productId = Long.parseLong(callbackData.replace("edit_product_", ""));
                return handleEditProduct(chatId, messageId, productId);
            } else if (callbackData.startsWith("delete_product_")) {
                Long productId = Long.parseLong(callbackData.replace("delete_product_", ""));
                return handleDeleteProduct(chatId, messageId, productId);
            } else if (callbackData.equals("add_category")) {
                return createTextMessage(chatId, "Добавление категорий временно недоступно.");
            } else if (callbackData.startsWith("categories_page_")) {
                return handleCategoriesPage(chatId, messageId, callbackData);
            } else if (callbackData.startsWith("edit_category_")) {
                Long categoryId = Long.parseLong(callbackData.replace("edit_category_", ""));
                return handleEditCategory(chatId, messageId, categoryId);
            } else if (callbackData.startsWith("delete_category_")) {
                Long categoryId = Long.parseLong(callbackData.replace("delete_category_", ""));
                logger.info("Обрабатываем колбэк delete_category_{} для чата {}", categoryId, chatId);
                return handleDeleteCategory(chatId, messageId, categoryId);
            } else if (callbackData.startsWith("user_details_")) {
                return createTextMessage(chatId, "Просмотр деталей пользователя временно недоступен.");
            } else if (callbackData.startsWith("users_page_")) {
                return handleUsersPage(chatId, messageId, callbackData);
            } else if (callbackData.equals("shop_settings")) {
                return handleShopSettings(chatId, messageId);
            } else if (callbackData.equals("edit_shop_contacts")) {
                return handleEditShopSettings(chatId, messageId, callbackData);
            } else if (callbackData.equals("edit_shop_hours")) {
                return handleEditShopSettings(chatId, messageId, callbackData);
            } else if (callbackData.equals("edit_shop_about")) {
                return handleEditShopSettings(chatId, messageId, callbackData);
            } else if (callbackData.equals("edit_shop_support")) {
                return handleEditShopSettings(chatId, messageId, callbackData);
            } else if (callbackData.equals("back_to_admin")) {
                return handleBackToAdmin(chatId, messageId);
            } else if (callbackData.equals("change_user_role")) {
                return handleChangeUserRole(chatId, messageId);
            } else if (callbackData.equals("add_manager")) {
                return handleAddManager(chatId, messageId);
            } else if (callbackData.startsWith("confirm_delete_category_")) {
                Long categoryId = Long.parseLong(callbackData.replace("confirm_delete_category_", ""));
                logger.info("Обрабатываем колбэк confirm_delete_category_{} для чата {}", categoryId, chatId);
                return handleConfirmDeleteCategory(chatId, messageId, categoryId);
            }
            
            logger.warn("Необработанный колбэк: {}", callbackData);
            return null;
        } catch (Exception e) {
            logger.error("Error handling admin callback: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при обработке запроса: " + e.getMessage());
        }
    }
    
    /**
     * Обрабатывает запросы на редактирование настроек магазина
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditShopSettings(Long chatId, Integer messageId, String callbackData) {
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user == null) {
            return createTextMessage(chatId, "Пользователь не найден");
        }
        
        switch (callbackData) {
            case "edit_shop_contacts":
                user.setState("EDITING_SHOP_CONTACTS");
                telegramUserRepository.save(user);
                
                ShopSettings settings = shopSettingsService.getShopSettings();
                
                EditMessageText editMessage = new EditMessageText();
                editMessage.setChatId(chatId);
                editMessage.setMessageId(messageId);
                editMessage.setText("📞 *Изменение контактной информации*\n\n" + 
                        "*Текущие значения:*\n" +
                        "Телефон: " + settings.getPhone() + "\n" +
                        "Email: " + settings.getEmail() + "\n" +
                        "Сайт: " + settings.getWebsite() + "\n\n" +
                        "Введите новую контактную информацию в формате:\n" +
                        "*Телефон|Email|Сайт*\n\n" +
                        "Например: `+7 (999) 123-45-67|info@example.com|www.example.com`");
                editMessage.setParseMode("Markdown");
                
                // Добавляем кнопку отмены
                addCancelButton(editMessage);
                
                // Отправляем сообщение
                return editMessage;
                
            case "edit_shop_support":
                user.setState("EDITING_SHOP_SUPPORT");
                telegramUserRepository.save(user);
                
                ShopSettings supportSettings = shopSettingsService.getShopSettings();
                
                EditMessageText supportMessage = new EditMessageText();
                supportMessage.setChatId(chatId);
                supportMessage.setMessageId(messageId);
                supportMessage.setText("❓ *Изменение сообщения поддержки*\n\n" + 
                        "*Текущее значение:*\n" +
                        supportSettings.getSupportInfo() + "\n\n" +
                        "Введите новое сообщение поддержки:");
                supportMessage.setParseMode("Markdown");
                
                // Добавляем кнопку отмены
                addCancelButton(supportMessage);
                
                // Отправляем сообщение
                return supportMessage;
                
            case "edit_shop_about":
                user.setState("EDITING_SHOP_ABOUT");
                telegramUserRepository.save(user);
                
                ShopSettings aboutSettings = shopSettingsService.getShopSettings();
                
                EditMessageText aboutMessage = new EditMessageText();
                aboutMessage.setChatId(chatId);
                aboutMessage.setMessageId(messageId);
                aboutMessage.setText("ℹ️ *Изменение информации о магазине*\n\n" + 
                        "*Текущее значение:*\n" +
                        aboutSettings.getAboutInfo() + "\n\n" +
                        "Введите новую информацию о магазине:");
                aboutMessage.setParseMode("Markdown");
                
                // Добавляем кнопку отмены
                addCancelButton(aboutMessage);
                
                // Отправляем сообщение
                return aboutMessage;
                
            case "edit_shop_hours":
                user.setState("EDITING_SHOP_HOURS");
                telegramUserRepository.save(user);
                
                ShopSettings hoursSettings = shopSettingsService.getShopSettings();
                
                EditMessageText hoursMessage = new EditMessageText();
                hoursMessage.setChatId(chatId);
                hoursMessage.setMessageId(messageId);
                hoursMessage.setText("🕒 *Изменение режима работы*\n\n" + 
                        "*Текущее значение:*\n" +
                        hoursSettings.getWorkingHours().replace("\n", "\\n") + "\n\n" +
                        "Введите новый режим работы (используйте \\n для переноса строки):");
                hoursMessage.setParseMode("Markdown");
                
                // Добавляем кнопку отмены
                addCancelButton(hoursMessage);
                
                // Отправляем сообщение
                return hoursMessage;
                
            default:
                logger.warn("Unknown shop settings callback: {}", callbackData);
                return createTextMessage(chatId, "Неизвестная команда");
        }
    }
    
    /**
     * Добавляет кнопку отмены редактирования к сообщению
     * @param message сообщение, к которому нужно добавить кнопку
     */
    private void addCancelButton(EditMessageText message) {
        InlineKeyboardMarkup cancelKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> cancelButtons = new ArrayList<>();
        List<InlineKeyboardButton> cancelRow = new ArrayList<>();
        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отмена редактирования");
        cancelButton.setCallbackData("shop_settings");
        cancelRow.add(cancelButton);
        cancelButtons.add(cancelRow);
        cancelKeyboard.setKeyboard(cancelButtons);
        message.setReplyMarkup(cancelKeyboard);
    }
    
    /**
     * Обрабатывает нажатие на кнопку "Назад" в настройках магазина
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @return ответ бота
     */
    private BotApiMethod<?> handleBackToAdmin(Long chatId, Integer messageId) {
        if (messageId == null) {
            // Если messageId не доступен, отправляем новое сообщение
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("⚙️ *Панель администратора*\n\n" +
                    "Выберите действие из меню ниже:");
            sendMessage.setParseMode("Markdown");
            sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
            
            return sendMessage;
        } else {
            // Если messageId доступен, редактируем существующее сообщение
            EditMessageText editMessageText = new EditMessageText();
            editMessageText.setChatId(chatId);
            editMessageText.setMessageId(messageId);
            editMessageText.setText("⚙️ *Панель администратора*\n\n" +
                    "Выберите действие из меню ниже:");
            editMessageText.setParseMode("Markdown");
            
            return editMessageText;
        }
    }
    
    /**
     * Обрабатывает нажатие кнопки "Редактировать товар"
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param productId ID товара
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditProduct(Long chatId, Integer messageId, Long productId) {
        Optional<Product> productOpt = productService.getProductById(productId);
        
        if (productOpt.isEmpty()) {
            return createTextMessage(chatId, "Товар не найден.");
        }
        
        Product product = productOpt.get();
        
        // Устанавливаем состояние пользователя для редактирования товара
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user == null) {
            user = new TelegramUser(chatId, null, "Unknown", "Unknown");
            logger.warn("Пользователь с ID {} не найден, создан новый пользователь", chatId);
        }
        user.setState("EDITING_PRODUCT_" + productId);
        telegramUserRepository.save(user);
        
        logger.info("Установлено состояние EDITING_PRODUCT_{} для пользователя {}", productId, chatId);
        
        StringBuilder messageText = new StringBuilder();
        messageText.append("✏️ *Редактирование товара*\n\n");
        messageText.append("Выберите, что вы хотите изменить:\n\n");
        messageText.append("1. Название: ").append(product.getName()).append("\n");
        messageText.append("2. Цена: ").append(product.getPrice()).append(" руб.\n");
        messageText.append("3. Количество: ").append(product.getStock()).append(" шт.\n");
        messageText.append("4. Категория: ").append(product.getCategory() != null ? product.getCategory().getName() : "Не указана").append("\n");
        messageText.append("5. Описание: ").append(product.getDescription()).append("\n");
        messageText.append("6. Изображение\n");
        messageText.append("7. Удалить товар\n");
        messageText.append("8. Сохранить и выйти\n\n");
        messageText.append("Введите номер поля, которое хотите изменить, или 8 для сохранения и выхода:");
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText.toString());
        sendMessage.setParseMode("Markdown");
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Удалить товар" с использованием EditMessageText
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param productId ID товара
     * @return ответ бота
     */
    private BotApiMethod<?> handleDeleteProduct(Long chatId, Integer messageId, Long productId) {
        boolean deleted = productService.deleteProduct(productId);
        
        // Вместо редактирования сообщения, отправим новое сообщение
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        
        if (deleted) {
            sendMessage.setText("✅ Товар успешно удален.");
        } else {
            sendMessage.setText("❌ Не удалось удалить товар. Возможно, он уже был удален.");
        }
        
        // Добавляем клавиатуру админ-панели
        sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
        
        // Также удалим сообщение с товаром
        try {
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(chatId);
            deleteMessage.setMessageId(messageId);
            messageSender.executeDeleteMessage(deleteMessage);
        } catch (Exception e) {
            logger.error("Ошибка при удалении сообщения: {}", e.getMessage());
        }
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Удалить товар" с использованием SendMessage
     * @param chatId ID чата
     * @param productId ID товара
     * @return ответ бота
     */
    private BotApiMethod<?> handleDeleteProduct(Long chatId, Long productId) {
        boolean deleted = productService.deleteProduct(productId);
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        
        if (deleted) {
            sendMessage.setText("✅ Товар успешно удален.");
        } else {
            sendMessage.setText("❌ Не удалось удалить товар. Возможно, он уже был удален.");
        }
        
        // Добавляем клавиатуру админ-панели
        sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Редактировать категорию"
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param categoryId ID категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditCategory(Long chatId, Integer messageId, Long categoryId) {
        Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
        
        if (categoryOpt.isEmpty()) {
            return createTextMessage(chatId, "Категория не найдена.");
        }
        
        Category category = categoryOpt.get();
        
        // Сохраняем ID категории в состоянии пользователя
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState("EDITING_CATEGORY_NAME");
            user.setTempData(categoryId.toString()); // Сохраняем ID редактируемой категории
            telegramUserRepository.save(user);
        }
        
        StringBuilder messageText = new StringBuilder();
        messageText.append("✏️ *Редактирование категории*\n\n");
        messageText.append("Текущие данные категории:\n");
        messageText.append("Название: ").append(category.getName()).append("\n");
        messageText.append("Описание: ").append(category.getDescription() != null ? category.getDescription() : "Не указано").append("\n\n");
        messageText.append("Введите новое название категории:");
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText.toString());
        sendMessage.setParseMode("Markdown");
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Удалить категорию" с использованием EditMessageText
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param categoryId ID категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleDeleteCategory(Long chatId, Integer messageId, Long categoryId) {
        logger.info("Начинаем обработку запроса на удаление категории с ID={}", categoryId);
        
        Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
        if (categoryOpt.isEmpty()) {
            logger.warn("Категория с ID={} не найдена при попытке удаления", categoryId);
            // Если категория не найдена, отправляем сообщение об ошибке
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("❌ Категория не найдена. Возможно, она уже была удалена.");
            sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
            
            return sendMessage;
        }
        
        Category category = categoryOpt.get();
        logger.info("Найдена категория для удаления: ID={}, Имя='{}'", category.getId(), category.getName());
        
        // Создаем сообщение с запросом подтверждения
        EditMessageText editMessageText = new EditMessageText();
        editMessageText.setChatId(chatId);
        editMessageText.setMessageId(messageId);
        editMessageText.setText("❓ Вы действительно хотите удалить категорию \"*" + category.getName() + "*\"?");
        editMessageText.setParseMode("Markdown");
        
        // Создаем клавиатуру с кнопками подтверждения и отмены
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопки подтверждения и отмены
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("✅ Да, удалить");
        confirmButton.setCallbackData("confirm_delete_category_" + categoryId);
        row.add(confirmButton);
        
        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отмена");
        cancelButton.setCallbackData("categories_page_1"); // Возвращаемся к списку категорий
        row.add(cancelButton);
        
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        
        editMessageText.setReplyMarkup(keyboardMarkup);
        
        logger.info("Подготовлено сообщение с запросом подтверждения удаления категории ID={}", categoryId);
        return editMessageText;
    }
    
    /**
     * Обрабатывает пагинацию списка товаров с использованием EditMessageText
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleProductsPage(Long chatId, Integer messageId, String callbackData) {
        logger.info("Handling products page with messageId: {}", messageId);
        
        // Извлекаем номер страницы из callback data
        int page = 1;
        if (callbackData.startsWith("products_page_")) {
            try {
                page = Integer.parseInt(callbackData.replace("products_page_", ""));
            } catch (NumberFormatException e) {
                logger.error("Ошибка при парсинге номера страницы: {}", e.getMessage());
            }
        }
        
        // Получаем страницу товаров
        Pageable pageable = PageRequest.of(page - 1, PRODUCTS_PAGE_SIZE);
        Page<Product> productsPage = productService.getAllProducts(pageable);
        
        // Если список товаров пуст, обновляем сообщение
        if (productsPage.isEmpty()) {
            EditMessageText editMessageText = new EditMessageText();
            editMessageText.setChatId(chatId);
            editMessageText.setMessageId(messageId);
            editMessageText.setText("Список товаров пуст. Добавьте товары, нажав на кнопку '➕ Добавить товар'.");
            return editMessageText;
        }
        
        // Обновляем сообщение с заголовком
        EditMessageText editMessageText = new EditMessageText();
        editMessageText.setChatId(chatId);
        editMessageText.setMessageId(messageId);
        editMessageText.setText("📋 *Список товаров* (страница " + page + " из " + productsPage.getTotalPages() + ")\n\n" +
                "Товары будут отправлены отдельными сообщениями.");
        editMessageText.setParseMode("Markdown");
        
        try {
            // Отправляем обновленный заголовок
            messageSender.executeEditMessage(editMessageText);
            
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
     * Обрабатывает пагинацию списка товаров с использованием SendMessage
     * @param chatId ID чата
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleProductsPage(Long chatId, String callbackData) {
        int page = Integer.parseInt(callbackData.replace("products_page_", ""));
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
     * Обрабатывает пагинацию списка категорий с использованием EditMessageText
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleCategoriesPage(Long chatId, Integer messageId, String callbackData) {
        int page = Integer.parseInt(callbackData.replace("categories_page_", ""));
        Pageable pageable = PageRequest.of(page - 1, CATEGORIES_PAGE_SIZE);
        Page<Category> categoriesPage = categoryService.getAllCategories(pageable);
        
        StringBuilder messageText = new StringBuilder();
        messageText.append("🗂 *Список категорий* (страница ").append(page).append(" из ").append(categoriesPage.getTotalPages()).append(")\n\n");
        
        List<Category> categories = categoriesPage.getContent();
        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            messageText.append(i + 1).append(". *").append(category.getName()).append("*\n");
            messageText.append("   Описание: ").append(category.getDescription() != null ? category.getDescription() : "Не указано").append("\n\n");
        }
        
        if (categories.isEmpty()) {
            messageText.append("Список категорий пуст.");
        }
        
        EditMessageText editMessageText = new EditMessageText();
        editMessageText.setChatId(chatId);
        editMessageText.setMessageId(messageId);
        editMessageText.setText(messageText.toString());
        editMessageText.setParseMode("Markdown");
        editMessageText.setReplyMarkup(keyboardFactory.createCategoryPaginationKeyboard(page, categoriesPage.getTotalPages(), categories));
        
        return editMessageText;
    }
    
    /**
     * Обрабатывает пагинацию списка категорий с использованием SendMessage
     * @param chatId ID чата
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleCategoriesPage(Long chatId, String callbackData) {
        int page = Integer.parseInt(callbackData.replace("categories_page_", ""));
        Pageable pageable = PageRequest.of(page - 1, CATEGORIES_PAGE_SIZE);
        Page<Category> categoriesPage = categoryService.getAllCategories(pageable);
        
        StringBuilder messageText = new StringBuilder();
        messageText.append("🗂 *Список категорий* (страница ").append(page).append(" из ").append(categoriesPage.getTotalPages()).append(")\n\n");
        
        List<Category> categories = categoriesPage.getContent();
        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            messageText.append(i + 1).append(". *").append(category.getName()).append("*\n");
            messageText.append("   Описание: ").append(category.getDescription() != null ? category.getDescription() : "Не указано").append("\n\n");
        }
        
        if (categories.isEmpty()) {
            messageText.append("Список категорий пуст.");
        }
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText.toString());
        sendMessage.setParseMode("Markdown");
        sendMessage.setReplyMarkup(keyboardFactory.createCategoryPaginationKeyboard(page, categoriesPage.getTotalPages(), categories));
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает пагинацию списка пользователей с использованием EditMessageText
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleUsersPage(Long chatId, Integer messageId, String callbackData) {
        int page = Integer.parseInt(callbackData.replace("users_page_", ""));
        Pageable pageable = PageRequest.of(page - 1, USERS_PAGE_SIZE);
        Page<TelegramUser> usersPage = telegramUserRepository.findAll(pageable);
        
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
        
        EditMessageText editMessageText = new EditMessageText();
        editMessageText.setChatId(chatId);
        editMessageText.setMessageId(messageId);
        editMessageText.setText(messageText.toString());
        editMessageText.setParseMode("Markdown");
        editMessageText.setReplyMarkup(keyboardFactory.createUserPaginationKeyboard(page, usersPage.getTotalPages()));
        
        return editMessageText;
    }
    
    /**
     * Обрабатывает пагинацию списка пользователей с использованием SendMessage
     * @param chatId ID чата
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleUsersPage(Long chatId, String callbackData) {
        int page = Integer.parseInt(callbackData.replace("users_page_", ""));
        Pageable pageable = PageRequest.of(page - 1, USERS_PAGE_SIZE);
        Page<TelegramUser> usersPage = telegramUserRepository.findAll(pageable);
        
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
     * Обрабатывает подтверждение удаления категории с использованием EditMessageText
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param categoryId ID категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleConfirmDeleteCategory(Long chatId, Integer messageId, Long categoryId) {
        logger.info("Начинаем удаление категории с ID={}", categoryId);
        
        // Также сначала удалим сообщение с запросом подтверждения
        try {
            logger.info("Удаляем сообщение с запросом подтверждения");
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(chatId);
            deleteMessage.setMessageId(messageId);
            messageSender.executeDeleteMessage(deleteMessage);
            logger.info("Сообщение с запросом подтверждения успешно удалено");
        } catch (Exception e) {
            logger.error("Ошибка при удалении сообщения с запросом подтверждения: {}", e.getMessage(), e);
        }
        
        // Затем выполняем удаление категории
        CategoryService.DeleteResult result = categoryService.deleteCategoryWithResult(categoryId);
        logger.info("Результат удаления категории: успех={}, сообщение='{}'", result.isSuccess(), result.getMessage());
        
        // Отправляем сообщение с результатом
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        
        if (result.isSuccess()) {
            sendMessage.setText("✅ " + result.getMessage());
        } else {
            sendMessage.setText("❌ " + result.getMessage());
        }
        
        // Добавляем клавиатуру админ-панели
        sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
        
        // Отправляем сообщение напрямую
        try {
            logger.info("Отправляем сообщение с результатом удаления категории: {}", sendMessage.getText());
            messageSender.executeMessage(sendMessage);
            logger.info("Сообщение успешно отправлено");
        } catch (Exception e) {
            logger.error("Ошибка при отправке сообщения с результатом: {}", e.getMessage(), e);
        }
        
        // Возвращаем пустое сообщение, чтобы предотвратить повторную отправку ответа
        return null;
    }
    
    /**
     * Обрабатывает подтверждение удаления категории с использованием SendMessage
     * @param chatId ID чата
     * @param categoryId ID категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleConfirmDeleteCategory(Long chatId, Long categoryId) {
        logger.info("Начинаем удаление категории с ID={} (без messageId)", categoryId);
        
        // Выполняем удаление категории
        CategoryService.DeleteResult result = categoryService.deleteCategoryWithResult(categoryId);
        logger.info("Результат удаления категории: успех={}, сообщение='{}'", result.isSuccess(), result.getMessage());
        
        // Отправляем сообщение с результатом
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        
        if (result.isSuccess()) {
            sendMessage.setText("✅ " + result.getMessage());
        } else {
            sendMessage.setText("❌ " + result.getMessage());
        }
        
        // Добавляем клавиатуру админ-панели
        sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
        
        // Отправляем сообщение напрямую
        try {
            logger.info("Отправляем сообщение с результатом удаления категории: {}", sendMessage.getText());
            messageSender.executeMessage(sendMessage);
            logger.info("Сообщение успешно отправлено");
        } catch (Exception e) {
            logger.error("Ошибка при отправке сообщения с результатом: {}", e.getMessage(), e);
        }
        
        // Возвращаем пустое сообщение, чтобы предотвратить повторную отправку ответа
        return null;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Редактировать товар" с использованием SendMessage
     * @param chatId ID чата
     * @param productId ID товара
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditProduct(Long chatId, Long productId) {
        Optional<Product> productOpt = productService.getProductById(productId);
        
        if (productOpt.isEmpty()) {
            return createTextMessage(chatId, "Товар не найден.");
        }
        
        Product product = productOpt.get();
        
        StringBuilder messageText = new StringBuilder();
        messageText.append("✏️ *Редактирование товара*\n\n");
        messageText.append("*Текущие данные:*\n");
        messageText.append("Название: ").append(product.getName()).append("\n");
        messageText.append("Описание: ").append(product.getDescription()).append("\n");
        messageText.append("Цена: ").append(product.getPrice()).append("\n");
        messageText.append("Остаток: ").append(product.getStock()).append("\n");
        messageText.append("Категория: ").append(product.getCategory().getName()).append("\n\n");
        messageText.append("Введите новые данные товара в формате:\n");
        messageText.append("*Название|Описание|Цена|Остаток|ID_категории*\n\n");
        messageText.append("Например: `Ноутбук Dell XPS 13|Мощный и легкий ноутбук|95000|10|3`\n\n");
        messageText.append("Если вы хотите оставить какое-то поле без изменений, введите его текущее значение.");
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText.toString());
        sendMessage.setParseMode("Markdown");
        
        return sendMessage;
    }

    /**
     * Обрабатывает нажатие кнопки "Редактировать категорию" с использованием SendMessage
     * @param chatId ID чата
     * @param categoryId ID категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditCategory(Long chatId, Long categoryId) {
        Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
        
        if (categoryOpt.isEmpty()) {
            return createTextMessage(chatId, "Категория не найдена.");
        }
        
        Category category = categoryOpt.get();
        
        StringBuilder messageText = new StringBuilder();
        messageText.append("✏️ *Редактирование категории*\n\n");
        messageText.append("*Текущие данные:*\n");
        messageText.append("Название: ").append(category.getName()).append("\n");
        if (category.getDescription() != null) {
            messageText.append("Описание: ").append(category.getDescription()).append("\n");
        }
        messageText.append("Родительская категория: ");
        if (category.getParent() != null) {
            messageText.append(category.getParent().getName()).append(" (ID: ").append(category.getParent().getId()).append(")");
        } else {
            messageText.append("Нет (основная категория)");
        }
        messageText.append("\n\n");
        messageText.append("Введите новые данные категории в формате:\n");
        messageText.append("*Название|Описание|ID_родительской_категории*\n\n");
        messageText.append("Например: `Смартфоны|Мобильные телефоны|1`\n\n");
        messageText.append("Если вы хотите оставить поле без изменений, введите его текущее значение.\n");
        messageText.append("Если родительской категории нет (основная категория), введите 0 в поле ID_родительской_категории.");
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText.toString());
        sendMessage.setParseMode("Markdown");
        
        return sendMessage;
    }

    /**
     * Обрабатывает нажатие кнопки "Удалить категорию" с использованием SendMessage
     * @param chatId ID чата
     * @param categoryId ID категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleDeleteCategory(Long chatId, Long categoryId) {
        // Получаем категорию
        Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
        if (categoryOpt.isEmpty()) {
            // Если категория не найдена, отправляем сообщение об ошибке
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("❌ Категория не найдена. Возможно, она уже была удалена.");
            sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
            
            return sendMessage;
        }
        
        Category category = categoryOpt.get();
        
        // Создаем сообщение с запросом подтверждения
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("❓ Вы действительно хотите удалить категорию \"*" + category.getName() + "*\"?");
        sendMessage.setParseMode("Markdown");
        
        // Создаем клавиатуру с кнопками подтверждения и отмены
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопки подтверждения и отмены
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("✅ Да, удалить");
        confirmButton.setCallbackData("confirm_delete_category_" + categoryId);
        row.add(confirmButton);
        
        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отмена");
        cancelButton.setCallbackData("categories_page_1"); // Возвращаемся к списку категорий
        row.add(cancelButton);
        
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        
        sendMessage.setReplyMarkup(keyboardMarkup);
        
        return sendMessage;
    }

    /**
     * Обрабатывает ошибку с редактированием сообщения
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param errorText текст ошибки
     * @return ответ бота
     */
    private BotApiMethod<?> handleErrorWithEdit(Long chatId, Integer messageId, String errorText) {
        EditMessageText editMessageText = new EditMessageText();
        editMessageText.setChatId(chatId);
        editMessageText.setMessageId(messageId);
        editMessageText.setText("❌ " + errorText);
        
        return editMessageText;
    }

    /**
     * Обрабатывает кнопку для изменения роли пользователя
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @return ответ бота
     */
    private BotApiMethod<?> handleChangeUserRole(Long chatId, Integer messageId) {
        // Получаем пользователя и меняем его состояние
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user == null) {
            return createTextMessage(chatId, "Пользователь не найден");
        }
        
        // Проверяем права доступа
        if (!"ADMIN".equals(user.getRole())) {
            return createTextMessage(chatId, "У вас нет прав на изменение ролей пользователей");
        }
        
        // Устанавливаем состояние пользователя
        user.setState("CHANGING_USER_ROLE");
        telegramUserRepository.save(user);
        
        // Создаем сообщение с инструкциями
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(chatId);
        editMessage.setMessageId(messageId);
        editMessage.setText("🔄 *Изменение роли пользователя*\n\n" +
                "Введите ID пользователя и новую роль в формате:\n" +
                "*chatId|role*\n\n" +
                "Например: `123456789|MANAGER`\n\n" +
                "Возможные роли: `USER`, `MANAGER`, `ADMIN`");
        editMessage.setParseMode("Markdown");
        
        return editMessage;
    }
    
    /**
     * Обрабатывает кнопку для добавления менеджера
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddManager(Long chatId, Integer messageId) {
        // Получаем пользователя и меняем его состояние
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user == null) {
            return createTextMessage(chatId, "Пользователь не найден");
        }
        
        // Проверяем права доступа
        if (!"ADMIN".equals(user.getRole())) {
            return createTextMessage(chatId, "У вас нет прав на добавление менеджеров");
        }
        
        // Устанавливаем состояние пользователя
        user.setState("ADDING_MANAGER");
        telegramUserRepository.save(user);
        
        // Создаем сообщение с инструкциями
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(chatId);
        editMessage.setMessageId(messageId);
        editMessage.setText("➕ *Добавление нового менеджера*\n\n" +
                "Введите ID пользователя в Telegram и его имя в формате:\n" +
                "*chatId|firstName|lastName*\n\n" +
                "Например: `123456789|Иван|Иванов`\n\n" +
                "Фамилия (lastName) не обязательна.");
        editMessage.setParseMode("Markdown");
        
        return editMessage;
    }
    
    /**
     * Обрабатывает настройки для администратора
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @return ответ бота
     */
    private BotApiMethod<?> handleAdminSettings(Long chatId, Integer messageId) {
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(chatId);
        editMessage.setMessageId(messageId);
        editMessage.setText("⚙️ *Настройки администратора*\n\n" +
                "Выберите действие из списка ниже:");
        editMessage.setParseMode("Markdown");
        
        // Создаем клавиатуру
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопка настроек магазина
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton shopSettingsButton = new InlineKeyboardButton();
        shopSettingsButton.setText("🏪 Настройки магазина");
        shopSettingsButton.setCallbackData("shop_settings");
        row1.add(shopSettingsButton);
        keyboard.add(row1);
        
        // Кнопка изменения роли пользователя
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton changeRoleButton = new InlineKeyboardButton();
        changeRoleButton.setText("🔄 Изменить роль пользователя");
        changeRoleButton.setCallbackData("change_user_role");
        row2.add(changeRoleButton);
        keyboard.add(row2);
        
        // Кнопка добавления менеджера
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton addManagerButton = new InlineKeyboardButton();
        addManagerButton.setText("➕ Добавить менеджера");
        addManagerButton.setCallbackData("add_manager");
        row3.add(addManagerButton);
        keyboard.add(row3);
        
        // Кнопка возврата
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Назад");
        backButton.setCallbackData("back_to_admin");
        row4.add(backButton);
        keyboard.add(row4);
        
        keyboardMarkup.setKeyboard(keyboard);
        editMessage.setReplyMarkup(keyboardMarkup);
        
        return editMessage;
    }

    /**
     * Обрабатывает настройки магазина
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @return ответ бота
     */
    private BotApiMethod<?> handleShopSettings(Long chatId, Integer messageId) {
        // Получаем текущие настройки магазина
        ShopSettings settings = shopSettingsService.getShopSettings();
        
        // Сбрасываем состояние пользователя при возврате к настройкам магазина
        // Это нужно для корректной работы кнопки "Отмена редактирования"
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState(null);
            user.setTempData(null);
            telegramUserRepository.save(user);
        }
        
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
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(chatId);
        editMessage.setMessageId(messageId);
        editMessage.setText(messageText.toString());
        editMessage.setParseMode("Markdown");
        editMessage.setReplyMarkup(keyboardMarkup);
        
        return editMessage;
    }
}