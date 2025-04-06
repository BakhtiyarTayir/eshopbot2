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
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.CategoryService;
import uz.uportal.telegramshop.service.ProductService;
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
    
    // Константы для размера страницы при пагинации
    private static final int PRODUCTS_PAGE_SIZE = 5;
    private static final int CATEGORIES_PAGE_SIZE = 5;
    private static final int USERS_PAGE_SIZE = 10;
    
    public AdminCallbackHandler(
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
        if (!update.hasCallbackQuery()) {
            return false;
        }
        
        String callbackData = update.getCallbackQuery().getData();
        return callbackData.startsWith("edit_product_") || 
               callbackData.startsWith("delete_product_") || 
               callbackData.startsWith("edit_category_") || 
               callbackData.startsWith("delete_category_") || 
               callbackData.startsWith("confirm_delete_category_") || 
               callbackData.startsWith("products_page_") || 
               callbackData.startsWith("categories_page_") || 
               callbackData.startsWith("users_page_") || 
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
        
        logger.info("Handling callback: {} for chatId: {}", callbackData, chatId);
        
        try {
            if (callbackData.startsWith("edit_product_")) {
                return handleEditProduct(chatId, callbackData);
            } else if (callbackData.startsWith("delete_product_")) {
                return messageId != null 
                    ? handleDeleteProduct(chatId, messageId, callbackData)
                    : handleDeleteProduct(chatId, callbackData);
            } else if (callbackData.startsWith("edit_category_")) {
                return handleEditCategory(chatId, callbackData);
            } else if (callbackData.startsWith("delete_category_")) {
                return messageId != null 
                    ? handleDeleteCategory(chatId, messageId, callbackData)
                    : handleDeleteCategory(chatId, callbackData);
            } else if (callbackData.startsWith("confirm_delete_category_")) {
                return messageId != null 
                    ? handleConfirmDeleteCategory(chatId, messageId, callbackData)
                    : handleConfirmDeleteCategory(chatId, callbackData);
            } else if (callbackData.startsWith("products_page_")) {
                return messageId != null 
                    ? handleProductsPage(chatId, messageId, callbackData)
                    : handleProductsPage(chatId, callbackData);
            } else if (callbackData.startsWith("categories_page_")) {
                return messageId != null 
                    ? handleCategoriesPage(chatId, messageId, callbackData)
                    : handleCategoriesPage(chatId, callbackData);
            } else if (callbackData.startsWith("users_page_")) {
                return messageId != null 
                    ? handleUsersPage(chatId, messageId, callbackData)
                    : handleUsersPage(chatId, callbackData);
            } else if (callbackData.equals("back_to_admin")) {
                return handleBackToAdmin(chatId);
            } else {
                logger.warn("Unhandled callback: {}", callbackData);
                return null;
            }
        } catch (Exception e) {
            logger.error("Error handling callback: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при обработке запроса. Пожалуйста, попробуйте еще раз.");
        }
    }
    
    /**
     * Обрабатывает нажатие кнопки "Редактировать товар"
     * @param chatId ID чата
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditProduct(Long chatId, String callbackData) {
        Long productId = Long.parseLong(callbackData.replace("edit_product_", ""));
        Optional<Product> productOpt = productService.getProductById(productId);
        
        if (productOpt.isEmpty()) {
            return createTextMessage(chatId, "Товар не найден.");
        }
        
        Product product = productOpt.get();
        
        // Устанавливаем состояние пользователя для редактирования товара
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState("EDITING_PRODUCT_" + productId);
            telegramUserRepository.save(user);
        }
        
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
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleDeleteProduct(Long chatId, Integer messageId, String callbackData) {
        Long productId = Long.parseLong(callbackData.replace("delete_product_", ""));
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
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleDeleteProduct(Long chatId, String callbackData) {
        Long productId = Long.parseLong(callbackData.replace("delete_product_", ""));
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
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditCategory(Long chatId, String callbackData) {
        Long categoryId = Long.parseLong(callbackData.replace("edit_category_", ""));
        Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
        
        if (categoryOpt.isEmpty()) {
            return createTextMessage(chatId, "Категория не найдена.");
        }
        
        // Устанавливаем состояние пользователя для редактирования категории
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState("EDITING_CATEGORY_" + categoryId);
            telegramUserRepository.save(user);
        }
        
        Category category = categoryOpt.get();
        
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
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleDeleteCategory(Long chatId, Integer messageId, String callbackData) {
        Long categoryId = Long.parseLong(callbackData.replace("delete_category_", ""));
        
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
        
        return editMessageText;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Удалить категорию" с использованием SendMessage
     * @param chatId ID чата
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleDeleteCategory(Long chatId, String callbackData) {
        Long categoryId = Long.parseLong(callbackData.replace("delete_category_", ""));
        
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
     * Обрабатывает нажатие кнопки "Назад в админ-панель"
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleBackToAdmin(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("⚙️ *Панель администратора*\n\n" +
                "Выберите действие из меню ниже:");
        sendMessage.setParseMode("Markdown");
        sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
        
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
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleConfirmDeleteCategory(Long chatId, Integer messageId, String callbackData) {
        Long categoryId = Long.parseLong(callbackData.replace("confirm_delete_category_", ""));
        boolean deleted = categoryService.deleteCategory(categoryId);
        
        // Вместо редактирования сообщения, отправим новое сообщение
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        
        if (deleted) {
            sendMessage.setText("✅ Категория успешно удалена.");
        } else {
            sendMessage.setText("❌ Не удалось удалить категорию. Возможно, она уже была удалена или содержит товары.");
        }
        
        // Добавляем клавиатуру админ-панели
        sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
        
        // Также удалим сообщение с запросом подтверждения
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
     * Обрабатывает подтверждение удаления категории с использованием SendMessage
     * @param chatId ID чата
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleConfirmDeleteCategory(Long chatId, String callbackData) {
        Long categoryId = Long.parseLong(callbackData.replace("confirm_delete_category_", ""));
        boolean deleted = categoryService.deleteCategory(categoryId);
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        
        if (deleted) {
            sendMessage.setText("✅ Категория успешно удалена.");
        } else {
            sendMessage.setText("❌ Не удалось удалить категорию. Возможно, она уже была удалена или содержит товары.");
        }
        
        // Добавляем клавиатуру админ-панели
        sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
        
        return sendMessage;
    }
} 