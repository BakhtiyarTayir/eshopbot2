package uz.uportal.telegramshop.service.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Абстрактный базовый класс для Telegram бота
 */
public abstract class AbstractTelegramBot extends TelegramWebhookBot {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    private final String botUsername;
    private final String botPath;
    
    public AbstractTelegramBot(String botToken, String botUsername, String botPath) {
        super(botToken);
        this.botUsername = botUsername;
        this.botPath = botPath;
    }
    
    @Override
    public String getBotUsername() {
        return botUsername;
    }
    
    @Override
    public String getBotPath() {
        return botPath;
    }
    
    /**
     * Безопасное выполнение метода отправки сообщения с обработкой исключений
     * @param method метод для выполнения
     */
    protected void executeWithExceptionHandling(BotApiMethod<?> method) {
        try {
            execute(method);
        } catch (TelegramApiException e) {
            logger.error("Error executing method: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Безопасная отправка текстового сообщения
     * @param chatId ID чата
     * @param text текст сообщения
     */
    protected void sendTextMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending message to {}: {}", chatId, e.getMessage(), e);
        }
    }
} 