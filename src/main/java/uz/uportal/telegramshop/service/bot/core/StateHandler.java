package uz.uportal.telegramshop.service.bot.core;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Интерфейс для обработки состояний пользователя
 */
public interface StateHandler extends UpdateHandler {
    
    /**
     * Проверяет, может ли обработчик обработать обновление на основе состояния пользователя
     * 
     * @param update обновление от Telegram
     * @param state текущее состояние пользователя
     * @return true, если обработчик может обработать обновление
     */
    boolean canHandleState(Update update, String state);
    
    /**
     * Обрабатывает обновление на основе состояния пользователя
     * 
     * @param update обновление от Telegram
     * @param state текущее состояние пользователя
     * @return ответ бота
     */
    BotApiMethod<?> handleState(Update update, String state);
} 