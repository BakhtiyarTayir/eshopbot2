package uz.uportal.telegramshop.service.bot.messages;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;

/**
 * Интерфейс для фабрики сообщений
 */
public interface MessageFactory {
    
    /**
     * Создает информационное сообщение
     * @param chatId ID чата
     * @param text текст сообщения
     * @return информационное сообщение
     */
    SendMessage createInfoMessage(Long chatId, String text);
    
    /**
     * Создает сообщение об ошибке
     * @param chatId ID чата
     * @param errorText текст ошибки
     * @return сообщение об ошибке
     */
    SendMessage createErrorMessage(Long chatId, String errorText);
    
    /**
     * Создает стандартное сообщение
     * @param chatId ID чата
     * @param text текст сообщения
     * @return стандартное сообщение
     */
    SendMessage createDefaultMessage(Long chatId, String text);
    
    /**
     * Создает сообщение с клавиатурой
     * @param chatId ID чата
     * @param text текст сообщения
     * @param keyboard клавиатура
     * @return сообщение с клавиатурой
     */
    SendMessage createMessageWithKeyboard(Long chatId, String text, ReplyKeyboard keyboard);
} 