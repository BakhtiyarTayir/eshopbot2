package uz.uportal.telegramshop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import uz.uportal.telegramshop.service.bot.AbstractTelegramBot;
import uz.uportal.telegramshop.service.bot.core.MessageSender;
import uz.uportal.telegramshop.service.bot.core.UpdateHandlerChain;
import uz.uportal.telegramshop.service.bot.keyboards.KeyboardFactory;

/**
 * Основной класс Telegram бота
 */
@Service("TelegramBotService")
public class TelegramBotService extends AbstractTelegramBot implements MessageSender {
    
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);
    private final UpdateHandlerChain updateHandlerChain;
    private final KeyboardFactory keyboardFactory;
    
    public TelegramBotService(
            UpdateHandlerChain updateHandlerChain,
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${telegram.bot.webhook-path}") String botPath,
            KeyboardFactory keyboardFactory) {
        super(botToken, botUsername, botPath);
        this.updateHandlerChain = updateHandlerChain;
        this.keyboardFactory = keyboardFactory;
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        logger.info("Received update: {}", update);
        
        try {
            // Обрабатываем обновление через цепочку обработчиков
            BotApiMethod<?> response = updateHandlerChain.handle(update);
            
            // Если ни один обработчик не смог обработать обновление, отправляем сообщение по умолчанию
            if (response == null && update.hasMessage()) {
                logger.info("No handler processed the update, sending default message");
                return defaultResponse(update);
            }
            
            return response;
        } catch (Exception e) {
            logger.error("Error processing update", e);
            return null;
        }
    }
    
    /**
     * Создает ответ по умолчанию, если ни один обработчик не смог обработать обновление
     * @param update обновление от Telegram
     * @return ответ бота
     */
    private BotApiMethod<?> defaultResponse(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Long chatId = update.getMessage().getChatId();
            
            // Отправляем сообщение о том, что команда не распознана
            return createTextMessage(chatId, "Извините, я не понимаю эту команду. Используйте меню для навигации.");
        }
        
        return null;
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
     * Отправляет сообщение с фотографией
     * @param sendPhoto объект сообщения с фотографией
     * @return отправленное сообщение
     * @throws TelegramApiException если произошла ошибка при отправке
     */
    public Message executePhoto(SendPhoto sendPhoto) throws TelegramApiException {
        try {
            return execute(sendPhoto);
        } catch (TelegramApiException e) {
            logger.error("Error sending photo message: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Отправляет текстовое сообщение
     * @param sendMessage объект текстового сообщения
     * @return отправленное сообщение
     * @throws TelegramApiException если произошла ошибка при отправке
     */
    public Message executeMessage(SendMessage sendMessage) throws TelegramApiException {
        try {
            return execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.error("Error sending text message: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Отправляет сообщение с обновлением текста
     * @param editMessageText объект сообщения с обновлением текста
     * @return результат выполнения
     * @throws TelegramApiException если произошла ошибка при отправке
     */
    @Override
    public Object executeEditMessage(EditMessageText editMessageText) throws TelegramApiException {
        try {
            return execute(editMessageText);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при редактировании сообщения: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Удаляет сообщение
     * 
     * @param deleteMessage объект с данными для удаления сообщения
     * @return результат операции
     * @throws TelegramApiException если произошла ошибка при удалении
     */
    @Override
    public Boolean executeDeleteMessage(DeleteMessage deleteMessage) throws TelegramApiException {
        try {
            return execute(deleteMessage);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при удалении сообщения: {}", e.getMessage());
            throw e;
        }
    }
}