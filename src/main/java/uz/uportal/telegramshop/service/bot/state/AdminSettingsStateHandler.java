package uz.uportal.telegramshop.service.bot.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import uz.uportal.telegramshop.model.ShopSettings;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.ShopSettingsService;
import uz.uportal.telegramshop.service.bot.core.StateHandler;

/**
 * Обработчик состояний для редактирования настроек магазина в админ-панели
 */
@Component
public class AdminSettingsStateHandler implements StateHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminSettingsStateHandler.class);
    private final TelegramUserRepository telegramUserRepository;
    private final ShopSettingsService shopSettingsService;
    
    public AdminSettingsStateHandler(
            TelegramUserRepository telegramUserRepository,
            ShopSettingsService shopSettingsService) {
        this.telegramUserRepository = telegramUserRepository;
        this.shopSettingsService = shopSettingsService;
    }
    
    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage() && update.getMessage().hasText();
    }
    
    @Override
    public boolean canHandleState(Update update, String state) {
        return state != null && (
                state.equals("EDITING_SHOP_CONTACTS") || 
                state.equals("EDITING_SHOP_SUPPORT") || 
                state.equals("EDITING_SHOP_ABOUT") || 
                state.equals("EDITING_SHOP_HOURS"));
    }
    
    @Override
    public BotApiMethod<?> handle(Update update) {
        // Этот метод не должен вызываться напрямую, используйте handleState
        return null;
    }
    
    @Override
    public BotApiMethod<?> handleState(Update update, String state) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String text = message.getText();
        
        logger.info("Handling admin settings state: {} for chatId: {}", state, chatId);
        
        // Получаем пользователя
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user == null) {
            return createTextMessage(chatId, "Пользователь не найден");
        }
        
        // Проверяем права доступа
        if (!"ADMIN".equals(user.getRole()) && !"MANAGER".equals(user.getRole())) {
            user.setState(null);
            telegramUserRepository.save(user);
            return createTextMessage(chatId, "У вас нет доступа к административным функциям.");
        }
        
        switch (state) {
            case "EDITING_SHOP_CONTACTS":
                return handleEditContacts(chatId, text, user);
            case "EDITING_SHOP_SUPPORT":
                return handleEditSupport(chatId, text, user);
            case "EDITING_SHOP_ABOUT":
                return handleEditAbout(chatId, text, user);
            case "EDITING_SHOP_HOURS":
                return handleEditHours(chatId, text, user);
            default:
                return null;
        }
    }
    
    /**
     * Обрабатывает редактирование контактной информации
     * @param chatId ID чата
     * @param text текст сообщения
     * @param user пользователь
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditContacts(Long chatId, String text, TelegramUser user) {
        // Формат: телефон|email|сайт
        String[] parts = text.split("\\|");
        if (parts.length != 3) {
            return createTextMessage(chatId, "❌ Неверный формат. Введите данные в формате: *Телефон|Email|Сайт*");
        }
        
        String phone = parts[0].trim();
        String email = parts[1].trim();
        String website = parts[2].trim();
        
        try {
            // Обновляем настройки
            ShopSettings settings = shopSettingsService.updateContactInfo(phone, email, website);
            
            // Сбрасываем состояние пользователя
            user.setState(null);
            telegramUserRepository.save(user);
            
            return createTextMessage(chatId, "✅ Контактная информация успешно обновлена!\n\n" +
                    "📞 Телефон: " + settings.getPhone() + "\n" +
                    "📧 Email: " + settings.getEmail() + "\n" +
                    "🌐 Сайт: " + settings.getWebsite());
        } catch (Exception e) {
            logger.error("Error updating shop contacts", e);
            return createTextMessage(chatId, "❌ Ошибка при обновлении контактной информации: " + e.getMessage());
        }
    }
    
    /**
     * Обрабатывает редактирование информации о поддержке
     * @param chatId ID чата
     * @param text текст сообщения
     * @param user пользователь
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditSupport(Long chatId, String text, TelegramUser user) {
        try {
            // Обновляем настройки
            ShopSettings settings = shopSettingsService.updateSupportInfo(text);
            
            // Сбрасываем состояние пользователя
            user.setState(null);
            telegramUserRepository.save(user);
            
            return createTextMessage(chatId, "✅ Информация о поддержке успешно обновлена!\n\n" +
                    "Новое сообщение поддержки:\n" + settings.getSupportInfo());
        } catch (Exception e) {
            logger.error("Error updating shop support info", e);
            return createTextMessage(chatId, "❌ Ошибка при обновлении информации о поддержке: " + e.getMessage());
        }
    }
    
    /**
     * Обрабатывает редактирование информации о магазине
     * @param chatId ID чата
     * @param text текст сообщения
     * @param user пользователь
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditAbout(Long chatId, String text, TelegramUser user) {
        try {
            // Обновляем настройки
            ShopSettings settings = shopSettingsService.updateAboutInfo(text);
            
            // Сбрасываем состояние пользователя
            user.setState(null);
            telegramUserRepository.save(user);
            
            return createTextMessage(chatId, "✅ Информация о магазине успешно обновлена!\n\n" +
                    "Новая информация о магазине:\n" + settings.getAboutInfo());
        } catch (Exception e) {
            logger.error("Error updating shop about info", e);
            return createTextMessage(chatId, "❌ Ошибка при обновлении информации о магазине: " + e.getMessage());
        }
    }
    
    /**
     * Обрабатывает редактирование часов работы
     * @param chatId ID чата
     * @param text текст сообщения
     * @param user пользователь
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditHours(Long chatId, String text, TelegramUser user) {
        // Заменяем \n на реальные переносы строк
        String hours = text.replace("\\n", "\n");
        
        try {
            // Обновляем настройки
            ShopSettings settings = shopSettingsService.updateWorkingHours(hours);
            
            // Сбрасываем состояние пользователя
            user.setState(null);
            telegramUserRepository.save(user);
            
            return createTextMessage(chatId, "✅ Режим работы успешно обновлен!\n\n" +
                    "Новый режим работы:\n" + settings.getWorkingHours());
        } catch (Exception e) {
            logger.error("Error updating shop working hours", e);
            return createTextMessage(chatId, "❌ Ошибка при обновлении режима работы: " + e.getMessage());
        }
    }
    
    /**
     * Создает объект текстового сообщения
     * @param chatId ID чата
     * @param text текст сообщения
     * @return объект сообщения
     */
    private SendMessage createTextMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("Markdown");
        return message;
    }
} 