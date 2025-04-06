package uz.uportal.telegramshop.service.bot.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import uz.uportal.telegramshop.model.Order;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.CartService;
import uz.uportal.telegramshop.service.OrderService;
import uz.uportal.telegramshop.service.bot.core.StateHandler;
import uz.uportal.telegramshop.service.bot.keyboards.KeyboardFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Обработчик состояний для процесса оформления заказа
 */
@Component
public class CheckoutStateHandler implements StateHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CheckoutStateHandler.class);
    
    private final TelegramUserRepository telegramUserRepository;
    private final CartService cartService;
    private final OrderService orderService;
    private final KeyboardFactory keyboardFactory;
    
    public CheckoutStateHandler(
            TelegramUserRepository telegramUserRepository,
            CartService cartService,
            OrderService orderService,
            KeyboardFactory keyboardFactory) {
        this.telegramUserRepository = telegramUserRepository;
        this.cartService = cartService;
        this.orderService = orderService;
        this.keyboardFactory = keyboardFactory;
    }
    
    @Override
    public boolean canHandleState(Update update, String state) {
        return state != null && (
                state.equals("WAITING_FOR_ADDRESS") ||
                state.equals("WAITING_FOR_PHONE") ||
                state.equals("WAITING_FOR_COMMENT"));
    }
    
    @Override
    public boolean canHandle(Update update) {
        // This handler only handles state-based updates
        return false;
    }
    
    @Override
    public BotApiMethod<?> handle(Update update) {
        // This handler only handles state-based updates
        return null;
    }
    
    @Override
    public BotApiMethod<?> handleState(Update update, String state) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return null;
        }
        
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String text = message.getText();
        
        // Получаем пользователя
        Optional<TelegramUser> userOpt = telegramUserRepository.findById(chatId);
        if (userOpt.isEmpty()) {
            logger.warn("User with chatId {} not found", chatId);
            return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, попробуйте позже.");
        }
        
        TelegramUser user = userOpt.get();
        
        try {
            switch (state) {
                case "WAITING_FOR_ADDRESS":
                    return handleAddressInput(user, chatId, text);
                case "WAITING_FOR_PHONE":
                    return handlePhoneInput(user, chatId, text);
                case "WAITING_FOR_COMMENT":
                    return handleCommentInput(user, chatId, text);
                default:
                    logger.warn("Unknown state: {}", state);
                    return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, попробуйте позже.");
            }
        } catch (Exception e) {
            logger.error("Error handling checkout state: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при обработке вашего запроса.");
        }
    }
    
    /**
     * Обрабатывает ввод адреса доставки
     * 
     * @param user пользователь
     * @param chatId ID чата
     * @param address адрес доставки
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddressInput(TelegramUser user, Long chatId, String address) {
        // Сохраняем адрес в tempData пользователя
        user.setTempData(address);
        
        // Переходим к следующему шагу - ввод номера телефона
        user.setState("WAITING_FOR_PHONE");
        telegramUserRepository.save(user);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Спасибо! Теперь, пожалуйста, укажите номер телефона для связи:");
        
        return message;
    }
    
    /**
     * Обрабатывает ввод номера телефона
     * 
     * @param user пользователь
     * @param chatId ID чата
     * @param phone номер телефона
     * @return ответ бота
     */
    private BotApiMethod<?> handlePhoneInput(TelegramUser user, Long chatId, String phone) {
        // Сохраняем номер телефона в пользователе
        user.setPhoneNumber(phone);
        
        // Переходим к следующему шагу - ввод комментария
        user.setState("WAITING_FOR_COMMENT");
        telegramUserRepository.save(user);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Спасибо! Если у вас есть комментарий к заказу, напишите его сейчас. " +
                        "Если комментария нет, просто отправьте '-':");
        
        return message;
    }
    
    /**
     * Обрабатывает ввод комментария к заказу
     * 
     * @param user пользователь
     * @param chatId ID чата
     * @param comment комментарий к заказу
     * @return ответ бота
     */
    private BotApiMethod<?> handleCommentInput(TelegramUser user, Long chatId, String comment) {
        // Если пользователь не хочет оставлять комментарий
        String finalComment = comment.equals("-") ? "" : comment;
        
        // Сбрасываем состояние пользователя
        user.setState(null);
        
        // Получаем адрес из tempData
        String address = user.getTempData();
        
        // Создаем заказ
        Order order = orderService.createOrderFromCart(user, address, user.getPhoneNumber(), finalComment);
        
        // Очищаем временные данные
        user.setTempData(null);
        telegramUserRepository.save(user);
        
        if (order != null) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("✅ Ваш заказ #" + order.getId() + " успешно оформлен!\n\n" +
                           "Мы свяжемся с вами в ближайшее время для подтверждения заказа.\n\n" +
                           "Спасибо за покупку!");
            
            // Создаем клавиатуру с кнопкой "Вернуться в каталог"
            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Вернуться в каталог");
            backButton.setCallbackData("catalog_categories");
            row.add(backButton);
            keyboard.add(row);
            
            keyboardMarkup.setKeyboard(keyboard);
            message.setReplyMarkup(keyboardMarkup);
            
            return message;
        } else {
            return createTextMessage(chatId, "❌ Не удалось оформить заказ. Пожалуйста, попробуйте позже.");
        }
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