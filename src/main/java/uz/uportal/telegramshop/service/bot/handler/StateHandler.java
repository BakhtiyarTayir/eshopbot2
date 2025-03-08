package uz.uportal.telegramshop.service.bot.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.model.Product;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.model.CartItem;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.CategoryService;
import uz.uportal.telegramshop.service.ProductService;
import uz.uportal.telegramshop.service.CartService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Обработчик состояний пользователя
 */
@Component
public class StateHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(StateHandler.class);
    
    private final TelegramUserRepository userRepository;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final OrderHandler orderHandler;
    private final CartService cartService;
    @Lazy
    private final CommandHandler commandHandler;
    
    // Временное хранилище данных для создания продукта
    private final Map<Long, Map<String, String>> productCreationData = new HashMap<>();
    
    // Временное хранилище данных для редактирования категории
    private final Map<Long, Map<String, String>> categoryEditData = new HashMap<>();
    
    public StateHandler(TelegramUserRepository userRepository,
                       CategoryService categoryService,
                       ProductService productService,
                       OrderHandler orderHandler,
                       CartService cartService,
                       @Lazy CommandHandler commandHandler) {
        this.userRepository = userRepository;
        this.categoryService = categoryService;
        this.productService = productService;
        this.orderHandler = orderHandler;
        this.cartService = cartService;
        this.commandHandler = commandHandler;
    }
    
    /**
     * Получить данные для создания продукта
     * @return Map с данными для создания продукта
     */
    public Map<Long, Map<String, String>> getProductCreationData() {
        return productCreationData;
    }
    
    /**
     * Обработка состояний пользователя
     * @param message сообщение
     * @return ответ бота
     */
    public BotApiMethod<?> handleState(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();
        
        // Получаем пользователя из базы данных
        TelegramUser user = userRepository.findById(chatId).orElse(null);
        if (user == null) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId);
            errorMessage.setText("Пользователь не найден. Пожалуйста, отправьте /start для начала работы.");
            return errorMessage;
        }
        
        logger.info("Handling state {} for user {}", user.getState(), chatId);
        
        // Обработка состояний пользователя
        switch (user.getState()) {
            case "ADDING_CATEGORY":
                return handleAddingCategory(chatId, text, user);
            case "ADDING_PRODUCT_CATEGORY":
                return handleAddingProductCategory(chatId, text, user);
            case "ADDING_PRODUCT_NAME":
                return handleAddingProductName(chatId, text, user);
            case "ADDING_PRODUCT_DESCRIPTION":
                return handleAddingProductDescription(chatId, text, user);
            case "ADDING_PRODUCT_PRICE":
                return handleAddingProductPrice(chatId, text, user);
            case "ADDING_PRODUCT_IMAGE":
                return handleAddingProductImage(chatId, text, user);
            case "ADDING_PRODUCT_STOCK":
                return handleAddingProductStock(chatId, text, user);
            case "DIRECT_PURCHASE":
            case "CART_CHECKOUT":
            case "ORDERING_PHONE":
                return handleOrderingPhone(chatId, text, user);
            case "ORDERING_ADDRESS":
                return handleOrderingAddress(chatId, text, user);
            case "CONFIRMING_ORDER":
                return handleConfirmingOrder(chatId, text, user);
            case "EDITING_CATEGORY":
                return handleEditingCategory(chatId, text, user);
            default:
                SendMessage message1 = new SendMessage();
                message1.setChatId(chatId);
                message1.setText("Вы написали: " + text + "\nИспользуйте кнопки или команды для взаимодействия с ботом.");
                return message1;
        }
    }
    
    /**
     * Обработка фото, отправленного пользователем
     * @param chatId ID чата
     * @param photos список фотографий
     * @param user пользователь
     * @return ответ бота
     */
    public BotApiMethod<?> handlePhotoUpload(Long chatId, List<PhotoSize> photos, TelegramUser user) {
        logger.info("Handling photo upload for user {}", chatId);
        
        if (user == null) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId);
            errorMessage.setText("Пользователь не найден. Пожалуйста, отправьте /start для начала работы.");
            return errorMessage;
        }
        
        if ("ADDING_PRODUCT_IMAGE".equals(user.getState())) {
            // Получаем фото с наилучшим качеством
            PhotoSize photo = photos.stream()
                .max(Comparator.comparing(PhotoSize::getFileSize))
                .orElse(null);
            
            if (photo == null) {
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(chatId);
                errorMessage.setText("Не удалось получить фото. Пожалуйста, попробуйте еще раз или отправьте URL изображения.");
                return errorMessage;
            }
            
            // Получаем fileId фото
            String fileId = photo.getFileId();
            logger.info("Received photo with fileId: {}", fileId);
            
            // Сохраняем fileId в данных продукта
            Map<String, String> productData = productCreationData.get(chatId);
            if (productData != null) {
                productData.put("imageUrl", fileId);
                
                // Переходим к следующему шагу
                user.setState("ADDING_PRODUCT_STOCK");
                userRepository.save(user);
                
                SendMessage message = new SendMessage();
                message.setChatId(chatId);
                message.setText("Фото сохранено! Теперь введите количество товара на складе:");
                return message;
            } else {
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(chatId);
                errorMessage.setText("Произошла ошибка. Пожалуйста, начните процесс добавления товара заново.");
                
                user.setState("ADMIN_MENU");
                userRepository.save(user);
                return errorMessage;
            }
        }
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Фото получено, но я не знаю, что с ним делать. Пожалуйста, используйте команды для взаимодействия с ботом.");
        return message;
    }
    
    /**
     * Обработка контакта, отправленного пользователем
     * @param chatId ID чата
     * @param phoneNumber номер телефона
     * @param user пользователь
     * @return ответ бота
     */
    public BotApiMethod<?> handlePhoneNumber(Long chatId, String phoneNumber, TelegramUser user) {
        logger.info("Processing phone number for user {}: {}", chatId, phoneNumber);
        
        // Получаем данные заказа
        Map<String, String> orderData = orderHandler.getOrderData(chatId);
        logger.info("Order data for user {}: {}", chatId, orderData);
        
        if (orderData != null) {
            // Сохраняем номер телефона
            orderData.put("phone", phoneNumber);
            
            // Обновляем номер телефона пользователя в базе данных
            user.setPhoneNumber(phoneNumber);
            
            // Переходим к запросу адреса
            user.setState("ORDERING_ADDRESS");
            userRepository.save(user);
            logger.info("Set state to ORDERING_ADDRESS for user {}", chatId);
            
            // Запрашиваем адрес доставки
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Спасибо! Теперь, пожалуйста, введите адрес доставки:");
            
            // Возвращаем обычную клавиатуру
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
        } else {
            // Если данных заказа нет, возвращаем сообщение об ошибке
            logger.error("Order data not found for user {}", chatId);
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Произошла ошибка. Пожалуйста, начните процесс заказа заново.");
            
            // Сбрасываем состояние пользователя
            user.setState("NORMAL");
            userRepository.save(user);
            
            return message;
        }
    }
    
    // Методы обработки состояний
    
    private SendMessage handleAddingCategory(Long chatId, String text, TelegramUser user) {
        logger.info("Handling adding category for user {}: {}", chatId, text);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        try {
            // Добавление новой категории
            Category newCategory = categoryService.createCategory(text);
            logger.info("Category created: {}", newCategory.getName());
            
            message.setText("Категория '" + newCategory.getName() + "' успешно добавлена!");
            user.setState("NORMAL");
            userRepository.save(user);
            return showAdminPanel(chatId);
        } catch (IllegalArgumentException e) {
            // Обработка ошибки пустого имени категории
            logger.error("Error creating category: {}", e.getMessage());
            message.setText("Ошибка: " + e.getMessage() + "\nПожалуйста, введите корректное название категории:");
            return message;
        } catch (Exception e) {
            // Обработка других ошибок
            logger.error("Error creating category", e);
            message.setText("Произошла ошибка при создании категории. Пожалуйста, попробуйте еще раз.");
            user.setState("NORMAL");
            userRepository.save(user);
            return showAdminPanel(chatId);
        }
    }
    
    private SendMessage handleAddingProductCategory(Long chatId, String text, TelegramUser user) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        // Проверяем, не хочет ли пользователь вернуться назад
        if (text.equalsIgnoreCase("Назад") || text.equalsIgnoreCase("Отмена")) {
            user.setState("NORMAL");
            userRepository.save(user);
            message.setText("Добавление товара отменено.");
            return showAdminPanel(chatId);
        }
        
        // Если пользователь ввел текст, но не выбрал категорию через inline-кнопки,
        // показываем список категорий снова
        message.setText("Пожалуйста, выберите категорию из списка, нажав на соответствующую кнопку:");
        return showCategoriesForProductCreation(chatId);
    }
    
    private SendMessage handleAddingProductName(Long chatId, String text, TelegramUser user) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        Map<String, String> productData = productCreationData.get(chatId);
        if (productData != null) {
            productData.put("name", text);
            user.setState("ADDING_PRODUCT_DESCRIPTION");
            userRepository.save(user);
            message.setText("Введите описание товара:");
        } else {
            message.setText("Произошла ошибка. Пожалуйста, начните процесс добавления товара заново.");
            user.setState("ADMIN_MENU");
            userRepository.save(user);
            return showAdminPanel(chatId);
        }
        
        return message;
    }
    
    private SendMessage handleAddingProductDescription(Long chatId, String text, TelegramUser user) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        Map<String, String> productData = productCreationData.get(chatId);
        if (productData != null) {
            productData.put("description", text);
            user.setState("ADDING_PRODUCT_PRICE");
            userRepository.save(user);
            message.setText("Введите цену товара (например, 1000.50):");
        } else {
            message.setText("Произошла ошибка. Пожалуйста, начните процесс добавления товара заново.");
            user.setState("ADMIN_MENU");
            userRepository.save(user);
            return showAdminPanel(chatId);
        }
        
        return message;
    }
    
    private SendMessage handleAddingProductPrice(Long chatId, String text, TelegramUser user) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        Map<String, String> productData = productCreationData.get(chatId);
        if (productData != null) {
            try {
                // Проверяем, что введена корректная цена
                BigDecimal price = new BigDecimal(text.replace(",", "."));
                productData.put("price", price.toString());
                user.setState("ADDING_PRODUCT_IMAGE");
                userRepository.save(user);
                message.setText("Введите URL изображения товара, отправьте фото или напишите 'нет' для пропуска:");
            } catch (NumberFormatException e) {
                message.setText("Пожалуйста, введите корректную цену (например, 1000.50):");
            }
        } else {
            message.setText("Произошла ошибка. Пожалуйста, начните процесс добавления товара заново.");
            user.setState("ADMIN_MENU");
            userRepository.save(user);
            return showAdminPanel(chatId);
        }
        
        return message;
    }
    
    private SendMessage handleAddingProductImage(Long chatId, String text, TelegramUser user) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        Map<String, String> productData = productCreationData.get(chatId);
        if (productData != null) {
            if (text.equalsIgnoreCase("нет")) {
                // Пользователь не хочет добавлять изображение
                user.setState("ADDING_PRODUCT_STOCK");
                userRepository.save(user);
                message.setText("Введите количество товара на складе:");
            } else if (text.startsWith("http")) {
                // Пользователь ввел URL
                productData.put("imageUrl", text);
                user.setState("ADDING_PRODUCT_STOCK");
                userRepository.save(user);
                message.setText("URL изображения сохранен! Теперь введите количество товара на складе:");
            } else {
                // Пользователь ввел что-то другое, просим загрузить фото или URL
                message.setText("Пожалуйста, отправьте фотографию товара, URL изображения (начинающийся с http) или напишите 'нет', чтобы пропустить этот шаг.");
            }
        } else {
            message.setText("Произошла ошибка. Пожалуйста, начните процесс добавления товара заново.");
            user.setState("ADMIN_MENU");
            userRepository.save(user);
            return showAdminPanel(chatId);
        }
        
        return message;
    }
    
    private SendMessage handleAddingProductStock(Long chatId, String text, TelegramUser user) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        Map<String, String> productData = productCreationData.get(chatId);
        if (productData != null) {
            try {
                // Проверяем, что введено корректное количество
                Integer stock = Integer.parseInt(text);
                productData.put("stock", stock.toString());
                
                // Создаем товар
                Long categoryId = Long.parseLong(productData.get("categoryId"));
                Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
                
                if (categoryOpt.isPresent()) {
                    Category category = categoryOpt.get();
                    Product product;
                    
                    if (productData.containsKey("imageUrl")) {
                        product = productService.createProduct(
                            productData.get("name"),
                            productData.get("description"),
                            new BigDecimal(productData.get("price")),
                            productData.get("imageUrl"),
                            Integer.parseInt(productData.get("stock")),
                            category
                        );
                    } else {
                        product = productService.createProduct(
                            productData.get("name"),
                            productData.get("description"),
                            new BigDecimal(productData.get("price")),
                            Integer.parseInt(productData.get("stock")),
                            category
                        );
                    }
                    
                    message.setText("Товар '" + product.getName() + "' успешно добавлен в категорию '" + 
                                   category.getName() + "'!");
                } else {
                    message.setText("Категория не найдена. Товар не был добавлен.");
                }
                
                // Очищаем данные и возвращаемся в админ-панель
                productCreationData.remove(chatId);
                user.setState("ADMIN_MENU");
                userRepository.save(user);
                return showAdminPanel(chatId);
            } catch (NumberFormatException e) {
                message.setText("Пожалуйста, введите корректное количество товара (целое число):");
            }
        } else {
            message.setText("Произошла ошибка. Пожалуйста, начните процесс добавления товара заново.");
            user.setState("ADMIN_MENU");
            userRepository.save(user);
            return showAdminPanel(chatId);
        }
        
        return message;
    }
    
    private SendMessage handleOrderingPhone(Long chatId, String text, TelegramUser user) {
        logger.info("Handling ordering phone for user {}: {}", chatId, text);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        Map<String, String> orderData = orderHandler.getOrderData(chatId);
        logger.info("Order data for user {}: {}", chatId, orderData);
        
        if (orderData != null) {
            // Сохраняем номер телефона
            orderData.put("phone", text);
            
            // Обновляем номер телефона пользователя в базе данных
            user.setPhoneNumber(text);
            
            // Переходим к запросу адреса
            user.setState("ORDERING_ADDRESS");
            userRepository.save(user);
            logger.info("Set state to ORDERING_ADDRESS for user {}", chatId);
            
            // Запрашиваем адрес доставки
            message.setText("Спасибо! Теперь, пожалуйста, введите адрес доставки:");
            
            // Возвращаем обычную клавиатуру
            ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
            List<KeyboardRow> keyboard = new ArrayList<>();
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton("Отмена"));
            keyboard.add(row);
            keyboardMarkup.setKeyboard(keyboard);
            keyboardMarkup.setResizeKeyboard(true);
            keyboardMarkup.setOneTimeKeyboard(true);
            message.setReplyMarkup(keyboardMarkup);
        } else {
            logger.error("Order data not found for user {}", chatId);
            message.setText("Произошла ошибка. Пожалуйста, начните процесс заказа заново.");
            user.setState("NORMAL");
            userRepository.save(user);
        }
        
        return message;
    }
    
    private SendMessage handleOrderingAddress(Long chatId, String text, TelegramUser user) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        logger.info("Handling ordering address for user {}: {}", chatId, text);
        
        Map<String, String> orderData = orderHandler.getOrderData(chatId);
        logger.info("Order data for user {}: {}", chatId, orderData);
        
        if (orderData != null) {
            // Если пользователь отменил заказ
            if (text.equalsIgnoreCase("Отмена")) {
                orderHandler.removeOrderData(chatId);
                user.setState("NORMAL");
                userRepository.save(user);
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
            
            // Сохраняем адрес
            orderData.put("address", text);
            logger.info("Saved address for user {}: {}", chatId, text);
            
            // Проверяем, заказ из корзины или прямая покупка
            if (orderData.containsKey("fromCart") && "true".equals(orderData.get("fromCart"))) {
                logger.info("Processing cart checkout for user {}", chatId);
                
                // Получаем товары из корзины
                List<CartItem> cartItems = cartService.getCartItems(user);
                if (cartItems.isEmpty()) {
                    logger.error("Cart is empty for user {}", chatId);
                    message.setText("Ваша корзина пуста. Добавьте товары из каталога.");
                    user.setState("NORMAL");
                    userRepository.save(user);
                    return message;
                }
                
                // Формируем сообщение с подтверждением заказа
                StringBuilder confirmationText = new StringBuilder();
                confirmationText.append("Пожалуйста, проверьте данные заказа:\n\n");
                confirmationText.append("Товары в корзине:\n");
                
                BigDecimal totalPrice = BigDecimal.ZERO;
                for (CartItem item : cartItems) {
                    confirmationText.append("- ").append(item.getProduct().getName())
                            .append(" (").append(item.getQuantity()).append(" шт.) - ")
                            .append(item.getTotalPrice()).append(" $\n");
                    totalPrice = totalPrice.add(item.getTotalPrice());
                }
                
                confirmationText.append("\nОбщая сумма: ").append(totalPrice).append(" $\n\n");
                confirmationText.append("Ваш телефон: ").append(orderData.get("phone")).append("\n");
                confirmationText.append("Адрес доставки: ").append(orderData.get("address")).append("\n\n");
                confirmationText.append("Всё верно? Подтвердите заказ или отмените его.");
                
                message.setText(confirmationText.toString());
                
                // Добавляем кнопки подтверждения и отмены
                InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
                
                // Кнопка "Подтвердить заказ"
                List<InlineKeyboardButton> confirmRow = new ArrayList<>();
                InlineKeyboardButton confirmButton = new InlineKeyboardButton();
                confirmButton.setText("✅ Подтвердить заказ");
                confirmButton.setCallbackData("confirm_order");
                confirmRow.add(confirmButton);
                rowsInline.add(confirmRow);
                
                // Кнопка "Отменить заказ"
                List<InlineKeyboardButton> cancelRow = new ArrayList<>();
                InlineKeyboardButton cancelButton = new InlineKeyboardButton();
                cancelButton.setText("❌ Отменить заказ");
                cancelButton.setCallbackData("cancel_order");
                cancelRow.add(cancelButton);
                rowsInline.add(cancelRow);
                
                markupInline.setKeyboard(rowsInline);
                message.setReplyMarkup(markupInline);
                
                // Устанавливаем состояние ожидания подтверждения
                user.setState("CONFIRMING_ORDER");
                userRepository.save(user);
                logger.info("Set state to CONFIRMING_ORDER for user {}", chatId);
                
                return message;
            } else if (orderData.containsKey("productId")) {
                // Прямая покупка одного товара
                Long productId = Long.parseLong(orderData.get("productId"));
                logger.info("Product ID for user {}: {}", chatId, productId);
                
                Optional<Product> productOpt = productService.getProductById(productId);
                logger.info("Product found for user {}: {}", chatId, productOpt.isPresent());
                
                if (productOpt.isPresent()) {
                    Product product = productOpt.get();
                    
                    // Формируем сообщение с подтверждением заказа
                    StringBuilder confirmationText = new StringBuilder();
                    confirmationText.append("Пожалуйста, проверьте данные заказа:\n\n");
                    confirmationText.append("Товар: ").append(product.getName()).append("\n");
                    confirmationText.append("Цена: ").append(product.getPrice()).append(" $\n\n");
                    confirmationText.append("Ваш телефон: ").append(orderData.get("phone")).append("\n");
                    confirmationText.append("Адрес доставки: ").append(orderData.get("address")).append("\n\n");
                    confirmationText.append("Всё верно? Подтвердите заказ или отмените его.");
                    
                    message.setText(confirmationText.toString());
                    
                    // Добавляем кнопки подтверждения и отмены
                    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
                    
                    // Кнопка "Подтвердить заказ"
                    List<InlineKeyboardButton> confirmRow = new ArrayList<>();
                    InlineKeyboardButton confirmButton = new InlineKeyboardButton();
                    confirmButton.setText("✅ Подтвердить заказ");
                    confirmButton.setCallbackData("confirm_order");
                    confirmRow.add(confirmButton);
                    rowsInline.add(confirmRow);
                    
                    // Кнопка "Отменить заказ"
                    List<InlineKeyboardButton> cancelRow = new ArrayList<>();
                    InlineKeyboardButton cancelButton = new InlineKeyboardButton();
                    cancelButton.setText("❌ Отменить заказ");
                    cancelButton.setCallbackData("cancel_order");
                    cancelRow.add(cancelButton);
                    rowsInline.add(cancelRow);
                    
                    markupInline.setKeyboard(rowsInline);
                    message.setReplyMarkup(markupInline);
                    
                    // Устанавливаем состояние ожидания подтверждения
                    user.setState("CONFIRMING_ORDER");
                    userRepository.save(user);
                    logger.info("Set state to CONFIRMING_ORDER for user {}", chatId);
                } else {
                    message.setText("Товар не найден. Пожалуйста, начните процесс заказа заново.");
                    orderHandler.removeOrderData(chatId);
                    user.setState("NORMAL");
                    userRepository.save(user);
                    logger.info("Product not found for user {}, reset state to NORMAL", chatId);
                }
            } else {
                message.setText("Неизвестный тип заказа. Пожалуйста, начните процесс заказа заново.");
                orderHandler.removeOrderData(chatId);
                user.setState("NORMAL");
                userRepository.save(user);
                logger.info("Unknown order type for user {}, reset state to NORMAL", chatId);
            }
        } else {
            message.setText("Произошла ошибка. Пожалуйста, начните процесс заказа заново.");
            user.setState("NORMAL");
            userRepository.save(user);
            logger.info("Order data not found for user {}, reset state to NORMAL", chatId);
        }
        
        return message;
    }
    
    private SendMessage handleConfirmingOrder(Long chatId, String text, TelegramUser user) {
        logger.info("Handling confirming order for user {}: {}", chatId, text);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Пожалуйста, используйте кнопки для подтверждения или отмены заказа.");
        
        // Добавляем кнопки подтверждения и отмены
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        
        // Кнопка "Подтвердить заказ"
        List<InlineKeyboardButton> confirmRow = new ArrayList<>();
        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("✅ Подтвердить заказ");
        confirmButton.setCallbackData("confirm_order");
        confirmRow.add(confirmButton);
        rowsInline.add(confirmRow);
        
        // Кнопка "Отменить заказ"
        List<InlineKeyboardButton> cancelRow = new ArrayList<>();
        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отменить заказ");
        cancelButton.setCallbackData("cancel_order");
        cancelRow.add(cancelButton);
        rowsInline.add(cancelRow);
        
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);
        
        return message;
    }
    
    /**
     * Показать админ-панель
     * @param chatId ID чата
     * @return сообщение с админ-панелью
     */
    private SendMessage showAdminPanel(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        // Создаем клавиатуру для админ-панели
        ReplyKeyboardMarkup adminKeyboard = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Добавить категорию"));
        row1.add(new KeyboardButton("Добавить товар"));
        keyboard.add(row1);
        
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Управление заказами"));
        row2.add(new KeyboardButton("Список категорий"));
        keyboard.add(row2);
        
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
     * Показать список категорий для создания товара
     * @param chatId ID чата
     * @return сообщение со списком категорий
     */
    private SendMessage showCategoriesForProductCreation(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        List<Category> categories = categoryService.getAllCategories();
        
        if (categories.isEmpty()) {
            message.setText("Нет доступных категорий. Сначала создайте хотя бы одну категорию.");
            return showAdminPanel(chatId);
        }
        
        message.setText("Выберите категорию для нового товара:");
        
        // Создаем inline-клавиатуру с кнопками категорий
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        
        // Добавляем кнопки категорий (по 2 в ряд)
        for (int i = 0; i < categories.size(); i += 2) {
            List<InlineKeyboardButton> rowInline = new ArrayList<>();
            
            // Добавляем первую категорию в ряду
            Category category1 = categories.get(i);
            InlineKeyboardButton button1 = new InlineKeyboardButton();
            button1.setText(category1.getName());
            button1.setCallbackData("select_category:" + category1.getId());
            rowInline.add(button1);
            
            // Добавляем вторую категорию в ряду, если она есть
            if (i + 1 < categories.size()) {
                Category category2 = categories.get(i + 1);
                InlineKeyboardButton button2 = new InlineKeyboardButton();
                button2.setText(category2.getName());
                button2.setCallbackData("select_category:" + category2.getId());
                rowInline.add(button2);
            }
            
            rowsInline.add(rowInline);
        }
        
        // Добавляем кнопку "Назад"
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("◀️ Назад");
        backButton.setCallbackData("admin");
        backRow.add(backButton);
        rowsInline.add(backRow);
        
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);
        
        return message;
    }
    
    /**
     * Публичный метод для показа списка категорий при добавлении товара
     * @param chatId ID чата
     * @return сообщение со списком категорий
     */
    public SendMessage showCategoriesForProductAddition(Long chatId) {
        return showCategoriesForProductCreation(chatId);
    }
    
    /**
     * Сохранить данные категории для редактирования
     * @param chatId ID чата
     * @param data данные категории
     */
    public void saveCategoryData(Long chatId, Map<String, String> data) {
        categoryEditData.put(chatId, data);
    }
    
    /**
     * Получить данные категории для редактирования
     * @param chatId ID чата
     * @return данные категории
     */
    public Map<String, String> getCategoryData(Long chatId) {
        return categoryEditData.get(chatId);
    }
    
    /**
     * Удалить данные категории для редактирования
     * @param chatId ID чата
     */
    public void removeCategoryData(Long chatId) {
        categoryEditData.remove(chatId);
    }
    
    private SendMessage handleEditingCategory(Long chatId, String text, TelegramUser user) {
        logger.info("Handling editing category for user {}: {}", chatId, text);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        // Если пользователь отменил редактирование
        if (text.equalsIgnoreCase("Отмена")) {
            user.setState("NORMAL");
            userRepository.save(user);
            removeCategoryData(chatId);
            message.setText("Редактирование категории отменено.");
            
            // Удаляем клавиатуру с кнопкой "Отмена"
            ReplyKeyboardRemove keyboardRemove = new ReplyKeyboardRemove();
            keyboardRemove.setRemoveKeyboard(true);
            message.setReplyMarkup(keyboardRemove);
            
            return showAdminPanel(chatId);
        }
        
        try {
            // Получаем данные категории
            Map<String, String> categoryData = getCategoryData(chatId);
            if (categoryData == null || !categoryData.containsKey("categoryId")) {
                message.setText("Произошла ошибка. Пожалуйста, попробуйте еще раз.");
                user.setState("NORMAL");
                userRepository.save(user);
                return showAdminPanel(chatId);
            }
            
            // Получаем ID категории
            Long categoryId = Long.parseLong(categoryData.get("categoryId"));
            
            // Получаем категорию из базы данных
            Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
            if (categoryOpt.isEmpty()) {
                message.setText("Категория не найдена. Пожалуйста, попробуйте еще раз.");
                user.setState("NORMAL");
                userRepository.save(user);
                return showAdminPanel(chatId);
            }
            
            Category category = categoryOpt.get();
            
            // Обновляем название категории
            category.setName(text);
            
            // Генерируем slug вручную, так как метод generateSlug приватный
            String slug = text.toLowerCase()
                          .replaceAll("[^a-zA-Z0-9\\s]", "")
                          .replaceAll("\\s+", "-");
            
            // Если slug пустой, используем timestamp
            if (slug.trim().isEmpty()) {
                slug = "category-" + System.currentTimeMillis();
            }
            
            category.setSlug(slug);
            categoryService.updateCategory(category);
            
            message.setText("Категория успешно обновлена!");
            user.setState("NORMAL");
            userRepository.save(user);
            removeCategoryData(chatId);
            
            // Удаляем клавиатуру с кнопкой "Отмена"
            ReplyKeyboardRemove keyboardRemove = new ReplyKeyboardRemove();
            keyboardRemove.setRemoveKeyboard(true);
            
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
            message.setReplyMarkup(keyboardMarkup);
            
            return message;
        } catch (Exception e) {
            logger.error("Error updating category", e);
            message.setText("Произошла ошибка при обновлении категории. Пожалуйста, попробуйте еще раз.");
            user.setState("NORMAL");
            userRepository.save(user);
            removeCategoryData(chatId);
            
            // Удаляем клавиатуру
            ReplyKeyboardRemove keyboardRemove = new ReplyKeyboardRemove();
            keyboardRemove.setRemoveKeyboard(true);
            message.setReplyMarkup(keyboardRemove);
            
            return showAdminPanel(chatId);
        }
    }
} 