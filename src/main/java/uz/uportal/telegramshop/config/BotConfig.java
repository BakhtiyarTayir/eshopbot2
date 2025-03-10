package uz.uportal.telegramshop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import uz.uportal.telegramshop.service.TelegramBotService;
import uz.uportal.telegramshop.service.bot.core.MessageSender;

/**
 * Конфигурация для бота
 */
@Configuration
public class BotConfig {
    
    /**
     * Создает прокси для MessageSender, который будет использоваться в AdminCallbackHandler
     * Это разрывает циклическую зависимость между TelegramBotService и AdminCallbackHandler
     * 
     * @param telegramBotService сервис бота
     * @return интерфейс для отправки сообщений
     */
    @Bean
    @Primary
    public MessageSender messageSender(TelegramBotService telegramBotService) {
        return telegramBotService;
    }
} 