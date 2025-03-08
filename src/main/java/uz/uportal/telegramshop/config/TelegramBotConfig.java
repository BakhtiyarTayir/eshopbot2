package uz.uportal.telegramshop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.meta.generics.LongPollingBot;
import uz.uportal.telegramshop.service.bot.TelegramBotService;

@Configuration
public class TelegramBotConfig {
    
    // Для webhook бота не нужно регистрировать его в TelegramBotsApi
    // Webhook будет обрабатываться через WebhookController
} 