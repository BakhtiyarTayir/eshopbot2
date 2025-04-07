package uz.uportal.telegramshop.service.bot.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.model.ShopSettings;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.CartService;
import uz.uportal.telegramshop.service.CategoryService;
import uz.uportal.telegramshop.service.ShopSettingsService;
import uz.uportal.telegramshop.service.bot.core.UpdateHandler;
import uz.uportal.telegramshop.service.bot.keyboards.KeyboardFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик кнопок главного меню
 */
@Component
public class MainMenuHandler implements UpdateHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(MainMenuHandler.class);
    private final TelegramUserRepository telegramUserRepository;
    private final KeyboardFactory keyboardFactory;
    private final CategoryService categoryService;
    private final CartService cartService;
    private final ShopSettingsService shopSettingsService;
    
    public MainMenuHandler(
            TelegramUserRepository telegramUserRepository,
            KeyboardFactory keyboardFactory,
            CategoryService categoryService,
            CartService cartService,
            ShopSettingsService shopSettingsService) {
        this.telegramUserRepository = telegramUserRepository;
        this.keyboardFactory = keyboardFactory;
        this.categoryService = categoryService;
        this.cartService = cartService;
        this.shopSettingsService = shopSettingsService;
    }
    
    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }
        
        String text = update.getMessage().getText();
        return text.equals("🛍 Каталог") || 
               text.equals("🛒 Корзина") || 
               text.equals("ℹ️ Информация") || 
               text.equals("📞 Поддержка") || 
               text.equals("⚙️ Админ панель");
    }
    
    @Override
    public BotApiMethod<?> handle(Update update) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String text = message.getText();
        
        logger.info("Handling main menu button: {} for chatId: {}", text, chatId);
        
        // Получаем пользователя
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
        
        // Обрабатываем нажатие кнопки
        switch (text) {
            case "🛍 Каталог":
                return handleCatalog(chatId);
            case "🛒 Корзина":
                return handleCart(chatId, user);
            case "ℹ️ Информация":
                return handleInfo(chatId);
            case "📞 Поддержка":
                return handleHelp(chatId);
            case "⚙️ Админ панель":
                return handleAdminPanel(chatId, user);
            default:
                return null;
        }
    }
    
    /**
     * Обрабатывает нажатие кнопки "Каталог"
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleCatalog(Long chatId) {
        List<Category> categories = categoryService.getMainCategories();
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        
        if (categories.isEmpty()) {
            sendMessage.setText("В данный момент каталог товаров пуст. Пожалуйста, попробуйте позже.");
        } else {
            StringBuilder messageText = new StringBuilder();
            messageText.append("📋 *Каталог товаров*\n\n");
            messageText.append("Выберите категорию:\n\n");
            
            sendMessage.setText(messageText.toString());
            sendMessage.setParseMode("Markdown");
            sendMessage.setReplyMarkup(keyboardFactory.createCatalogKeyboard(categories));
        }
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Корзина"
     * @param chatId ID чата
     * @param user пользователь
     * @return ответ бота
     */
    private BotApiMethod<?> handleCart(Long chatId, TelegramUser user) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        
        String cartInfo = cartService.getCartInfo(chatId);
        
        if (cartInfo.isEmpty()) {
            sendMessage.setText("Ваша корзина пуста. Добавьте товары из каталога.");
            
            // Добавляем кнопку для перехода в каталог
            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton catalogButton = new InlineKeyboardButton();
            catalogButton.setText("🔍 Перейти в каталог");
            catalogButton.setCallbackData("catalog_categories");
            row.add(catalogButton);
            keyboard.add(row);
            
            keyboardMarkup.setKeyboard(keyboard);
            sendMessage.setReplyMarkup(keyboardMarkup);
        } else {
            sendMessage.setText("🛒 Ваша корзина:\n\n" + cartInfo);
            sendMessage.setReplyMarkup(keyboardFactory.createCartKeyboard());
        }
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Информация"
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleInfo(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        
        // Получаем настройки магазина
        ShopSettings settings = shopSettingsService.getShopSettings();
        
        StringBuilder infoText = new StringBuilder();
        infoText.append("ℹ️ *Информация о магазине*\n\n");
        infoText.append(settings.getAboutInfo()).append("\n\n");
        infoText.append("*Контакты:*\n");
        infoText.append("📞 Телефон: ").append(settings.getPhone()).append("\n");
        infoText.append("📧 Email: ").append(settings.getEmail()).append("\n");
        infoText.append("🌐 Сайт: ").append(settings.getWebsite()).append("\n\n");
        infoText.append("*Режим работы:*\n");
        infoText.append(settings.getWorkingHours());
        
        sendMessage.setText(infoText.toString());
        sendMessage.setParseMode("Markdown");
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Поддержка"
     * @param chatId ID чата
     * @return ответ бота
     */
    private BotApiMethod<?> handleHelp(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        
        // Получаем настройки магазина
        ShopSettings settings = shopSettingsService.getShopSettings();
        
        StringBuilder helpText = new StringBuilder();
        helpText.append("📞 *Поддержка*\n\n");
        helpText.append("*Основные команды:*\n");
        helpText.append("🛍 *Каталог* - просмотр категорий товаров\n");
        helpText.append("🛒 *Корзина* - просмотр и управление корзиной\n");
        helpText.append("ℹ️ *Информация* - информация о магазине\n");
        helpText.append("📞 *Поддержка* - контакты для связи\n\n");
        helpText.append(settings.getSupportInfo()).append(" ").append(settings.getPhone());
        
        sendMessage.setText(helpText.toString());
        sendMessage.setParseMode("Markdown");
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Админ панель"
     * @param chatId ID чата
     * @param user пользователь
     * @return ответ бота
     */
    private BotApiMethod<?> handleAdminPanel(Long chatId, TelegramUser user) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        
        // Проверяем, имеет ли пользователь права администратора или менеджера
        if ("ADMIN".equals(user.getRole()) || "MANAGER".equals(user.getRole())) {
            sendMessage.setText("⚙️ *Панель администратора*\n\n" +
                    "Здесь вы можете управлять товарами, категориями и заказами.");
            sendMessage.setParseMode("Markdown");
            
            // Здесь можно добавить клавиатуру для админ-панели
        } else {
            sendMessage.setText("У вас нет доступа к панели администратора.");
        }
        
        return sendMessage;
    }
} 