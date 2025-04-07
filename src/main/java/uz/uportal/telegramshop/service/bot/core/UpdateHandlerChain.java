package uz.uportal.telegramshop.service.bot.core;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;

/**
 * Цепочка обработчиков обновлений от Telegram
 */
@Component
public class UpdateHandlerChain {
    
    private static final Logger logger = LoggerFactory.getLogger(UpdateHandlerChain.class);
    private final ApplicationContext applicationContext;
    private final TelegramUserRepository telegramUserRepository;
    
    public UpdateHandlerChain(ApplicationContext applicationContext, TelegramUserRepository telegramUserRepository) {
        this.applicationContext = applicationContext;
        this.telegramUserRepository = telegramUserRepository;
    }
    
    /**
     * Обрабатывает обновление, передавая его по цепочке обработчиков
     * 
     * @param update обновление от Telegram
     * @return ответ на обновление
     */
    public BotApiMethod<?> handle(Update update) {
        logger.debug("Обработка обновления: {}", update);
        
        // Если это callback-запрос, добавляем специальное логирование
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            logger.info("Получен callback-запрос: data='{}' от пользователя chatId={}", callbackData, chatId);
        }
        
        // Специальная проверка для команды /start
        if (update.hasMessage() && update.getMessage().hasText() && 
            (update.getMessage().getText().equals("/start") || update.getMessage().getText().equals("start"))) {
            
            logger.info("Обнаружена команда /start, обрабатываем ее в приоритетном порядке");
            
            // Получаем все обработчики
            List<UpdateHandler> handlers = new ArrayList<>(applicationContext.getBeansOfType(UpdateHandler.class).values());
            
            // Ищем StartCommandHandler
            for (UpdateHandler handler : handlers) {
                if (handler.getClass().getSimpleName().equals("StartCommandHandler") && handler.canHandle(update)) {
                    logger.info("Найден StartCommandHandler, передаем ему обработку");
                    
                    // Сбрасываем состояние пользователя, если оно есть
                    Long chatId = update.getMessage().getChatId();
                    TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
                    if (user != null && user.getState() != null) {
                        logger.info("Сбрасываем состояние пользователя: {}", user.getState());
                        user.setState(null);
                        telegramUserRepository.save(user);
                    }
                    
                    return handler.handle(update);
                }
            }
        }
        
        // Проверяем, есть ли у пользователя состояние
        if (update.hasMessage()) {
            Long chatId = update.getMessage().getChatId();
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            
            if (user != null && user.getState() != null) {
                logger.debug("Пользователь {} находится в состоянии {}", chatId, user.getState());
                
                // Получаем все обработчики состояний из контекста приложения
                List<StateHandler> stateHandlers = new ArrayList<>(applicationContext.getBeansOfType(StateHandler.class).values());
                
                // Проходим по всем обработчикам состояний и находим тот, который может обработать обновление
                for (StateHandler handler : stateHandlers) {
                    if (handler.canHandleState(update, user.getState())) {
                        logger.debug("Обработчик состояний {} может обработать обновление", handler.getClass().getSimpleName());
                        return handler.handleState(update, user.getState());
                    }
                }
            }
        }
        
        // Если у пользователя нет состояния или ни один обработчик состояний не смог обработать обновление,
        // используем обычные обработчики
        List<UpdateHandler> handlers = new ArrayList<>(applicationContext.getBeansOfType(UpdateHandler.class).values());
        
        // Проходим по всем обработчикам и находим тот, который может обработать обновление
        for (UpdateHandler handler : handlers) {
            if (handler.canHandle(update)) {
                logger.debug("Обработчик {} может обработать обновление", handler.getClass().getSimpleName());
                return handler.handle(update);
            } else if (update.hasCallbackQuery()) {
                logger.debug("Обработчик {} НЕ может обработать callback запрос '{}'", 
                        handler.getClass().getSimpleName(), update.getCallbackQuery().getData());
            }
        }
        
        if (update.hasCallbackQuery()) {
            logger.warn("Ни один обработчик не смог обработать callback запрос: '{}'", 
                    update.getCallbackQuery().getData());
        } else {
            logger.debug("Ни один обработчик не смог обработать обновление");
        }
        return null;
    }
} 