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
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.model.Product;
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
 * Обработчик callback-запросов, связанных с каталогом товаров
 */
@Component
public class CatalogCallbackHandler implements UpdateHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CatalogCallbackHandler.class);
    
    private final TelegramUserRepository telegramUserRepository;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final KeyboardFactory keyboardFactory;
    private final MessageSender messageSender;
    
    // Константы для размера страницы при пагинации
    private static final int PRODUCTS_PAGE_SIZE = 3;
    
    public CatalogCallbackHandler(
            TelegramUserRepository telegramUserRepository,
            CategoryService categoryService,
            ProductService productService,
            KeyboardFactory keyboardFactory,
            MessageSender messageSender) {
        this.telegramUserRepository = telegramUserRepository;
        this.categoryService = categoryService;
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
        return callbackData.startsWith("catalog_category_") || 
               callbackData.startsWith("catalog_products_page_") ||
               callbackData.equals("catalog_categories") ||
               callbackData.equals("back_to_catalog");
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
        
        logger.info("Handling catalog callback: {} for chatId: {}", callbackData, chatId);
        
        try {
            if (callbackData.startsWith("catalog_category_")) {
                return messageId != null 
                    ? handleCategoryProducts(chatId, messageId, callbackData)
                    : handleCategoryProducts(chatId, callbackData);
            } else if (callbackData.startsWith("catalog_products_page_")) {
                // Формат: catalog_products_page_{categoryId}_{page}
                return messageId != null 
                    ? handleProductsInCategoryPage(chatId, messageId, callbackData)
                    : handleProductsInCategoryPage(chatId, callbackData);
            } else if (callbackData.equals("catalog_categories")) {
                return messageId != null 
                    ? handleCatalogCategories(chatId, messageId)
                    : handleCatalogCategories(chatId);
            } else {
                logger.warn("Unhandled catalog callback: {}", callbackData);
                return null;
            }
        } catch (Exception e) {
            logger.error("Error handling catalog callback: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при обработке запроса. Пожалуйста, попробуйте еще раз.");
        }
    }
    
    /**
     * Обрабатывает нажатие на категорию в каталоге с использованием EditMessageText
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleCategoryProducts(Long chatId, Integer messageId, String callbackData) {
        Long categoryId = Long.parseLong(callbackData.replace("catalog_category_", ""));
        
        // Получаем категорию
        Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
        if (categoryOpt.isEmpty()) {
            EditMessageText editMessageText = new EditMessageText();
            editMessageText.setChatId(chatId);
            editMessageText.setMessageId(messageId);
            editMessageText.setText("Категория не найдена.");
            return editMessageText;
        }
        
        Category category = categoryOpt.get();
        
        // Получаем товары категории (первая страница)
        int page = 1;
        Pageable pageable = PageRequest.of(page - 1, PRODUCTS_PAGE_SIZE);
        Page<Product> productsPage = productService.getProductsByCategory(category, pageable);
        List<Product> products = productsPage.getContent();
        
        // Обновляем заголовок категории с информацией о пагинации
        StringBuilder headerText = new StringBuilder();
        headerText.append("🛍 *Товары в категории \"").append(category.getName()).append("\"*\n\n");
        
        if (products.isEmpty()) {
            headerText.append("В данной категории пока нет товаров.");
            
            EditMessageText editMessageText = new EditMessageText();
            editMessageText.setChatId(chatId);
            editMessageText.setMessageId(messageId);
            editMessageText.setText(headerText.toString());
            editMessageText.setParseMode("Markdown");
            
            // Добавляем кнопку возврата к категориям
            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Назад к категориям");
            backButton.setCallbackData("catalog_categories");
            backRow.add(backButton);
            keyboard.add(backRow);
            keyboardMarkup.setKeyboard(keyboard);
            
            editMessageText.setReplyMarkup(keyboardMarkup);
            
            return editMessageText;
        } else {
            // Если есть товары, обновляем заголовок с информацией о пагинации
            headerText.append("Страница ").append(page).append(" из ").append(productsPage.getTotalPages()).append("\n\n");
            
            // Обновляем заголовок
            EditMessageText editMessageText = new EditMessageText();
            editMessageText.setChatId(chatId);
            editMessageText.setMessageId(messageId);
            editMessageText.setText(headerText.toString());
            editMessageText.setParseMode("Markdown");
            
            // Добавляем только кнопку возврата к категориям в заголовок
            InlineKeyboardMarkup headerKeyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> headerKeyboard = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Назад к категориям");
            backButton.setCallbackData("catalog_categories");
            backRow.add(backButton);
            headerKeyboard.add(backRow);
            headerKeyboardMarkup.setKeyboard(headerKeyboard);
            
            editMessageText.setReplyMarkup(headerKeyboardMarkup);
            
            // Отправляем заголовок
            try {
                messageSender.executeEditMessage(editMessageText);
            } catch (Exception e) {
                logger.error("Ошибка при отправке заголовка категории: {}", e.getMessage());
            }
            
            // Теперь отправляем каждый товар отдельным сообщением
            for (Product product : products) {
                try {
                    // Формируем сообщение для товара
                    StringBuilder productText = new StringBuilder();
                    productText.append("*").append(product.getName()).append("*\n");
                    productText.append("💰 Цена: ").append(product.getPrice()).append(" руб.\n");
                    productText.append("📦 В наличии: ").append(product.getStock()).append(" шт.\n");
                    if (product.getDescription() != null && !product.getDescription().isEmpty()) {
                        productText.append("📝 Описание: ").append(product.getDescription()).append("\n");
                    }
                    
                    // Создаем клавиатуру с кнопкой "Добавить в корзину"
                    InlineKeyboardMarkup productKeyboard = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> productKeyboardRows = new ArrayList<>();
                    List<InlineKeyboardButton> addToCartRow = new ArrayList<>();
                    InlineKeyboardButton addToCartButton = new InlineKeyboardButton();
                    addToCartButton.setText("🛒 Добавить в корзину");
                    addToCartButton.setCallbackData("add_to_cart_" + product.getId());
                    addToCartRow.add(addToCartButton);
                    productKeyboardRows.add(addToCartRow);
                    productKeyboard.setKeyboard(productKeyboardRows);
                    
                    // Если у товара есть изображение, отправляем фото с подписью
                    if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                        SendPhoto sendPhoto = new SendPhoto();
                        sendPhoto.setChatId(chatId);
                        sendPhoto.setPhoto(new InputFile(product.getImageUrl()));
                        sendPhoto.setCaption(productText.toString());
                        sendPhoto.setParseMode("Markdown");
                        sendPhoto.setReplyMarkup(productKeyboard);
                        
                        messageSender.executePhoto(sendPhoto);
                    } else {
                        // Иначе отправляем текстовое сообщение
                        SendMessage sendMessage = new SendMessage();
                        sendMessage.setChatId(chatId);
                        sendMessage.setText(productText.toString());
                        sendMessage.setParseMode("Markdown");
                        sendMessage.setReplyMarkup(productKeyboard);
                        
                        messageSender.executeMessage(sendMessage);
                    }
                } catch (Exception e) {
                    logger.error("Ошибка при отправке товара: {}", e.getMessage());
                }
            }
            
            // После всех товаров отправляем сообщение с кнопками пагинации
            if (productsPage.getTotalPages() > 1) {
                try {
                    SendMessage paginationMessage = new SendMessage();
                    paginationMessage.setChatId(chatId);
                    paginationMessage.setText("📄 *Страницы*");
                    paginationMessage.setParseMode("Markdown");
                    
                    // Создаем клавиатуру с кнопками пагинации
                    InlineKeyboardMarkup paginationKeyboardMarkup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> paginationKeyboard = new ArrayList<>();
                    List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                    
                    if (page > 1) {
                        InlineKeyboardButton prevButton = new InlineKeyboardButton();
                        prevButton.setText("⬅️ Предыдущая");
                        prevButton.setCallbackData("catalog_products_page_" + categoryId + "_" + (page - 1));
                        paginationRow.add(prevButton);
                    }
                    
                    if (page < productsPage.getTotalPages()) {
                        InlineKeyboardButton nextButton = new InlineKeyboardButton();
                        nextButton.setText("Следующая ➡️");
                        nextButton.setCallbackData("catalog_products_page_" + categoryId + "_" + (page + 1));
                        paginationRow.add(nextButton);
                    }
                    
                    paginationKeyboard.add(paginationRow);
                    paginationKeyboardMarkup.setKeyboard(paginationKeyboard);
                    paginationMessage.setReplyMarkup(paginationKeyboardMarkup);
                    
                    messageSender.executeMessage(paginationMessage);
                } catch (Exception e) {
                    logger.error("Ошибка при отправке кнопок пагинации: {}", e.getMessage());
                }
            }
            
            // Возвращаем null, так как мы уже отправили все сообщения
            return null;
        }
    }
    
    /**
     * Обрабатывает нажатие на категорию в каталоге с использованием SendMessage
     * @param chatId ID чата
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleCategoryProducts(Long chatId, String callbackData) {
        Long categoryId = Long.parseLong(callbackData.replace("catalog_category_", ""));
        
        // Получаем категорию
        Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
        if (categoryOpt.isEmpty()) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("Категория не найдена.");
            return sendMessage;
        }
        
        Category category = categoryOpt.get();
        
        // Получаем товары категории (первая страница)
        int page = 1;
        Pageable pageable = PageRequest.of(page - 1, PRODUCTS_PAGE_SIZE);
        Page<Product> productsPage = productService.getProductsByCategory(category, pageable);
        List<Product> products = productsPage.getContent();
        
        // Сначала отправляем заголовок категории
        StringBuilder headerText = new StringBuilder();
        headerText.append("🛍 *Товары в категории \"").append(category.getName()).append("\"*\n\n");
        
        if (products.isEmpty()) {
            headerText.append("В данной категории пока нет товаров.");
            
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText(headerText.toString());
            sendMessage.setParseMode("Markdown");
            
            // Добавляем кнопку возврата к категориям
            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Назад к категориям");
            backButton.setCallbackData("catalog_categories");
            backRow.add(backButton);
            keyboard.add(backRow);
            keyboardMarkup.setKeyboard(keyboard);
            
            sendMessage.setReplyMarkup(keyboardMarkup);
            
            return sendMessage;
        } else {
            // Если есть товары, отправляем заголовок с информацией о пагинации
            headerText.append("Страница ").append(page).append(" из ").append(productsPage.getTotalPages()).append("\n\n");
            
            SendMessage headerMessage = new SendMessage();
            headerMessage.setChatId(chatId);
            headerMessage.setText(headerText.toString());
            headerMessage.setParseMode("Markdown");
            
            // Добавляем только кнопку возврата к категориям в заголовок
            InlineKeyboardMarkup headerKeyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> headerKeyboard = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Назад к категориям");
            backButton.setCallbackData("catalog_categories");
            backRow.add(backButton);
            headerKeyboard.add(backRow);
            headerKeyboardMarkup.setKeyboard(headerKeyboard);
            
            headerMessage.setReplyMarkup(headerKeyboardMarkup);
            
            // Отправляем заголовок
            try {
                messageSender.executeMessage(headerMessage);
            } catch (Exception e) {
                logger.error("Ошибка при отправке заголовка категории: {}", e.getMessage());
            }
            
            // Теперь отправляем каждый товар отдельным сообщением
            for (Product product : products) {
                try {
                    // Формируем сообщение для товара
                    StringBuilder productText = new StringBuilder();
                    productText.append("*").append(product.getName()).append("*\n");
                    productText.append("💰 Цена: ").append(product.getPrice()).append(" руб.\n");
                    productText.append("📦 В наличии: ").append(product.getStock()).append(" шт.\n");
                    if (product.getDescription() != null && !product.getDescription().isEmpty()) {
                        productText.append("📝 Описание: ").append(product.getDescription()).append("\n");
                    }
                    
                    // Создаем клавиатуру с кнопкой "Добавить в корзину"
                    InlineKeyboardMarkup productKeyboard = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> productKeyboardRows = new ArrayList<>();
                    List<InlineKeyboardButton> addToCartRow = new ArrayList<>();
                    InlineKeyboardButton addToCartButton = new InlineKeyboardButton();
                    addToCartButton.setText("🛒 Добавить в корзину");
                    addToCartButton.setCallbackData("add_to_cart_" + product.getId());
                    addToCartRow.add(addToCartButton);
                    productKeyboardRows.add(addToCartRow);
                    productKeyboard.setKeyboard(productKeyboardRows);
                    
                    // Если у товара есть изображение, отправляем фото с подписью
                    if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                        SendPhoto sendPhoto = new SendPhoto();
                        sendPhoto.setChatId(chatId);
                        sendPhoto.setPhoto(new InputFile(product.getImageUrl()));
                        sendPhoto.setCaption(productText.toString());
                        sendPhoto.setParseMode("Markdown");
                        sendPhoto.setReplyMarkup(productKeyboard);
                        
                        messageSender.executePhoto(sendPhoto);
                    } else {
                        // Иначе отправляем текстовое сообщение
                        SendMessage sendMessage = new SendMessage();
                        sendMessage.setChatId(chatId);
                        sendMessage.setText(productText.toString());
                        sendMessage.setParseMode("Markdown");
                        sendMessage.setReplyMarkup(productKeyboard);
                        
                        messageSender.executeMessage(sendMessage);
                    }
                } catch (Exception e) {
                    logger.error("Ошибка при отправке товара: {}", e.getMessage());
                }
            }
            
            // После всех товаров отправляем сообщение с кнопками пагинации
            if (productsPage.getTotalPages() > 1) {
                try {
                    SendMessage paginationMessage = new SendMessage();
                    paginationMessage.setChatId(chatId);
                    paginationMessage.setText("📄 *Страницы*");
                    paginationMessage.setParseMode("Markdown");
                    
                    // Создаем клавиатуру с кнопками пагинации
                    InlineKeyboardMarkup paginationKeyboardMarkup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> paginationKeyboard = new ArrayList<>();
                    List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                    
                    if (page > 1) {
                        InlineKeyboardButton prevButton = new InlineKeyboardButton();
                        prevButton.setText("⬅️ Предыдущая");
                        prevButton.setCallbackData("catalog_products_page_" + categoryId + "_" + (page - 1));
                        paginationRow.add(prevButton);
                    }
                    
                    if (page < productsPage.getTotalPages()) {
                        InlineKeyboardButton nextButton = new InlineKeyboardButton();
                        nextButton.setText("Следующая ➡️");
                        nextButton.setCallbackData("catalog_products_page_" + categoryId + "_" + (page + 1));
                        paginationRow.add(nextButton);
                    }
                    
                    paginationKeyboard.add(paginationRow);
                    paginationKeyboardMarkup.setKeyboard(paginationKeyboard);
                    paginationMessage.setReplyMarkup(paginationKeyboardMarkup);
                    
                    messageSender.executeMessage(paginationMessage);
                } catch (Exception e) {
                    logger.error("Ошибка при отправке кнопок пагинации: {}", e.getMessage());
                }
            }
            
            // Возвращаем null, так как мы уже отправили все сообщения
            return null;
        }
    }
    
    /**
     * Обрабатывает пагинацию товаров в категории с использованием EditMessageText
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleProductsInCategoryPage(Long chatId, Integer messageId, String callbackData) {
        // Формат: catalog_products_page_{categoryId}_{page}
        String[] parts = callbackData.replace("catalog_products_page_", "").split("_");
        Long categoryId = Long.parseLong(parts[0]);
        int page = Integer.parseInt(parts[1]);
        
        // Получаем категорию
        Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
        if (categoryOpt.isEmpty()) {
            EditMessageText editMessageText = new EditMessageText();
            editMessageText.setChatId(chatId);
            editMessageText.setMessageId(messageId);
            editMessageText.setText("Категория не найдена.");
            return editMessageText;
        }
        
        Category category = categoryOpt.get();
        
        // Получаем товары категории для указанной страницы
        Pageable pageable = PageRequest.of(page - 1, PRODUCTS_PAGE_SIZE);
        Page<Product> productsPage = productService.getProductsByCategory(category, pageable);
        List<Product> products = productsPage.getContent();
        
        // Обновляем заголовок категории с информацией о пагинации
        StringBuilder headerText = new StringBuilder();
        headerText.append("🛍 *Товары в категории \"").append(category.getName()).append("\"*\n\n");
        
        if (products.isEmpty()) {
            headerText.append("В данной категории пока нет товаров.");
            
            EditMessageText editMessageText = new EditMessageText();
            editMessageText.setChatId(chatId);
            editMessageText.setMessageId(messageId);
            editMessageText.setText(headerText.toString());
            editMessageText.setParseMode("Markdown");
            
            // Добавляем кнопку возврата к категориям
            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Назад к категориям");
            backButton.setCallbackData("catalog_categories");
            backRow.add(backButton);
            keyboard.add(backRow);
            keyboardMarkup.setKeyboard(keyboard);
            
            editMessageText.setReplyMarkup(keyboardMarkup);
            
            return editMessageText;
        } else {
            // Если есть товары, обновляем заголовок с информацией о пагинации
            headerText.append("Страница ").append(page).append(" из ").append(productsPage.getTotalPages()).append("\n\n");
            
            // Обновляем заголовок
            EditMessageText editMessageText = new EditMessageText();
            editMessageText.setChatId(chatId);
            editMessageText.setMessageId(messageId);
            editMessageText.setText(headerText.toString());
            editMessageText.setParseMode("Markdown");
            
            // Добавляем только кнопку возврата к категориям в заголовок
            InlineKeyboardMarkup headerKeyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> headerKeyboard = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Назад к категориям");
            backButton.setCallbackData("catalog_categories");
            backRow.add(backButton);
            headerKeyboard.add(backRow);
            headerKeyboardMarkup.setKeyboard(headerKeyboard);
            
            editMessageText.setReplyMarkup(headerKeyboardMarkup);
            
            // Отправляем заголовок
            try {
                messageSender.executeEditMessage(editMessageText);
            } catch (Exception e) {
                logger.error("Ошибка при отправке заголовка категории: {}", e.getMessage());
            }
            
            // Теперь отправляем каждый товар отдельным сообщением
            for (Product product : products) {
                try {
                    // Формируем сообщение для товара
                    StringBuilder productText = new StringBuilder();
                    productText.append("*").append(product.getName()).append("*\n");
                    productText.append("💰 Цена: ").append(product.getPrice()).append(" руб.\n");
                    productText.append("📦 В наличии: ").append(product.getStock()).append(" шт.\n");
                    if (product.getDescription() != null && !product.getDescription().isEmpty()) {
                        productText.append("📝 Описание: ").append(product.getDescription()).append("\n");
                    }
                    
                    // Создаем клавиатуру с кнопкой "Добавить в корзину"
                    InlineKeyboardMarkup productKeyboard = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> productKeyboardRows = new ArrayList<>();
                    List<InlineKeyboardButton> addToCartRow = new ArrayList<>();
                    InlineKeyboardButton addToCartButton = new InlineKeyboardButton();
                    addToCartButton.setText("🛒 Добавить в корзину");
                    addToCartButton.setCallbackData("add_to_cart_" + product.getId());
                    addToCartRow.add(addToCartButton);
                    productKeyboardRows.add(addToCartRow);
                    productKeyboard.setKeyboard(productKeyboardRows);
                    
                    // Если у товара есть изображение, отправляем фото с подписью
                    if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                        SendPhoto sendPhoto = new SendPhoto();
                        sendPhoto.setChatId(chatId);
                        sendPhoto.setPhoto(new InputFile(product.getImageUrl()));
                        sendPhoto.setCaption(productText.toString());
                        sendPhoto.setParseMode("Markdown");
                        sendPhoto.setReplyMarkup(productKeyboard);
                        
                        messageSender.executePhoto(sendPhoto);
                    } else {
                        // Иначе отправляем текстовое сообщение
                        SendMessage sendMessage = new SendMessage();
                        sendMessage.setChatId(chatId);
                        sendMessage.setText(productText.toString());
                        sendMessage.setParseMode("Markdown");
                        sendMessage.setReplyMarkup(productKeyboard);
                        
                        messageSender.executeMessage(sendMessage);
                    }
                } catch (Exception e) {
                    logger.error("Ошибка при отправке товара: {}", e.getMessage());
                }
            }
            
            // После всех товаров отправляем сообщение с кнопками пагинации
            if (productsPage.getTotalPages() > 1) {
                try {
                    SendMessage paginationMessage = new SendMessage();
                    paginationMessage.setChatId(chatId);
                    paginationMessage.setText("📄 *Страницы*");
                    paginationMessage.setParseMode("Markdown");
                    
                    // Создаем клавиатуру с кнопками пагинации
                    InlineKeyboardMarkup paginationKeyboardMarkup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> paginationKeyboard = new ArrayList<>();
                    List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                    
                    if (page > 1) {
                        InlineKeyboardButton prevButton = new InlineKeyboardButton();
                        prevButton.setText("⬅️ Предыдущая");
                        prevButton.setCallbackData("catalog_products_page_" + categoryId + "_" + (page - 1));
                        paginationRow.add(prevButton);
                    }
                    
                    if (page < productsPage.getTotalPages()) {
                        InlineKeyboardButton nextButton = new InlineKeyboardButton();
                        nextButton.setText("Следующая ➡️");
                        nextButton.setCallbackData("catalog_products_page_" + categoryId + "_" + (page + 1));
                        paginationRow.add(nextButton);
                    }
                    
                    paginationKeyboard.add(paginationRow);
                    paginationKeyboardMarkup.setKeyboard(paginationKeyboard);
                    paginationMessage.setReplyMarkup(paginationKeyboardMarkup);
                    
                    messageSender.executeMessage(paginationMessage);
                } catch (Exception e) {
                    logger.error("Ошибка при отправке кнопок пагинации: {}", e.getMessage());
                }
            }
            
            // Возвращаем null, так как мы уже отправили все сообщения
            return null;
        }
    }
    
    /**
     * Обрабатывает пагинацию товаров в категории с использованием SendMessage
     * @param chatId ID чата
     * @param callbackData данные callback
     * @return ответ бота
     */
    private BotApiMethod<?> handleProductsInCategoryPage(Long chatId, String callbackData) {
        // Формат: catalog_products_page_{categoryId}_{page}
        String[] parts = callbackData.replace("catalog_products_page_", "").split("_");
        Long categoryId = Long.parseLong(parts[0]);
        int page = Integer.parseInt(parts[1]);
        
        // Получаем категорию
        Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
        if (categoryOpt.isEmpty()) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("Категория не найдена.");
            return sendMessage;
        }
        
        Category category = categoryOpt.get();
        
        // Получаем товары категории для указанной страницы
        Pageable pageable = PageRequest.of(page - 1, PRODUCTS_PAGE_SIZE);
        Page<Product> productsPage = productService.getProductsByCategory(category, pageable);
        List<Product> products = productsPage.getContent();
        
        // Формируем заголовок категории с информацией о пагинации
        StringBuilder headerText = new StringBuilder();
        headerText.append("🛍 *Товары в категории \"").append(category.getName()).append("\"*\n\n");
        
        if (products.isEmpty()) {
            headerText.append("В данной категории пока нет товаров.");
            
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText(headerText.toString());
            sendMessage.setParseMode("Markdown");
            
            // Добавляем кнопку возврата к категориям
            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Назад к категориям");
            backButton.setCallbackData("catalog_categories");
            backRow.add(backButton);
            keyboard.add(backRow);
            keyboardMarkup.setKeyboard(keyboard);
            
            sendMessage.setReplyMarkup(keyboardMarkup);
            
            return sendMessage;
        } else {
            // Если есть товары, отправляем заголовок с информацией о пагинации
            headerText.append("Страница ").append(page).append(" из ").append(productsPage.getTotalPages()).append("\n\n");
            
            SendMessage headerMessage = new SendMessage();
            headerMessage.setChatId(chatId);
            headerMessage.setText(headerText.toString());
            headerMessage.setParseMode("Markdown");
            
            // Добавляем только кнопку возврата к категориям в заголовок
            InlineKeyboardMarkup headerKeyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> headerKeyboard = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Назад к категориям");
            backButton.setCallbackData("catalog_categories");
            backRow.add(backButton);
            headerKeyboard.add(backRow);
            headerKeyboardMarkup.setKeyboard(headerKeyboard);
            
            headerMessage.setReplyMarkup(headerKeyboardMarkup);
            
            // Отправляем заголовок
            try {
                messageSender.executeMessage(headerMessage);
            } catch (Exception e) {
                logger.error("Ошибка при отправке заголовка категории: {}", e.getMessage());
            }
            
            // Теперь отправляем каждый товар отдельным сообщением
            for (Product product : products) {
                try {
                    // Формируем сообщение для товара
                    StringBuilder productText = new StringBuilder();
                    productText.append("*").append(product.getName()).append("*\n");
                    productText.append("💰 Цена: ").append(product.getPrice()).append(" руб.\n");
                    productText.append("📦 В наличии: ").append(product.getStock()).append(" шт.\n");
                    if (product.getDescription() != null && !product.getDescription().isEmpty()) {
                        productText.append("📝 Описание: ").append(product.getDescription()).append("\n");
                    }
                    
                    // Создаем клавиатуру с кнопкой "Добавить в корзину"
                    InlineKeyboardMarkup productKeyboard = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> productKeyboardRows = new ArrayList<>();
                    List<InlineKeyboardButton> addToCartRow = new ArrayList<>();
                    InlineKeyboardButton addToCartButton = new InlineKeyboardButton();
                    addToCartButton.setText("🛒 Добавить в корзину");
                    addToCartButton.setCallbackData("add_to_cart_" + product.getId());
                    addToCartRow.add(addToCartButton);
                    productKeyboardRows.add(addToCartRow);
                    productKeyboard.setKeyboard(productKeyboardRows);
                    
                    // Если у товара есть изображение, отправляем фото с подписью
                    if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                        SendPhoto sendPhoto = new SendPhoto();
                        sendPhoto.setChatId(chatId);
                        sendPhoto.setPhoto(new InputFile(product.getImageUrl()));
                        sendPhoto.setCaption(productText.toString());
                        sendPhoto.setParseMode("Markdown");
                        sendPhoto.setReplyMarkup(productKeyboard);
                        
                        messageSender.executePhoto(sendPhoto);
                    } else {
                        // Иначе отправляем текстовое сообщение
                        SendMessage sendMessage = new SendMessage();
                        sendMessage.setChatId(chatId);
                        sendMessage.setText(productText.toString());
                        sendMessage.setParseMode("Markdown");
                        sendMessage.setReplyMarkup(productKeyboard);
                        
                        messageSender.executeMessage(sendMessage);
                    }
                } catch (Exception e) {
                    logger.error("Ошибка при отправке товара: {}", e.getMessage());
                }
            }
            
            // После всех товаров отправляем сообщение с кнопками пагинации
            if (productsPage.getTotalPages() > 1) {
                try {
                    SendMessage paginationMessage = new SendMessage();
                    paginationMessage.setChatId(chatId);
                    paginationMessage.setText("📄 *Страницы*");
                    paginationMessage.setParseMode("Markdown");
                    
                    // Создаем клавиатуру с кнопками пагинации
                    InlineKeyboardMarkup paginationKeyboardMarkup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> paginationKeyboard = new ArrayList<>();
                    List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                    
                    if (page > 1) {
                        InlineKeyboardButton prevButton = new InlineKeyboardButton();
                        prevButton.setText("⬅️ Предыдущая");
                        prevButton.setCallbackData("catalog_products_page_" + categoryId + "_" + (page - 1));
                        paginationRow.add(prevButton);
                    }
                    
                    if (page < productsPage.getTotalPages()) {
                        InlineKeyboardButton nextButton = new InlineKeyboardButton();
                        nextButton.setText("Следующая ➡️");
                        nextButton.setCallbackData("catalog_products_page_" + categoryId + "_" + (page + 1));
                        paginationRow.add(nextButton);
                    }
                    
                    paginationKeyboard.add(paginationRow);
                    paginationKeyboardMarkup.setKeyboard(paginationKeyboard);
                    paginationMessage.setReplyMarkup(paginationKeyboardMarkup);
                    
                    messageSender.executeMessage(paginationMessage);
                } catch (Exception e) {
                    logger.error("Ошибка при отправке кнопок пагинации: {}", e.getMessage());
                }
            }
            
            // Возвращаем null, так как мы уже отправили все сообщения
            return null;
        }
    }
    
    /**
     * Обрабатывает нажатие на кнопку "Назад к категориям" с использованием EditMessageText
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @return ответ бота
     */
    private BotApiMethod<?> handleCatalogCategories(Long chatId, Integer messageId) {
        // Получаем список категорий
        List<Category> categories = categoryService.getAllCategories();
        
        // Формируем сообщение
        StringBuilder messageText = new StringBuilder();
        messageText.append("📋 *Каталог товаров*\n\n");
        messageText.append("Выберите категорию:\n\n");
        
        // Создаем клавиатуру с категориями
        InlineKeyboardMarkup keyboardMarkup = keyboardFactory.createCatalogKeyboard(categories);
        
        // Отправляем сообщение
        EditMessageText editMessageText = new EditMessageText();
        editMessageText.setChatId(chatId);
        editMessageText.setMessageId(messageId);
        editMessageText.setText(messageText.toString());
        editMessageText.setParseMode("Markdown");
        editMessageText.setReplyMarkup(keyboardMarkup);
        
        return editMessageText;
    }
    
    /**
     * Обрабатывает нажатие на кнопку "Назад к категориям" с использованием SendMessage
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleCatalogCategories(Long chatId) {
        // Получаем список категорий
        List<Category> categories = categoryService.getAllCategories();
        
        // Формируем сообщение
        StringBuilder messageText = new StringBuilder();
        messageText.append("📋 *Каталог товаров*\n\n");
        messageText.append("Выберите категорию:\n\n");
        
        // Создаем клавиатуру с категориями
        InlineKeyboardMarkup keyboardMarkup = keyboardFactory.createCatalogKeyboard(categories);
        
        // Отправляем сообщение
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText.toString());
        sendMessage.setParseMode("Markdown");
        sendMessage.setReplyMarkup(keyboardMarkup);
        
        return sendMessage;
    }
    
    /**
     * Создает текстовое сообщение
     * @param chatId ID чата
     * @param text текст сообщения
     * @return объект сообщения
     */
    private SendMessage createTextMessage(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        return sendMessage;
    }
} 