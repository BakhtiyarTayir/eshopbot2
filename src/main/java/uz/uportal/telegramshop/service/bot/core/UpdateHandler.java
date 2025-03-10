package uz.uportal.telegramshop.service.bot.core;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Интерфейс для обработчиков обновлений от Telegram
 */
public interface UpdateHandler {
    
    /**
     * Проверяет, может ли данный обработчик обработать обновление
     * @param update обновление от Telegram
     * @return true, если обработчик может обработать обновление, иначе false
     */
    boolean canHandle(Update update);
    
    /**
     * Обрабатывает обновление от Telegram
     * @param update обновление от Telegram
     * @return ответ бота
     */
    BotApiMethod<?> handle(Update update);
} 