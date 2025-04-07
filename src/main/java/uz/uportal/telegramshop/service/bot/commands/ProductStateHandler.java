package uz.uportal.telegramshop.service.bot.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.model.Product;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.CategoryService;
import uz.uportal.telegramshop.service.ProductService;
import uz.uportal.telegramshop.service.bot.core.MessageSender;
import uz.uportal.telegramshop.service.bot.core.StateHandler;
import uz.uportal.telegramshop.service.bot.keyboards.KeyboardFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Обработчик состояний пользователя при добавлении и редактировании товара
 */
@Component
public class ProductStateHandler implements StateHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductStateHandler.class);
    private final TelegramUserRepository telegramUserRepository;
    private final KeyboardFactory keyboardFactory;
    private final ProductService productService;
    private final CategoryService categoryService;
    private final MessageSender messageSender;
    
    // Временное хранилище данных о товарах в процессе добавления/редактирования
    private final Map<Long, Product> productDrafts = new HashMap<>();
    
    public ProductStateHandler(
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
        if (!update.hasMessage()) {
            return false;
        }
        
        Long chatId = update.getMessage().getChatId();
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        
        if (user == null) {
            return false;
        }
        
        String state = user.getState();
        return state != null && (
                state.equals("ADDING_PRODUCT_NAME") ||
                state.equals("ADDING_PRODUCT_PRICE") ||
                state.equals("ADDING_PRODUCT_STOCK") ||
                state.equals("ADDING_PRODUCT_CATEGORY") ||
                state.equals("ADDING_PRODUCT_DESCRIPTION") ||
                state.equals("ADDING_PRODUCT_IMAGE") ||
                state.startsWith("EDITING_PRODUCT_") ||
                state.equals("EDITING_PRODUCT_NAME") ||
                state.equals("EDITING_PRODUCT_PRICE") ||
                state.equals("EDITING_PRODUCT_STOCK") ||
                state.equals("EDITING_PRODUCT_CATEGORY") ||
                state.equals("EDITING_PRODUCT_DESCRIPTION") ||
                state.equals("EDITING_PRODUCT_IMAGE")
        );
    }
    
    @Override
    public BotApiMethod<?> handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        
        if (user == null) {
            return createTextMessage(chatId, "Пользователь не найден. Пожалуйста, перезапустите бота командой /start");
        }
        
        String state = user.getState();
        return handleState(update, state);
    }
    
    @Override
    public boolean canHandleState(Update update, String state) {
        return state != null && (
                state.equals("ADDING_PRODUCT_NAME") ||
                state.equals("ADDING_PRODUCT_PRICE") ||
                state.equals("ADDING_PRODUCT_STOCK") ||
                state.equals("ADDING_PRODUCT_CATEGORY") ||
                state.equals("ADDING_PRODUCT_DESCRIPTION") ||
                state.equals("ADDING_PRODUCT_IMAGE") ||
                state.startsWith("EDITING_PRODUCT_") ||
                state.equals("EDITING_PRODUCT_NAME") ||
                state.equals("EDITING_PRODUCT_PRICE") ||
                state.equals("EDITING_PRODUCT_STOCK") ||
                state.equals("EDITING_PRODUCT_CATEGORY") ||
                state.equals("EDITING_PRODUCT_DESCRIPTION") ||
                state.equals("EDITING_PRODUCT_IMAGE")
        );
    }
    
    @Override
    public BotApiMethod<?> handleState(Update update, String state) {
        Long chatId = update.getMessage().getChatId();
        Message message = update.getMessage();
        String text = message.getText();
        
        logger.info("Handling state: {} for chatId: {}", state, chatId);
        
        // Обработка состояний добавления товара
        if (state.equals("ADDING_PRODUCT_NAME")) {
            return handleAddingProductName(chatId, text);
        } else if (state.equals("ADDING_PRODUCT_PRICE")) {
            return handleAddingProductPrice(chatId, text);
        } else if (state.equals("ADDING_PRODUCT_STOCK")) {
            return handleAddingProductStock(chatId, text);
        } else if (state.equals("ADDING_PRODUCT_CATEGORY")) {
            return handleAddingProductCategory(chatId, text);
        } else if (state.equals("ADDING_PRODUCT_DESCRIPTION")) {
            return handleAddingProductDescription(chatId, text);
        } else if (state.equals("ADDING_PRODUCT_IMAGE")) {
            if (message.hasPhoto()) {
                return handleAddingProductImage(chatId, message);
            } else {
                return handleSkipProductImage(chatId);
            }
        }
        
        // Обработка начального состояния редактирования товара
        else if (state.startsWith("EDITING_PRODUCT_") && 
                 !state.equals("EDITING_PRODUCT_NAME") && 
                 !state.equals("EDITING_PRODUCT_PRICE") && 
                 !state.equals("EDITING_PRODUCT_STOCK") && 
                 !state.equals("EDITING_PRODUCT_CATEGORY") && 
                 !state.equals("EDITING_PRODUCT_DESCRIPTION") && 
                 !state.equals("EDITING_PRODUCT_IMAGE")) {
            
            logger.info("Обработка состояния редактирования товара: {}", state);
            return handleEditingProductField(chatId, text);
        }
        
        // Обработка состояний редактирования товара
        else if (state.equals("EDITING_PRODUCT_NAME")) {
            return handleEditingProductName(chatId, text);
        } else if (state.equals("EDITING_PRODUCT_PRICE")) {
            return handleEditingProductPrice(chatId, text);
        } else if (state.equals("EDITING_PRODUCT_STOCK")) {
            return handleEditingProductStock(chatId, text);
        } else if (state.equals("EDITING_PRODUCT_CATEGORY")) {
            return handleEditingProductCategory(chatId, text);
        } else if (state.equals("EDITING_PRODUCT_DESCRIPTION")) {
            return handleEditingProductDescription(chatId, text);
        } else if (state.equals("EDITING_PRODUCT_IMAGE")) {
            if (message.hasPhoto()) {
                return handleEditingProductImage(chatId, message);
            } else {
                return handleSkipEditingProductImage(chatId);
            }
        }
        
        logger.warn("Неизвестное состояние: {}", state);
        return createTextMessage(chatId, "Неизвестное состояние. Пожалуйста, начните заново.");
    }
    
    /**
     * Обрабатывает добавление названия товара
     * @param chatId ID чата
     * @param name название товара
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddingProductName(Long chatId, String name) {
        // Создаем новый черновик товара или получаем существующий
        Product product = productDrafts.getOrDefault(chatId, new Product());
        product.setName(name);
        productDrafts.put(chatId, product);
        
        // Обновляем состояние пользователя
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState("ADDING_PRODUCT_PRICE");
            telegramUserRepository.save(user);
        }
        
        return createTextMessage(chatId, "Введите цену товара (в рублях):");
    }
    
    /**
     * Обрабатывает добавление цены товара
     * @param chatId ID чата
     * @param priceText цена товара
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddingProductPrice(Long chatId, String priceText) {
        try {
            BigDecimal price = new BigDecimal(priceText);
            
            // Получаем черновик товара
            Product product = productDrafts.get(chatId);
            if (product == null) {
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните добавление товара заново.");
            }
            
            product.setPrice(price);
            productDrafts.put(chatId, product);
            
            // Обновляем состояние пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState("ADDING_PRODUCT_STOCK");
                telegramUserRepository.save(user);
            }
            
            return createTextMessage(chatId, "Введите количество товара в наличии:");
        } catch (NumberFormatException e) {
            return createTextMessage(chatId, "Некорректная цена. Пожалуйста, введите число (например, 100 или 99.99):");
        }
    }
    
    /**
     * Обрабатывает добавление количества товара
     * @param chatId ID чата
     * @param stockText количество товара
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddingProductStock(Long chatId, String stockText) {
        try {
            int stock = Integer.parseInt(stockText);
            
            // Получаем черновик товара
            Product product = productDrafts.get(chatId);
            if (product == null) {
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните добавление товара заново.");
            }
            
            product.setStock(stock);
            productDrafts.put(chatId, product);
            
            // Получаем список категорий для выбора
            List<Category> categories = categoryService.getAllCategories();
            StringBuilder messageText = new StringBuilder("Выберите категорию товара (введите номер):\n\n");
            
            for (int i = 0; i < categories.size(); i++) {
                Category category = categories.get(i);
                messageText.append(i + 1).append(". ").append(category.getName()).append("\n");
            }
            
            // Обновляем состояние пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState("ADDING_PRODUCT_CATEGORY");
                telegramUserRepository.save(user);
            }
            
            return createTextMessage(chatId, messageText.toString());
        } catch (NumberFormatException e) {
            return createTextMessage(chatId, "Некорректное количество. Пожалуйста, введите целое число:");
        }
    }
    
    /**
     * Обрабатывает добавление категории товара
     * @param chatId ID чата
     * @param categoryText номер категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddingProductCategory(Long chatId, String categoryText) {
        try {
            int categoryIndex = Integer.parseInt(categoryText) - 1;
            List<Category> categories = categoryService.getAllCategories();
            
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                return createTextMessage(chatId, "Некорректный номер категории. Пожалуйста, выберите из списка:");
            }
            
            Category category = categories.get(categoryIndex);
            
            // Получаем черновик товара
            Product product = productDrafts.get(chatId);
            if (product == null) {
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните добавление товара заново.");
            }
            
            product.setCategory(category);
            productDrafts.put(chatId, product);
            
            // Обновляем состояние пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState("ADDING_PRODUCT_DESCRIPTION");
                telegramUserRepository.save(user);
            }
            
            return createTextMessage(chatId, "Введите описание товара:");
        } catch (NumberFormatException e) {
            return createTextMessage(chatId, "Некорректный номер категории. Пожалуйста, введите число:");
        }
    }
    
    /**
     * Обрабатывает добавление описания товара
     * @param chatId ID чата
     * @param description описание товара
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddingProductDescription(Long chatId, String description) {
        // Получаем черновик товара
        Product product = productDrafts.get(chatId);
        if (product == null) {
            return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните добавление товара заново.");
        }
        
        product.setDescription(description);
        productDrafts.put(chatId, product);
        
        // Обновляем состояние пользователя
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState("ADDING_PRODUCT_IMAGE");
            telegramUserRepository.save(user);
        }
        
        return createTextMessage(chatId, "Отправьте изображение товара или введите любой текст, чтобы пропустить этот шаг:");
    }
    
    /**
     * Обрабатывает добавление изображения товара
     * @param chatId ID чата
     * @param message сообщение с изображением
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddingProductImage(Long chatId, Message message) {
        // Получаем черновик товара
        Product product = productDrafts.get(chatId);
        if (product == null) {
            return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните добавление товара заново.");
        }
        
        // Получаем fileId изображения
        String fileId = message.getPhoto().get(message.getPhoto().size() - 1).getFileId();
        
        // Сохраняем товар с изображением
        Product savedProduct = productService.createProduct(
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            fileId,
            product.getStock(),
            product.getCategory()
        );
        
        // Очищаем черновик
        productDrafts.remove(chatId);
        
        // Сбрасываем состояние пользователя
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState(null);
            telegramUserRepository.save(user);
        }
        
        // Отправляем сообщение об успешном добавлении товара
        SendMessage successMessage = new SendMessage();
        successMessage.setChatId(chatId);
        successMessage.setText("Товар успешно добавлен!");
        
        try {
            messageSender.executeMessage(successMessage);
            
            // Отправляем информацию о товаре
            StringBuilder productText = new StringBuilder();
            productText.append("*").append(savedProduct.getName()).append("*\n\n");
            productText.append("💰 Цена: ").append(savedProduct.getPrice()).append(" руб.\n");
            productText.append("📦 В наличии: ").append(savedProduct.getStock()).append(" шт.\n");
            productText.append("🗂 Категория: ").append(savedProduct.getCategory() != null ? savedProduct.getCategory().getName() : "Не указана").append("\n\n");
            productText.append("📝 Описание: ").append(savedProduct.getDescription()).append("\n\n");
            
            // Отправляем сообщение с изображением
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId);
            sendPhoto.setPhoto(new InputFile(savedProduct.getImageUrl()));
            sendPhoto.setCaption(productText.toString());
            sendPhoto.setParseMode("Markdown");
            messageSender.executePhoto(sendPhoto);
            
            // Отправляем сообщение с кнопкой возврата в админ-панель
            SendMessage backMessage = new SendMessage();
            backMessage.setChatId(chatId);
            backMessage.setText("Что вы хотите сделать дальше?");
            backMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
            return backMessage;
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения о добавлении товара", e);
            return createTextMessage(chatId, "Товар успешно добавлен, но произошла ошибка при отправке информации о нем.");
        }
    }
    
    /**
     * Обрабатывает пропуск добавления изображения товара
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleSkipProductImage(Long chatId) {
        // Получаем черновик товара
        Product product = productDrafts.get(chatId);
        if (product == null) {
            return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните добавление товара заново.");
        }
        
        // Сохраняем товар без изображения
        Product savedProduct = productService.createProduct(
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            product.getCategory()
        );
        
        // Очищаем черновик
        productDrafts.remove(chatId);
        
        // Сбрасываем состояние пользователя
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState(null);
            telegramUserRepository.save(user);
        }
        
        // Отправляем сообщение об успешном добавлении товара
        StringBuilder messageText = new StringBuilder();
        messageText.append("Товар успешно добавлен!\n\n");
        messageText.append("*").append(savedProduct.getName()).append("*\n\n");
        messageText.append("💰 Цена: ").append(savedProduct.getPrice()).append(" руб.\n");
        messageText.append("📦 В наличии: ").append(savedProduct.getStock()).append(" шт.\n");
        messageText.append("🗂 Категория: ").append(savedProduct.getCategory() != null ? savedProduct.getCategory().getName() : "Не указана").append("\n\n");
        messageText.append("📝 Описание: ").append(savedProduct.getDescription()).append("\n\n");
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText.toString());
        sendMessage.setParseMode("Markdown");
        sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает выбор поля для редактирования товара
     * @param chatId ID чата
     * @param text текст сообщения
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditingProductField(Long chatId, String text) {
        logger.info("Обработка выбора поля для редактирования. chatId: {}, text: {}", chatId, text);
        
        try {
            // Получаем черновик товара
            Product product = productDrafts.get(chatId);
            
            // Если черновик не найден, пытаемся получить товар из состояния пользователя
            if (product == null) {
                logger.info("Черновик товара не найден для chatId: {}, пытаемся получить из состояния", chatId);
                
                TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
                if (user == null) {
                    logger.error("Пользователь не найден для chatId: {}", chatId);
                    return createTextMessage(chatId, "Пользователь не найден. Пожалуйста, перезапустите бота командой /start");
                }
                
                String state = user.getState();
                if (state == null || !state.startsWith("EDITING_PRODUCT_")) {
                    logger.error("Некорректное состояние пользователя: {}", state);
                    return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
                }
                
                String productIdStr = state.replace("EDITING_PRODUCT_", "");
                try {
                    Long productId = Long.parseLong(productIdStr);
                    Optional<Product> productOpt = productService.getProductById(productId);
                    
                    if (productOpt.isEmpty()) {
                        logger.error("Товар с ID {} не найден", productId);
                        
                        // Сбрасываем состояние пользователя
                        user.setState(null);
                        telegramUserRepository.save(user);
                        
                        return createTextMessage(chatId, "Товар не найден. Пожалуйста, начните редактирование товара заново.");
                    }
                    
                    product = productOpt.get();
                    productDrafts.put(chatId, product);
                    logger.info("Товар получен из базы данных и сохранен в черновики: {}", product.getName());
                    
                    // Если пользователь еще не выбрал поле для редактирования (первый вход в режим редактирования)
                    if (text == null || text.isEmpty() || text.equals("")) {
                        logger.info("Первый вход в режим редактирования, отправляем меню выбора поля");
                        
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
                        
                        return createTextMessage(chatId, messageText.toString());
                    }
                } catch (NumberFormatException e) {
                    logger.error("Ошибка при парсинге ID товара: {}", e.getMessage());
                    
                    // Сбрасываем состояние пользователя
                    user.setState(null);
                    telegramUserRepository.save(user);
                    
                    return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
                }
            }
            
            // Если черновик все еще не найден после всех попыток восстановления
            if (product == null) {
                logger.error("Не удалось восстановить черновик товара для chatId: {}", chatId);
                
                // Сбрасываем состояние пользователя
                TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
                if (user != null) {
                    user.setState(null);
                    telegramUserRepository.save(user);
                }
                
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
            }
            
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user == null) {
                logger.error("Пользователь не найден для chatId: {}", chatId);
                return createTextMessage(chatId, "Пользователь не найден. Пожалуйста, перезапустите бота командой /start");
            }
            
            try {
                int field = Integer.parseInt(text);
                logger.info("Выбрано поле: {}", field);
                
                switch (field) {
                    case 1: // Название
                        user.setState("EDITING_PRODUCT_NAME");
                        telegramUserRepository.save(user);
                        return createTextMessage(chatId, "Введите новое название товара:");
                    case 2: // Цена
                        user.setState("EDITING_PRODUCT_PRICE");
                        telegramUserRepository.save(user);
                        return createTextMessage(chatId, "Введите новую цену товара (в рублях):");
                    case 3: // Количество
                        user.setState("EDITING_PRODUCT_STOCK");
                        telegramUserRepository.save(user);
                        return createTextMessage(chatId, "Введите новое количество товара в наличии:");
                    case 4: // Категория
                        user.setState("EDITING_PRODUCT_CATEGORY");
                        telegramUserRepository.save(user);
                        
                        // Получаем список категорий для выбора
                        List<Category> categories = categoryService.getAllCategories();
                        StringBuilder categoryText = new StringBuilder("Выберите новую категорию товара (введите номер):\n\n");
                        
                        for (int i = 0; i < categories.size(); i++) {
                            Category category = categories.get(i);
                            categoryText.append(i + 1).append(". ").append(category.getName()).append("\n");
                        }
                        
                        return createTextMessage(chatId, categoryText.toString());
                    case 5: // Описание
                        user.setState("EDITING_PRODUCT_DESCRIPTION");
                        telegramUserRepository.save(user);
                        return createTextMessage(chatId, "Введите новое описание товара:");
                    case 6: // Изображение
                        user.setState("EDITING_PRODUCT_IMAGE");
                        telegramUserRepository.save(user);
                        return createTextMessage(chatId, "Отправьте новое изображение товара или введите любой текст, чтобы пропустить этот шаг:");
                    case 7: // Удалить товар
                        // Удаляем товар
                        boolean deleted = productService.deleteProduct(product.getId());
                        
                        // Очищаем черновик
                        productDrafts.remove(chatId);
                        
                        // Сбрасываем состояние пользователя
                        user.setState(null);
                        telegramUserRepository.save(user);
                        
                        // Отправляем сообщение об успешном удалении товара
                        SendMessage deleteMessage = new SendMessage();
                        deleteMessage.setChatId(chatId);
                        
                        if (deleted) {
                            deleteMessage.setText("✅ Товар успешно удален!");
                        } else {
                            deleteMessage.setText("❌ Не удалось удалить товар. Возможно, он уже был удален.");
                        }
                        
                        deleteMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
                        
                        return deleteMessage;
                    case 8: // Сохранить и выйти
                        // Сохраняем товар
                        Product savedProduct = productService.updateProduct(
                            product.getId(),
                            product.getName(),
                            product.getDescription(),
                            product.getPrice(),
                            product.getStock(),
                            product.getCategory()
                        );
                        
                        // Очищаем черновик
                        productDrafts.remove(chatId);
                        
                        // Сбрасываем состояние пользователя
                        user.setState(null);
                        telegramUserRepository.save(user);
                        
                        // Отправляем сообщение об успешном сохранении товара
                        SendMessage saveMessage = new SendMessage();
                        saveMessage.setChatId(chatId);
                        saveMessage.setText("Товар успешно сохранен!");
                        saveMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
                        
                        return saveMessage;
                    default:
                        return createTextMessage(chatId, "Некорректный номер поля. Пожалуйста, введите число от 1 до 8:");
                }
            } catch (NumberFormatException e) {
                logger.error("Ошибка при парсинге номера поля: {}", e.getMessage());
                return createTextMessage(chatId, "Некорректный номер поля. Пожалуйста, введите число от 1 до 8:");
            }
        } catch (Exception e) {
            logger.error("Непредвиденная ошибка при обработке выбора поля для редактирования: {}", e.getMessage(), e);
            
            // Сбрасываем состояние пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState(null);
                telegramUserRepository.save(user);
            }
            
            return createTextMessage(chatId, "Произошла непредвиденная ошибка. Пожалуйста, начните редактирование товара заново.");
        }
    }
    
    /**
     * Обрабатывает редактирование названия товара
     * @param chatId ID чата
     * @param name новое название товара
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditingProductName(Long chatId, String name) {
        // Получаем черновик товара
        Product product = productDrafts.get(chatId);
        if (product == null) {
            // Если черновик не найден, пытаемся получить товар из состояния пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user == null) {
                return createTextMessage(chatId, "Пользователь не найден. Пожалуйста, перезапустите бота командой /start");
            }
            
            String state = user.getState();
            if (state == null || !state.startsWith("EDITING_PRODUCT_")) {
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
            }
            
            String productIdStr = state.replace("EDITING_PRODUCT_", "");
            try {
                Long productId = Long.parseLong(productIdStr);
                Optional<Product> productOpt = productService.getProductById(productId);
                
                if (productOpt.isEmpty()) {
                    return createTextMessage(chatId, "Товар не найден. Пожалуйста, начните редактирование товара заново.");
                }
                
                product = productOpt.get();
                productDrafts.put(chatId, product);
                logger.info("Товар получен из базы данных и сохранен в черновики: {}", product.getName());
            } catch (NumberFormatException e) {
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
            }
        }
        
        // Сохраняем старые значения
        String oldName = product.getName();
        
        // Обновляем название товара
        product.setName(name);
        productDrafts.put(chatId, product);
        
        // Возвращаемся к выбору поля для редактирования
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState("EDITING_PRODUCT_" + product.getId());
            telegramUserRepository.save(user);
        }
        
        // Отправляем сообщение с текущими данными товара и предлагаем выбрать, что редактировать
        StringBuilder messageText = new StringBuilder();
        messageText.append("✏️ *Редактирование товара*\n\n");
        messageText.append("Название товара успешно изменено!\n\n");
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
        
        return createTextMessage(chatId, messageText.toString());
    }
    
    /**
     * Обрабатывает редактирование цены товара
     * @param chatId ID чата
     * @param priceText новая цена товара
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditingProductPrice(Long chatId, String priceText) {
        try {
            BigDecimal price = new BigDecimal(priceText);
            
            // Получаем черновик товара
            Product product = productDrafts.get(chatId);
            if (product == null) {
                // Если черновик не найден, пытаемся получить товар из состояния пользователя
                TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
                if (user == null) {
                    return createTextMessage(chatId, "Пользователь не найден. Пожалуйста, перезапустите бота командой /start");
                }
                
                String state = user.getState();
                if (state == null || !state.startsWith("EDITING_PRODUCT_")) {
                    return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
                }
                
                String productIdStr = state.replace("EDITING_PRODUCT_", "");
                try {
                    Long productId = Long.parseLong(productIdStr);
                    Optional<Product> productOpt = productService.getProductById(productId);
                    
                    if (productOpt.isEmpty()) {
                        return createTextMessage(chatId, "Товар не найден. Пожалуйста, начните редактирование товара заново.");
                    }
                    
                    product = productOpt.get();
                    productDrafts.put(chatId, product);
                    logger.info("Товар получен из базы данных и сохранен в черновики: {}", product.getName());
                } catch (NumberFormatException e) {
                    return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
                }
            }
            
            // Обновляем цену товара
            product.setPrice(price);
            productDrafts.put(chatId, product);
            
            // Возвращаемся к выбору поля для редактирования
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState("EDITING_PRODUCT_" + product.getId());
                telegramUserRepository.save(user);
            }
            
            // Отправляем сообщение с текущими данными товара и предлагаем выбрать, что редактировать
            StringBuilder messageText = new StringBuilder();
            messageText.append("✏️ *Редактирование товара*\n\n");
            messageText.append("Цена товара успешно изменена!\n\n");
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
            
            return createTextMessage(chatId, messageText.toString());
        } catch (NumberFormatException e) {
            return createTextMessage(chatId, "Некорректная цена. Пожалуйста, введите число (например, 100 или 99.99):");
        }
    }
    
    /**
     * Обрабатывает редактирование количества товара
     * @param chatId ID чата
     * @param stockText новое количество товара
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditingProductStock(Long chatId, String stockText) {
        try {
            int stock = Integer.parseInt(stockText);
            
            // Получаем черновик товара
            Product product = productDrafts.get(chatId);
            if (product == null) {
                // Если черновик не найден, пытаемся получить товар из состояния пользователя
                TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
                if (user == null) {
                    return createTextMessage(chatId, "Пользователь не найден. Пожалуйста, перезапустите бота командой /start");
                }
                
                String state = user.getState();
                if (state == null || !state.startsWith("EDITING_PRODUCT_")) {
                    return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
                }
                
                String productIdStr = state.replace("EDITING_PRODUCT_", "");
                try {
                    Long productId = Long.parseLong(productIdStr);
                    Optional<Product> productOpt = productService.getProductById(productId);
                    
                    if (productOpt.isEmpty()) {
                        return createTextMessage(chatId, "Товар не найден. Пожалуйста, начните редактирование товара заново.");
                    }
                    
                    product = productOpt.get();
                    productDrafts.put(chatId, product);
                    logger.info("Товар получен из базы данных и сохранен в черновики: {}", product.getName());
                } catch (NumberFormatException e) {
                    return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
                }
            }
            
            // Обновляем количество товара
            product.setStock(stock);
            productDrafts.put(chatId, product);
            
            // Возвращаемся к выбору поля для редактирования
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState("EDITING_PRODUCT_" + product.getId());
                telegramUserRepository.save(user);
            }
            
            // Отправляем сообщение с текущими данными товара и предлагаем выбрать, что редактировать
            StringBuilder messageText = new StringBuilder();
            messageText.append("✏️ *Редактирование товара*\n\n");
            messageText.append("Количество товара успешно изменено!\n\n");
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
            
            return createTextMessage(chatId, messageText.toString());
        } catch (NumberFormatException e) {
            return createTextMessage(chatId, "Некорректное количество. Пожалуйста, введите целое число:");
        }
    }
    
    /**
     * Обрабатывает редактирование категории товара
     * @param chatId ID чата
     * @param categoryText номер категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditingProductCategory(Long chatId, String categoryText) {
        try {
            int categoryIndex = Integer.parseInt(categoryText) - 1;
            List<Category> categories = categoryService.getAllCategories();
            
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                return createTextMessage(chatId, "Некорректный номер категории. Пожалуйста, выберите из списка:");
            }
            
            Category category = categories.get(categoryIndex);
            
            // Получаем черновик товара
            Product product = productDrafts.get(chatId);
            if (product == null) {
                // Если черновик не найден, пытаемся получить товар из состояния пользователя
                TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
                if (user == null) {
                    return createTextMessage(chatId, "Пользователь не найден. Пожалуйста, перезапустите бота командой /start");
                }
                
                String state = user.getState();
                if (state == null || !state.startsWith("EDITING_PRODUCT_")) {
                    return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
                }
                
                String productIdStr = state.replace("EDITING_PRODUCT_", "");
                try {
                    Long productId = Long.parseLong(productIdStr);
                    Optional<Product> productOpt = productService.getProductById(productId);
                    
                    if (productOpt.isEmpty()) {
                        return createTextMessage(chatId, "Товар не найден. Пожалуйста, начните редактирование товара заново.");
                    }
                    
                    product = productOpt.get();
                    productDrafts.put(chatId, product);
                    logger.info("Товар получен из базы данных и сохранен в черновики: {}", product.getName());
                } catch (NumberFormatException e) {
                    return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
                }
            }
            
            // Обновляем категорию товара
            product.setCategory(category);
            productDrafts.put(chatId, product);
            
            // Возвращаемся к выбору поля для редактирования
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState("EDITING_PRODUCT_" + product.getId());
                telegramUserRepository.save(user);
            }
            
            // Отправляем сообщение с текущими данными товара и предлагаем выбрать, что редактировать
            StringBuilder messageText = new StringBuilder();
            messageText.append("✏️ *Редактирование товара*\n\n");
            messageText.append("Категория товара успешно изменена!\n\n");
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
            
            return createTextMessage(chatId, messageText.toString());
        } catch (NumberFormatException e) {
            return createTextMessage(chatId, "Некорректный номер категории. Пожалуйста, введите число:");
        }
    }
    
    /**
     * Обрабатывает редактирование описания товара
     * @param chatId ID чата
     * @param description новое описание товара
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditingProductDescription(Long chatId, String description) {
        // Получаем черновик товара
        Product product = productDrafts.get(chatId);
        if (product == null) {
            // Если черновик не найден, пытаемся получить товар из состояния пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user == null) {
                return createTextMessage(chatId, "Пользователь не найден. Пожалуйста, перезапустите бота командой /start");
            }
            
            String state = user.getState();
            if (state == null || !state.startsWith("EDITING_PRODUCT_")) {
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
            }
            
            String productIdStr = state.replace("EDITING_PRODUCT_", "");
            try {
                Long productId = Long.parseLong(productIdStr);
                Optional<Product> productOpt = productService.getProductById(productId);
                
                if (productOpt.isEmpty()) {
                    return createTextMessage(chatId, "Товар не найден. Пожалуйста, начните редактирование товара заново.");
                }
                
                product = productOpt.get();
                productDrafts.put(chatId, product);
                logger.info("Товар получен из базы данных и сохранен в черновики: {}", product.getName());
            } catch (NumberFormatException e) {
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
            }
        }
        
        // Обновляем описание товара
        product.setDescription(description);
        productDrafts.put(chatId, product);
        
        // Возвращаемся к выбору поля для редактирования
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState("EDITING_PRODUCT_" + product.getId());
            telegramUserRepository.save(user);
        }
        
        // Отправляем сообщение с текущими данными товара и предлагаем выбрать, что редактировать
        StringBuilder messageText = new StringBuilder();
        messageText.append("✏️ *Редактирование товара*\n\n");
        messageText.append("Описание товара успешно изменено!\n\n");
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
        
        return createTextMessage(chatId, messageText.toString());
    }
    
    /**
     * Обрабатывает редактирование изображения товара
     * @param chatId ID чата
     * @param message сообщение с изображением
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditingProductImage(Long chatId, Message message) {
        // Получаем черновик товара
        Product product = productDrafts.get(chatId);
        if (product == null) {
            // Если черновик не найден, пытаемся получить товар из состояния пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user == null) {
                return createTextMessage(chatId, "Пользователь не найден. Пожалуйста, перезапустите бота командой /start");
            }
            
            String state = user.getState();
            if (state == null || !state.startsWith("EDITING_PRODUCT_")) {
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
            }
            
            String productIdStr = state.replace("EDITING_PRODUCT_", "");
            try {
                Long productId = Long.parseLong(productIdStr);
                Optional<Product> productOpt = productService.getProductById(productId);
                
                if (productOpt.isEmpty()) {
                    return createTextMessage(chatId, "Товар не найден. Пожалуйста, начните редактирование товара заново.");
                }
                
                product = productOpt.get();
                productDrafts.put(chatId, product);
                logger.info("Товар получен из базы данных и сохранен в черновики: {}", product.getName());
            } catch (NumberFormatException e) {
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
            }
        }
        
        try {
            // Получаем fileId изображения
            String fileId = message.getPhoto().get(message.getPhoto().size() - 1).getFileId();
            logger.info("Получен fileId изображения: {}", fileId);
            
            // Обновляем изображение товара в базе данных напрямую
            Product updatedProduct = productService.updateProductImage(product.getId(), fileId);
            
            if (updatedProduct == null) {
                logger.error("Не удалось обновить изображение товара с ID {}", product.getId());
                return createTextMessage(chatId, "Произошла ошибка при обновлении изображения товара. Пожалуйста, попробуйте снова.");
            }
            
            // Обновляем черновик товара
            product.setImageUrl(fileId);
            productDrafts.put(chatId, product);
            
            logger.info("Изображение товара '{}' успешно обновлено", product.getName());
            
            // Возвращаемся к выбору поля для редактирования
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState("EDITING_PRODUCT_" + product.getId());
                telegramUserRepository.save(user);
            }
            
            // Отправляем сообщение с текущими данными товара и предлагаем выбрать, что редактировать
            StringBuilder messageText = new StringBuilder();
            messageText.append("✏️ *Редактирование товара*\n\n");
            messageText.append("Изображение товара успешно изменено!\n\n");
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
            
            return createTextMessage(chatId, messageText.toString());
        } catch (Exception e) {
            logger.error("Ошибка при обработке изображения товара: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при обработке изображения. Пожалуйста, попробуйте снова.");
        }
    }
    
    /**
     * Обрабатывает пропуск редактирования изображения товара
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleSkipEditingProductImage(Long chatId) {
        // Получаем черновик товара
        Product product = productDrafts.get(chatId);
        if (product == null) {
            // Если черновик не найден, пытаемся получить товар из состояния пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user == null) {
                return createTextMessage(chatId, "Пользователь не найден. Пожалуйста, перезапустите бота командой /start");
            }
            
            String state = user.getState();
            if (state == null || !state.startsWith("EDITING_PRODUCT_")) {
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
            }
            
            String productIdStr = state.replace("EDITING_PRODUCT_", "");
            try {
                Long productId = Long.parseLong(productIdStr);
                Optional<Product> productOpt = productService.getProductById(productId);
                
                if (productOpt.isEmpty()) {
                    return createTextMessage(chatId, "Товар не найден. Пожалуйста, начните редактирование товара заново.");
                }
                
                product = productOpt.get();
                productDrafts.put(chatId, product);
                logger.info("Товар получен из базы данных и сохранен в черновики: {}", product.getName());
            } catch (NumberFormatException e) {
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование товара заново.");
            }
        }
        
        // Возвращаемся к выбору поля для редактирования
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState("EDITING_PRODUCT_" + product.getId());
            telegramUserRepository.save(user);
        }
        
        // Отправляем сообщение с текущими данными товара и предлагаем выбрать, что редактировать
        StringBuilder messageText = new StringBuilder();
        messageText.append("✏️ *Редактирование товара*\n\n");
        messageText.append("Редактирование изображения пропущено.\n\n");
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
        
        return createTextMessage(chatId, messageText.toString());
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