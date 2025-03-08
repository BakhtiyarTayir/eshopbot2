package uz.uportal.telegramshop.service.bot.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.uportal.telegramshop.model.Order;
import uz.uportal.telegramshop.model.Product;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.OrderService;
import uz.uportal.telegramshop.service.ProductService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Обработчик заказов
 */
@Component
public class OrderHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderHandler.class);
    
    private final OrderService orderService;
    private final ProductService productService;
    private final TelegramUserRepository userRepository;
    
    // Временное хранилище данных для создания заказа
    private final Map<Long, Map<String, String>> orderCreationData = new HashMap<>();
    
    public OrderHandler(OrderService orderService, 
                       ProductService productService,
                       TelegramUserRepository userRepository) {
        this.orderService = orderService;
        this.productService = productService;
        this.userRepository = userRepository;
    }
    
    /**
     * Показать заказы пользователя
     * @param chatId ID чата
     * @return сообщение со списком заказов
     */
    public SendMessage showUserOrders(Long chatId) {
        logger.info("Showing orders for user {}", chatId);
        
        TelegramUser user = userRepository.findById(chatId).orElse(null);
        if (user == null) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId);
            errorMessage.setText("Пользователь не найден.");
            return errorMessage;
        }
        
        List<Order> orders = orderService.getUserOrders(user);
        
        if (orders.isEmpty()) {
            SendMessage emptyMessage = new SendMessage();
            emptyMessage.setChatId(chatId);
            emptyMessage.setText("У вас пока нет заказов.");
            return emptyMessage;
        }
        
        StringBuilder messageText = new StringBuilder();
        messageText.append("Ваши заказы:\n\n");
        
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        
        for (Order order : orders) {
            // Добавляем кнопку для каждого заказа
            List<InlineKeyboardButton> orderRow = new ArrayList<>();
            InlineKeyboardButton orderButton = new InlineKeyboardButton();
            
            String statusEmoji = order.getStatusEmoji();
            String orderInfo = String.format(
                "Заказ #%d - %s %s - %s",
                order.getId(),
                statusEmoji,
                order.getStatusText(),
                order.getFormattedCreatedAt()
            );
            
            orderButton.setText(orderInfo);
            orderButton.setCallbackData("order_details:" + order.getId());
            orderRow.add(orderButton);
            rowsInline.add(orderRow);
        }
        
        // Добавляем кнопку "Назад"
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Назад");
        backButton.setCallbackData("back");
        backRow.add(backButton);
        rowsInline.add(backRow);
        
        markupInline.setKeyboard(rowsInline);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText.toString());
        message.setReplyMarkup(markupInline);
        
        return message;
    }
    
    /**
     * Показать все заказы (для админов)
     * @param chatId ID чата
     * @return сообщение со списком всех заказов
     */
    public SendMessage showAllOrders(Long chatId) {
        logger.info("Showing all orders for admin {}", chatId);
        
        TelegramUser user = userRepository.findById(chatId).orElse(null);
        if (user == null || !user.isAdmin()) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId);
            errorMessage.setText("У вас нет прав для просмотра всех заказов.");
            return errorMessage;
        }
        
        List<Order> orders = orderService.getAllOrders();
        
        if (orders.isEmpty()) {
            SendMessage emptyMessage = new SendMessage();
            emptyMessage.setChatId(chatId);
            emptyMessage.setText("Заказов пока нет.");
            return emptyMessage;
        }
        
        StringBuilder messageText = new StringBuilder();
        messageText.append("Все заказы:\n\n");
        
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        
        for (Order order : orders) {
            // Добавляем кнопку для каждого заказа
            List<InlineKeyboardButton> orderRow = new ArrayList<>();
            InlineKeyboardButton orderButton = new InlineKeyboardButton();
            
            String statusEmoji = order.getStatusEmoji();
            String userName = order.getUser().getFirstName();
            if (order.getUser().getLastName() != null) {
                userName += " " + order.getUser().getLastName();
            }
            
            String orderInfo = String.format(
                "Заказ #%d - %s %s - %s - %s",
                order.getId(),
                statusEmoji,
                order.getStatusText(),
                userName,
                order.getFormattedCreatedAt()
            );
            
            orderButton.setText(orderInfo);
            orderButton.setCallbackData("order_details:" + order.getId());
            orderRow.add(orderButton);
            rowsInline.add(orderRow);
        }
        
        // Добавляем кнопку "Назад"
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Назад");
        backButton.setCallbackData("admin");
        backRow.add(backButton);
        rowsInline.add(backRow);
        
        markupInline.setKeyboard(rowsInline);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText.toString());
        message.setReplyMarkup(markupInline);
        
        return message;
    }
    
    /**
     * Показать детали заказа
     * @param chatId ID чата
     * @param orderId ID заказа
     * @return сообщение с деталями заказа
     */
    public SendMessage showOrderDetails(Long chatId, Long orderId) {
        logger.info("Showing order details for order {} to user {}", orderId, chatId);
        
        TelegramUser user = userRepository.findById(chatId).orElse(null);
        if (user == null) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId);
            errorMessage.setText("Пользователь не найден.");
            return errorMessage;
        }
        
        Optional<Order> orderOpt = orderService.getOrderById(orderId);
        if (!orderOpt.isPresent()) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId);
            errorMessage.setText("Заказ не найден.");
            return errorMessage;
        }
        
        Order order = orderOpt.get();
        
        // Проверяем права доступа: пользователь может видеть только свои заказы, админ - все
        if (!user.isAdmin() && !order.getUser().getChatId().equals(chatId)) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId);
            errorMessage.setText("У вас нет прав для просмотра этого заказа.");
            return errorMessage;
        }
        
        // Формируем текст сообщения с деталями заказа
        StringBuilder messageText = new StringBuilder();
        messageText.append("Детали заказа #").append(order.getId()).append("\n\n");
        messageText.append("Статус: ").append(order.getStatusEmoji()).append(" ").append(order.getStatusText()).append("\n");
        messageText.append("Дата создания: ").append(order.getFormattedCreatedAt()).append("\n");
        messageText.append("Последнее обновление: ").append(order.getFormattedUpdatedAt()).append("\n\n");
        
        messageText.append("Товар: ").append(order.getProduct().getName()).append("\n");
        messageText.append("Артикул: ").append(String.format("%08d", order.getProduct().getId())).append("\n");
        messageText.append("Цена: ").append(order.getProduct().getPrice()).append(" $\n");
        messageText.append("Количество: ").append(order.getQuantity()).append("\n");
        messageText.append("Итого: ").append(order.getTotalPrice()).append(" $\n\n");
        
        messageText.append("Клиент: ").append(order.getUser().getFirstName());
        if (order.getUser().getLastName() != null) {
            messageText.append(" ").append(order.getUser().getLastName());
        }
        messageText.append("\n");
        messageText.append("Телефон: ").append(order.getPhoneNumber()).append("\n");
        messageText.append("Адрес: ").append(order.getAddress()).append("\n");
        
        if (order.getComment() != null && !order.getComment().isEmpty()) {
            messageText.append("\nКомментарий: ").append(order.getComment()).append("\n");
        }
        
        // Создаем клавиатуру с кнопками действий
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        
        // Если пользователь админ, добавляем кнопки управления заказом
        if (user.isAdmin()) {
            // Кнопки зависят от текущего статуса заказа
            if ("NEW".equals(order.getStatus())) {
                // Кнопка "Принять заказ"
                List<InlineKeyboardButton> acceptRow = new ArrayList<>();
                InlineKeyboardButton acceptButton = new InlineKeyboardButton();
                acceptButton.setText("✅ Принять заказ");
                acceptButton.setCallbackData("accept_order:" + order.getId());
                acceptRow.add(acceptButton);
                rowsInline.add(acceptRow);
            }
            
            if ("PROCESSING".equals(order.getStatus())) {
                // Кнопка "Выполнить заказ"
                List<InlineKeyboardButton> completeRow = new ArrayList<>();
                InlineKeyboardButton completeButton = new InlineKeyboardButton();
                completeButton.setText("✅ Выполнить заказ");
                completeButton.setCallbackData("complete_order:" + order.getId());
                completeRow.add(completeButton);
                rowsInline.add(completeRow);
            }
            
            if (!"COMPLETED".equals(order.getStatus()) && !"CANCELLED".equals(order.getStatus())) {
                // Кнопка "Отменить заказ"
                List<InlineKeyboardButton> cancelRow = new ArrayList<>();
                InlineKeyboardButton cancelButton = new InlineKeyboardButton();
                cancelButton.setText("❌ Отменить заказ");
                cancelButton.setCallbackData("admin_cancel_order:" + order.getId());
                cancelRow.add(cancelButton);
                rowsInline.add(cancelRow);
            }
            
            // Кнопка "Все заказы"
            List<InlineKeyboardButton> allOrdersRow = new ArrayList<>();
            InlineKeyboardButton allOrdersButton = new InlineKeyboardButton();
            allOrdersButton.setText("📋 Все заказы");
            allOrdersButton.setCallbackData("all_orders");
            allOrdersRow.add(allOrdersButton);
            rowsInline.add(allOrdersRow);
        } else {
            // Для обычного пользователя
            // Кнопка "Мои заказы"
            List<InlineKeyboardButton> myOrdersRow = new ArrayList<>();
            InlineKeyboardButton myOrdersButton = new InlineKeyboardButton();
            myOrdersButton.setText("📋 Мои заказы");
            myOrdersButton.setCallbackData("my_orders");
            myOrdersRow.add(myOrdersButton);
            rowsInline.add(myOrdersRow);
        }
        
        // Кнопка "Назад"
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Назад");
        backButton.setCallbackData(user.isAdmin() ? "admin" : "back");
        backRow.add(backButton);
        rowsInline.add(backRow);
        
        markupInline.setKeyboard(rowsInline);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText.toString());
        message.setReplyMarkup(markupInline);
        
        return message;
    }
    
    /**
     * Создать заказ
     * @param user пользователь
     * @param productId ID товара
     * @param phoneNumber номер телефона
     * @param address адрес доставки
     * @return созданный заказ
     */
    public Order createOrder(TelegramUser user, Long productId, String phoneNumber, String address) {
        return createOrder(user, productId, phoneNumber, address, 1);
    }
    
    /**
     * Создать заказ с указанным количеством товара
     * @param user пользователь
     * @param productId ID товара
     * @param phoneNumber номер телефона
     * @param address адрес доставки
     * @param quantity количество товара
     * @return созданный заказ
     */
    public Order createOrder(TelegramUser user, Long productId, String phoneNumber, String address, int quantity) {
        logger.info("Creating order for user {} and product {} with quantity {}", user.getChatId(), productId, quantity);
        
        Optional<Product> productOpt = productService.getProductById(productId);
        if (!productOpt.isPresent()) {
            logger.error("Product not found: {}", productId);
            return null;
        }
        
        Product product = productOpt.get();
        
        // Проверяем наличие товара
        if (product.getStock() < quantity) {
            logger.error("Not enough stock for product {}: {} < {}", productId, product.getStock(), quantity);
            return null;
        }
        
        // Создаем заказ
        Order order = orderService.createOrder(user, product, quantity, address, phoneNumber);
        logger.info("Order created: {}", order.getId());
        
        return order;
    }
    
    /**
     * Сохранить данные для создания заказа
     * @param chatId ID чата
     * @param productId ID товара
     */
    public void saveOrderData(Long chatId, Long productId) {
        Map<String, String> orderData = new HashMap<>();
        orderData.put("productId", productId.toString());
        orderCreationData.put(chatId, orderData);
    }
    
    /**
     * Сохранить данные для создания заказа
     * @param chatId ID чата
     * @param orderData данные заказа
     */
    public void saveOrderData(Long chatId, Map<String, String> orderData) {
        orderCreationData.put(chatId, orderData);
    }
    
    /**
     * Получить данные для создания заказа
     * @param chatId ID чата
     * @return данные для создания заказа
     */
    public Map<String, String> getOrderData(Long chatId) {
        return orderCreationData.get(chatId);
    }
    
    /**
     * Удалить данные для создания заказа
     * @param chatId ID чата
     */
    public void removeOrderData(Long chatId) {
        orderCreationData.remove(chatId);
    }
    
    /**
     * Обновить статус заказа
     * @param orderId ID заказа
     * @param status новый статус
     * @return обновленный заказ
     */
    public Order updateOrderStatus(Long orderId, String status) {
        return orderService.updateOrderStatus(orderId, status);
    }
    
    /**
     * Отменить заказ
     * @param orderId ID заказа
     * @return отмененный заказ
     */
    public Order cancelOrder(Long orderId) {
        return orderService.cancelOrder(orderId);
    }
} 