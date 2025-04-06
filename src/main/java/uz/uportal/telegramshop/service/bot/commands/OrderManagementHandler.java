package uz.uportal.telegramshop.service.bot.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import uz.uportal.telegramshop.model.Order;
import uz.uportal.telegramshop.model.OrderItem;
import uz.uportal.telegramshop.model.OrderStatus;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.OrderService;
import uz.uportal.telegramshop.service.bot.core.MessageSender;
import uz.uportal.telegramshop.service.bot.core.UpdateHandler;
import uz.uportal.telegramshop.service.bot.keyboards.KeyboardFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Обработчик для управления заказами в админ-панели
 */
@Component
public class OrderManagementHandler implements UpdateHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderManagementHandler.class);
    
    private final TelegramUserRepository telegramUserRepository;
    private final OrderService orderService;
    private final KeyboardFactory keyboardFactory;
    private final MessageSender messageSender;
    
    private static final int ORDERS_PAGE_SIZE = 5;
    
    public OrderManagementHandler(
            TelegramUserRepository telegramUserRepository,
            OrderService orderService,
            KeyboardFactory keyboardFactory,
            MessageSender messageSender) {
        this.telegramUserRepository = telegramUserRepository;
        this.orderService = orderService;
        this.keyboardFactory = keyboardFactory;
        this.messageSender = messageSender;
    }
    
    @Override
    public boolean canHandle(Update update) {
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            return callbackData.startsWith("orders_") || 
                   callbackData.startsWith("order_status_") ||
                   callbackData.startsWith("order_details_") ||
                   callbackData.equals("orders_all") ||
                   callbackData.equals("orders_new") ||
                   callbackData.equals("orders_processing") ||
                   callbackData.equals("orders_completed") ||
                   callbackData.equals("orders_cancelled");
        }
        return false;
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
        
        logger.info("Handling order management callback: {} for chatId: {}", callbackData, chatId);
        
        try {
            // Проверяем доступ пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user == null || (!user.getRole().equals("ADMIN") && !user.getRole().equals("MANAGER"))) {
                return createTextMessage(chatId, "У вас нет доступа к управлению заказами.");
            }
            
            if (callbackData.equals("orders_all")) {
                return handleAllOrders(chatId, messageId, 1);
            } else if (callbackData.equals("orders_new")) {
                return handleOrdersByStatus(chatId, messageId, OrderStatus.NEW, 1);
            } else if (callbackData.equals("orders_processing")) {
                return handleOrdersByStatus(chatId, messageId, OrderStatus.PROCESSING, 1);
            } else if (callbackData.equals("orders_completed")) {
                return handleOrdersByStatus(chatId, messageId, OrderStatus.COMPLETED, 1);
            } else if (callbackData.equals("orders_cancelled")) {
                return handleOrdersByStatus(chatId, messageId, OrderStatus.CANCELLED, 1);
            } else if (callbackData.startsWith("orders_page_")) {
                return handleOrdersPage(chatId, messageId, callbackData);
            } else if (callbackData.startsWith("order_details_")) {
                return handleOrderDetails(chatId, messageId, callbackData);
            } else if (callbackData.startsWith("order_status_")) {
                return handleChangeOrderStatus(chatId, messageId, callbackData);
            }
            
            return null;
        } catch (Exception e) {
            logger.error("Error handling order management callback: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при обработке запроса. Пожалуйста, попробуйте еще раз.");
        }
    }
    
    /**
     * Обрабатывает отображение всех заказов
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param page номер страницы
     * @return ответ бота
     */
    private BotApiMethod<?> handleAllOrders(Long chatId, Integer messageId, int page) {
        Pageable pageable = PageRequest.of(page - 1, ORDERS_PAGE_SIZE);
        Page<Order> ordersPage = orderService.getAllOrders(pageable);
        
        if (ordersPage.isEmpty()) {
            return createEditMessage(chatId, messageId, "Заказы не найдены.", createFilterOrdersKeyboard());
        }
        
        return displayOrdersList(chatId, messageId, ordersPage, page, "Все заказы", null);
    }
    
    /**
     * Обрабатывает отображение заказов по статусу
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param status статус заказов
     * @param page номер страницы
     * @return ответ бота
     */
    private BotApiMethod<?> handleOrdersByStatus(Long chatId, Integer messageId, OrderStatus status, int page) {
        Pageable pageable = PageRequest.of(page - 1, ORDERS_PAGE_SIZE);
        Page<Order> ordersPage = orderService.getOrdersByStatus(status, pageable);
        
        if (ordersPage.isEmpty()) {
            return createEditMessage(chatId, messageId, "Заказы со статусом " + getStatusText(status) + " не найдены.", createFilterOrdersKeyboard());
        }
        
        return displayOrdersList(chatId, messageId, ordersPage, page, "Заказы: " + getStatusText(status), status);
    }
    
    /**
     * Обрабатывает пагинацию по заказам
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param callbackData данные колбэка
     * @return ответ бота
     */
    private BotApiMethod<?> handleOrdersPage(Long chatId, Integer messageId, String callbackData) {
        try {
            // Ожидаемый формат: orders_page_[status]_[page]
            // Примеры: orders_page_all_2, orders_page_new_3, ...
            String[] parts = callbackData.split("_");
            
            if (parts.length < 4) {
                return createTextMessage(chatId, "Неверный формат данных для пагинации.");
            }
            
            String statusStr = parts[2];
            int page = Integer.parseInt(parts[3]);
            
            if (statusStr.equals("all")) {
                return handleAllOrders(chatId, messageId, page);
            } else {
                OrderStatus status = OrderStatus.valueOf(statusStr.toUpperCase());
                return handleOrdersByStatus(chatId, messageId, status, page);
            }
        } catch (Exception e) {
            logger.error("Error parsing pagination data: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при обработке пагинации.");
        }
    }
    
    /**
     * Обрабатывает просмотр деталей заказа
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param callbackData данные колбэка
     * @return ответ бота
     */
    private BotApiMethod<?> handleOrderDetails(Long chatId, Integer messageId, String callbackData) {
        try {
            // Ожидаемый формат: order_details_[orderId]
            Long orderId = Long.parseLong(callbackData.replace("order_details_", ""));
            
            Optional<Order> orderOpt = orderService.getOrderById(orderId);
            if (orderOpt.isEmpty()) {
                return createEditMessage(chatId, messageId, "Заказ не найден.", createBackToOrdersKeyboard());
            }
            
            Order order = orderOpt.get();
            
            StringBuilder messageText = new StringBuilder();
            messageText.append("📋 *Детали заказа #").append(order.getId()).append("*\n\n");
            messageText.append("📅 Дата: ").append(order.getCreatedAt().toString().replace("T", " ").substring(0, 16)).append("\n");
            messageText.append("👤 Клиент: ").append(order.getUser().getFirstName());
            if (order.getUser().getLastName() != null) {
                messageText.append(" ").append(order.getUser().getLastName());
            }
            messageText.append("\n");
            messageText.append("📱 Телефон: ").append(order.getPhoneNumber()).append("\n");
            messageText.append("🏠 Адрес: ").append(order.getAddress()).append("\n");
            messageText.append("📝 Комментарий: ").append(order.getComment() != null ? order.getComment() : "Нет").append("\n");
            messageText.append("🏷 Статус: ").append(getStatusText(order.getStatus())).append("\n\n");
            
            messageText.append("📦 *Товары:*\n");
            for (OrderItem item : order.getItems()) {
                messageText.append("- ").append(item.getProductName())
                           .append(" x").append(item.getQuantity())
                           .append(" = ").append(item.getTotalPrice()).append(" руб.\n");
            }
            
            messageText.append("\n💰 *Итого: ").append(order.getTotalAmount()).append(" руб.*");
            
            return createEditMessage(chatId, messageId, messageText.toString(), createOrderDetailsKeyboard(order));
        } catch (Exception e) {
            logger.error("Error displaying order details: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при отображении деталей заказа.");
        }
    }
    
    /**
     * Обрабатывает изменение статуса заказа
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param callbackData данные колбэка
     * @return ответ бота
     */
    private BotApiMethod<?> handleChangeOrderStatus(Long chatId, Integer messageId, String callbackData) {
        try {
            // Ожидаемый формат: order_status_[orderId]_[status]
            String[] parts = callbackData.split("_");
            if (parts.length < 4) {
                return createTextMessage(chatId, "Неверный формат данных для изменения статуса.");
            }
            
            Long orderId = Long.parseLong(parts[2]);
            String statusStr = parts[3].toUpperCase();
            OrderStatus newStatus = OrderStatus.valueOf(statusStr);
            
            Order updatedOrder = orderService.updateOrderStatus(orderId, newStatus);
            if (updatedOrder == null) {
                return createEditMessage(chatId, messageId, "Заказ не найден или не может быть обновлен.", createBackToOrdersKeyboard());
            }
            
            return handleOrderDetails(chatId, messageId, "order_details_" + orderId);
        } catch (Exception e) {
            logger.error("Error changing order status: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при изменении статуса заказа.");
        }
    }
    
    /**
     * Отображает список заказов
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param ordersPage страница с заказами
     * @param page номер текущей страницы
     * @param title заголовок списка
     * @param status статус заказов (null для всех заказов)
     * @return ответ бота
     */
    private BotApiMethod<?> displayOrdersList(Long chatId, Integer messageId, Page<Order> ordersPage, int page, String title, OrderStatus status) {
        StringBuilder messageText = new StringBuilder();
        messageText.append("📦 *").append(title).append("* (стр. ").append(page).append(" из ").append(ordersPage.getTotalPages()).append(")\n\n");
        
        List<Order> orders = ordersPage.getContent();
        for (Order order : orders) {
            messageText.append("🔹 *Заказ #").append(order.getId()).append("*\n");
            messageText.append("📅 ").append(order.getCreatedAt().toString().replace("T", " ").substring(0, 16)).append("\n");
            messageText.append("👤 ").append(order.getUser().getFirstName());
            if (order.getUser().getLastName() != null) {
                messageText.append(" ").append(order.getUser().getLastName());
            }
            messageText.append("\n");
            messageText.append("💰 ").append(order.getTotalAmount()).append(" руб.\n");
            messageText.append("🏷 ").append(getStatusText(order.getStatus())).append("\n\n");
        }
        
        return createEditMessage(chatId, messageId, messageText.toString(), 
                createOrdersListKeyboard(page, ordersPage.getTotalPages(), orders, status));
    }
    
    /**
     * Создает клавиатуру для списка заказов
     * 
     * @param currentPage текущая страница
     * @param totalPages общее количество страниц
     * @param orders список заказов
     * @param status статус заказов (null для всех заказов)
     * @return клавиатура
     */
    private InlineKeyboardMarkup createOrdersListKeyboard(int currentPage, int totalPages, List<Order> orders, OrderStatus status) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопки для каждого заказа
        for (Order order : orders) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton detailsButton = new InlineKeyboardButton();
            detailsButton.setText("Заказ #" + order.getId() + " - " + getStatusText(order.getStatus()));
            detailsButton.setCallbackData("order_details_" + order.getId());
            row.add(detailsButton);
            keyboard.add(row);
        }
        
        // Кнопки пагинации
        if (totalPages > 1) {
            List<InlineKeyboardButton> paginationRow = new ArrayList<>();
            
            if (currentPage > 1) {
                InlineKeyboardButton prevButton = new InlineKeyboardButton();
                prevButton.setText("◀️ Назад");
                prevButton.setCallbackData("orders_page_" + (status == null ? "all" : status.name().toLowerCase()) + "_" + (currentPage - 1));
                paginationRow.add(prevButton);
            }
            
            if (currentPage < totalPages) {
                InlineKeyboardButton nextButton = new InlineKeyboardButton();
                nextButton.setText("Вперед ▶️");
                nextButton.setCallbackData("orders_page_" + (status == null ? "all" : status.name().toLowerCase()) + "_" + (currentPage + 1));
                paginationRow.add(nextButton);
            }
            
            keyboard.add(paginationRow);
        }
        
        // Кнопки для фильтрации заказов
        keyboard.add(createFilterButtonsRow());
        
        // Кнопка возврата в админ панель
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Вернуться в админ панель");
        backButton.setCallbackData("back_to_admin");
        backRow.add(backButton);
        keyboard.add(backRow);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
    
    /**
     * Создает клавиатуру для деталей заказа
     * 
     * @param order заказ
     * @return клавиатура
     */
    private InlineKeyboardMarkup createOrderDetailsKeyboard(Order order) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопки для изменения статуса
        List<InlineKeyboardButton> statusRow = new ArrayList<>();
        
        // В зависимости от текущего статуса показываем разные кнопки
        switch (order.getStatus()) {
            case NEW:
                InlineKeyboardButton processingButton = new InlineKeyboardButton();
                processingButton.setText("🔄 В обработку");
                processingButton.setCallbackData("order_status_" + order.getId() + "_processing");
                statusRow.add(processingButton);
                
                InlineKeyboardButton cancelButton = new InlineKeyboardButton();
                cancelButton.setText("❌ Отменить");
                cancelButton.setCallbackData("order_status_" + order.getId() + "_cancelled");
                statusRow.add(cancelButton);
                break;
                
            case PROCESSING:
                InlineKeyboardButton completeButton = new InlineKeyboardButton();
                completeButton.setText("✅ Выполнен");
                completeButton.setCallbackData("order_status_" + order.getId() + "_completed");
                statusRow.add(completeButton);
                
                InlineKeyboardButton cancelProcessingButton = new InlineKeyboardButton();
                cancelProcessingButton.setText("❌ Отменить");
                cancelProcessingButton.setCallbackData("order_status_" + order.getId() + "_cancelled");
                statusRow.add(cancelProcessingButton);
                break;
                
            case COMPLETED:
            case CANCELLED:
                // Для выполненных и отмененных заказов не показываем кнопки изменения статуса
                break;
        }
        
        if (!statusRow.isEmpty()) {
            keyboard.add(statusRow);
        }
        
        // Кнопка возврата к списку заказов
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Назад к списку заказов");
        backButton.setCallbackData("orders_all");
        backRow.add(backButton);
        keyboard.add(backRow);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
    
    /**
     * Создает клавиатуру для фильтрации заказов
     * 
     * @return клавиатура
     */
    private InlineKeyboardMarkup createFilterOrdersKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопки для фильтрации
        keyboard.add(createFilterButtonsRow());
        
        // Кнопка возврата в админ панель
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Вернуться в админ панель");
        backButton.setCallbackData("back_to_admin");
        backRow.add(backButton);
        keyboard.add(backRow);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
    
    /**
     * Создает ряд кнопок для фильтрации заказов
     * 
     * @return ряд кнопок
     */
    private List<InlineKeyboardButton> createFilterButtonsRow() {
        List<InlineKeyboardButton> filterRow = new ArrayList<>();
        
        InlineKeyboardButton allButton = new InlineKeyboardButton();
        allButton.setText("Все");
        allButton.setCallbackData("orders_all");
        filterRow.add(allButton);
        
        InlineKeyboardButton newButton = new InlineKeyboardButton();
        newButton.setText("Новые");
        newButton.setCallbackData("orders_new");
        filterRow.add(newButton);
        
        InlineKeyboardButton processingButton = new InlineKeyboardButton();
        processingButton.setText("В обработке");
        processingButton.setCallbackData("orders_processing");
        filterRow.add(processingButton);
        
        return filterRow;
    }
    
    /**
     * Создает второй ряд кнопок для фильтрации заказов
     * 
     * @return ряд кнопок
     */
    private List<InlineKeyboardButton> createFilterButtonsRow2() {
        List<InlineKeyboardButton> filterRow = new ArrayList<>();
        
        InlineKeyboardButton completedButton = new InlineKeyboardButton();
        completedButton.setText("Выполненные");
        completedButton.setCallbackData("orders_completed");
        filterRow.add(completedButton);
        
        InlineKeyboardButton cancelledButton = new InlineKeyboardButton();
        cancelledButton.setText("Отмененные");
        cancelledButton.setCallbackData("orders_cancelled");
        filterRow.add(cancelledButton);
        
        return filterRow;
    }
    
    /**
     * Создает клавиатуру для возврата к списку заказов
     * 
     * @return клавиатура
     */
    private InlineKeyboardMarkup createBackToOrdersKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Назад к списку заказов");
        backButton.setCallbackData("orders_all");
        row.add(backButton);
        keyboard.add(row);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
    
    /**
     * Возвращает текстовое представление статуса заказа
     * 
     * @param status статус заказа
     * @return текст статуса
     */
    private String getStatusText(OrderStatus status) {
        switch (status) {
            case NEW:
                return "🆕 Новый";
            case PROCESSING:
                return "🔄 В обработке";
            case COMPLETED:
                return "✅ Выполнен";
            case CANCELLED:
                return "❌ Отменен";
            default:
                return status.name();
        }
    }
    
    /**
     * Создает текстовое сообщение
     * 
     * @param chatId ID чата
     * @param text текст сообщения
     * @return объект сообщения
     */
    private SendMessage createTextMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("Markdown");
        return message;
    }
    
    /**
     * Создает сообщение с редактированием существующего
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param text текст сообщения
     * @param replyMarkup клавиатура
     * @return объект сообщения
     */
    private EditMessageText createEditMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup replyMarkup) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        
        if (messageId != null) {
            message.setMessageId(messageId);
        }
        
        message.setText(text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(replyMarkup);
        return message;
    }
} 