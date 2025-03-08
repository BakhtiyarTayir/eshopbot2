package uz.uportal.telegramshop.service;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;
import uz.uportal.telegramshop.repository.TelegramUserRepository;

import uz.uportal.telegramshop.service.bot.handler.CommandHandler;
import uz.uportal.telegramshop.service.bot.handler.OrderHandler;
import uz.uportal.telegramshop.service.bot.handler.StateHandler;
import uz.uportal.telegramshop.service.bot.handler.CatalogHandler;
import uz.uportal.telegramshop.service.bot.handler.CartHandler;

/**
 * Основной класс Telegram бота (обертка для совместимости)
 * @deprecated Используйте {@link uz.uportal.telegramshop.service.bot.TelegramBotService}
 */
@Service
@Deprecated
public class TelegramBotService extends uz.uportal.telegramshop.service.bot.TelegramBotService {
    
    public TelegramBotService(
            TelegramUserRepository userRepository,
            CommandHandler commandHandler,
            StateHandler stateHandler,
            OrderHandler orderHandler,
            CatalogHandler catalogHandler,
            CategoryService categoryService,
            CartHandler cartHandler,
            CartService cartService,
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${telegram.bot.webhook-path}") String botPath) {
        super(userRepository, commandHandler, stateHandler, orderHandler, catalogHandler, categoryService, cartHandler, cartService, botToken, botUsername, botPath);
    }
    
    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        return super.onWebhookUpdateReceived(update);
    }
} 