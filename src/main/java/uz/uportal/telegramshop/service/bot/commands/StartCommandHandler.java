package uz.uportal.telegramshop.service.bot.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.bot.core.UpdateHandler;
import uz.uportal.telegramshop.service.bot.keyboards.KeyboardFactory;

/**
 * Обработчик команды /start
 */
@Component
public class StartCommandHandler implements UpdateHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(StartCommandHandler.class);
    private final TelegramUserRepository telegramUserRepository;
    private final KeyboardFactory keyboardFactory;
    
    public StartCommandHandler(TelegramUserRepository telegramUserRepository, KeyboardFactory keyboardFactory) {
        this.telegramUserRepository = telegramUserRepository;
        this.keyboardFactory = keyboardFactory;
    }
    
    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }
        
        String text = update.getMessage().getText();
        return text.equals("/start") || text.equals("start");
    }
    
    @Override
    public BotApiMethod<?> handle(Update update) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        
        logger.info("Handling /start command for chatId: {}", chatId);
        
        // Получаем или создаем пользователя
        TelegramUser user = telegramUserRepository.findById(chatId)
                .orElseGet(() -> {
                    TelegramUser newUser = new TelegramUser(
                            chatId,
                            message.getFrom().getUserName(),
                            message.getFrom().getFirstName(),
                            message.getFrom().getLastName()
                    );
                    return telegramUserRepository.save(newUser);
                });
        
        // Проверяем, является ли пользователь админом или менеджером
        boolean isAdminOrManager = "ADMIN".equals(user.getRole()) || "MANAGER".equals(user.getRole());
        
        // Создаем сообщение приветствия
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        
        StringBuilder welcomeText = new StringBuilder();
        welcomeText.append("👋 Добро пожаловать в наш магазин, ")
                .append(user.getFirstName())
                .append("!\n\n");
        welcomeText.append("🛍 Здесь вы можете выбрать товары из нашего каталога и оформить заказ.\n\n");
        welcomeText.append("Используйте кнопки меню для навигации:");
        
        sendMessage.setText(welcomeText.toString());
        
        // Устанавливаем клавиатуру с учетом прав пользователя
        sendMessage.setReplyMarkup(keyboardFactory.createMainMenuKeyboard(isAdminOrManager));
        
        return sendMessage;
    }
} 