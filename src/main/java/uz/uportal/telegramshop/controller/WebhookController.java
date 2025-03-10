package uz.uportal.telegramshop.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

import uz.uportal.telegramshop.service.TelegramBotService;

import org.springframework.beans.factory.annotation.Qualifier;

@RestController
public class WebhookController {
    
    private final TelegramBotService telegramBotService;
    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);
    
    public WebhookController(@Qualifier("TelegramBotService") TelegramBotService telegramBotService) {
        this.telegramBotService = telegramBotService;
    }
    
    @PostMapping("/webhook")
    public BotApiMethod<?> onUpdateReceived(@RequestBody Update update) {
        logger.info("Received update: {}", update);
        BotApiMethod<?> response = telegramBotService.onWebhookUpdateReceived(update);
        logger.info("Response: {}", response);
        return response;
    }
} 