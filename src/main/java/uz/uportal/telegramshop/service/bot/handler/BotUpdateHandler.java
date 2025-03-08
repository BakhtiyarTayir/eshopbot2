package uz.uportal.telegramshop.service.bot.handler;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

/**
 * Интерфейс для обработчиков обновлений бота
 */
public interface BotUpdateHandler {
    
    /**
     * Обработка текстовых сообщений
     * @param message сообщение
     * @return ответ бота
     */
    BotApiMethod<?> handleMessage(Message message);
    
    /**
     * Обработка callback-запросов (нажатий на кнопки)
     * @param callbackQuery callback-запрос
     * @return ответ бота
     */
    BotApiMethod<?> handleCallbackQuery(CallbackQuery callbackQuery);
} 