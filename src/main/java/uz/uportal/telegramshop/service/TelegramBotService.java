package uz.uportal.telegramshop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class TelegramBotService extends TelegramWebhookBot {
    
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);
    
    @Value("${telegram.bot.username}")
    private String botUsername;
    
    @Value("${telegram.bot.webhook-path}")
    private String botPath;
    
    private final TelegramUserRepository userRepository;
    
    public TelegramBotService(TelegramUserRepository userRepository, @Value("${telegram.bot.token}") String botToken) {
        super(botToken);
        this.userRepository = userRepository;
    }
    
    @Override
    public String getBotUsername() {
        return botUsername;
    }
    
    @Override
    public String getBotPath() {
        return botPath;
    }
    
    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        try {
            return handleUpdate(update);
        } catch (Exception e) {
            logger.error("Error processing update", e);
            return null;
        }
    }
    
    private BotApiMethod<?> handleUpdate(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();
            
            // Сохраняем пользователя, если он новый
            userRepository.findById(chatId).orElseGet(() -> {
                TelegramUser newUser = new TelegramUser(
                    chatId,
                    update.getMessage().getFrom().getUserName(),
                    update.getMessage().getFrom().getFirstName(),
                    update.getMessage().getFrom().getLastName()
                );
                return userRepository.save(newUser);
            });
            
            // Обрабатываем команды
            if (messageText.startsWith("/")) {
                return handleCommand(chatId, messageText);
            } else {
                return handleMessage(chatId, messageText);
            }
        }
        return null;
    }
    
    private SendMessage handleCommand(Long chatId, String command) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        // Получаем пользователя из базы данных
        TelegramUser user = userRepository.findById(chatId).orElse(null);
        boolean isAdmin = user != null && user.isAdmin();
        
        // Создаем клавиатуру
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<KeyboardRow>();
        
        switch (command) {
            case "/start":
                // Создаем первый ряд кнопок
                KeyboardRow row1 = new KeyboardRow();
                row1.add(new KeyboardButton("Каталог"));
                row1.add(new KeyboardButton("Корзина"));
                keyboard.add(row1);
                
                // Создаем второй ряд кнопок
                KeyboardRow row2 = new KeyboardRow();
                row2.add(new KeyboardButton("Заказы"));
                row2.add(new KeyboardButton("Информация"));
                keyboard.add(row2);
                
                // Создаем третий ряд кнопок
                KeyboardRow row3 = new KeyboardRow();
                row3.add(new KeyboardButton("Помощь"));
                keyboard.add(row3);
                
                // Если пользователь админ, добавляем панель администратора
                if (isAdmin) {
                    KeyboardRow adminRow = new KeyboardRow();
                    adminRow.add(new KeyboardButton("Админ-панель"));
                    keyboard.add(adminRow);
                }
                
                // Настраиваем клавиатуру
                keyboardMarkup.setKeyboard(keyboard);
                keyboardMarkup.setResizeKeyboard(true);
                keyboardMarkup.setOneTimeKeyboard(false);
                
                // Устанавливаем клавиатуру в сообщение
                message.setReplyMarkup(keyboardMarkup);
                message.setText("Добро пожаловать в наш магазин! Выберите действие или используйте команду /help для получения списка доступных команд.");
                break;
            case "/admin":
                // Проверяем, является ли пользователь администратором
                if (isAdmin) {
                    message.setText("Вы вошли в панель администратора. Используйте кнопки для управления магазином.");
                } else {
                    message.setText("У вас нет прав доступа к этой команде.");
                }
                break;
            case "/help":
                message.setText("Доступные команды:\n" +
                               "/start - Начать работу с ботом\n" +
                               "/help - Показать список команд\n" +
                               "/catalog - Просмотреть каталог товаров");
                break;
            case "/catalog":
                message.setText("Каталог товаров пока пуст. Мы работаем над его наполнением!");
                break;
            default:
                message.setText("Неизвестная команда. Используйте /help для получения списка доступных команд.");
        }
        
        return message;
    }
    
    private SendMessage handleMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        // Получаем пользователя из базы данных
        TelegramUser user = userRepository.findById(chatId).orElse(null);
        boolean isAdmin = user != null && user.isAdmin();
        
        switch (text) {
            case "Каталог":
                message.setText("Вы выбрали раздел 'Каталог'. Здесь будет отображаться список доступных товаров.");
                break;
            case "Корзина":
                message.setText("Вы выбрали раздел 'Корзина'. Ваша корзина пока пуста.");
                break;
            case "Заказы":
                message.setText("Вы выбрали раздел 'Заказы'. У вас пока нет заказов.");
                break;
            case "Информация":
                message.setText("Вы выбрали раздел 'Информация'.\n\n" +
                               "Наш магазин предлагает широкий ассортимент товаров.\n" +
                               "Для связи с нами: example@email.com\n" +
                               "Телефон: +7 (123) 456-78-90");
                break;
            case "Помощь":
                message.setText("Раздел помощи:\n\n" +
                               "Каталог - просмотр доступных товаров\n" +
                               "Корзина - ваши выбранные товары\n" +
                               "Заказы - история и статус ваших заказов\n" +
                               "Информация - контактные данные магазина\n\n" +
                               "Если у вас возникли проблемы, напишите нам на support@example.com");
                break;
            case "Админ-панель":
                if (isAdmin) {
                    // Создаем клавиатуру для админ-панели
                    ReplyKeyboardMarkup adminKeyboard = new ReplyKeyboardMarkup();
                    List<KeyboardRow> keyboard = new ArrayList<KeyboardRow>();
                    
                    KeyboardRow row1 = new KeyboardRow();
                    row1.add(new KeyboardButton("Добавить категорию"));
                    row1.add(new KeyboardButton("Добавить товар"));
                    keyboard.add(row1);
                    
                    KeyboardRow row2 = new KeyboardRow();
                    row2.add(new KeyboardButton("Управление заказами"));
                    row2.add(new KeyboardButton("Статистика"));
                    keyboard.add(row2);
                    
                    KeyboardRow row3 = new KeyboardRow();
                    row3.add(new KeyboardButton("Назад"));
                    keyboard.add(row3);
                    
                    adminKeyboard.setKeyboard(keyboard);
                    adminKeyboard.setResizeKeyboard(true);
                    adminKeyboard.setOneTimeKeyboard(false);
                    
                    message.setReplyMarkup(adminKeyboard);
                    message.setText("Панель администратора. Выберите действие:");
                } else {
                    message.setText("У вас нет прав доступа к этому разделу.");
                }
                break;
            
            case "Добавить категорию":
                if (isAdmin) {
                    // Устанавливаем состояние пользователя для добавления категории
                    user.setState("ADDING_CATEGORY");
                    userRepository.save(user);
                    message.setText("Введите название новой категории:");
                } else {
                    message.setText("У вас нет прав доступа к этому действию.");
                }
                break;
            
            case "Добавить товар":
                if (isAdmin) {
                    // Устанавливаем состояние пользователя для добавления товара
                    user.setState("ADDING_PRODUCT_CATEGORY");
                    userRepository.save(user);
                    message.setText("Выберите категорию для нового товара:");
                    
                    // Здесь должен быть код для отображения списка категорий
                    // Это будет реализовано в следующем шаге
                } else {
                    message.setText("У вас нет прав доступа к этому действию.");
                }
                break;
            
            case "Назад":
                // Возвращаемся в главное меню
                return handleCommand(chatId, "/start");
            
            default:
                // Обработка состояний пользователя
                if (user != null) {
                    switch (user.getState()) {
                        case "ADDING_CATEGORY":
                            // Логика добавления категории
                            message.setText("Категория '" + text + "' успешно добавлена!");
                            user.setState("ADMIN_MENU");
                            userRepository.save(user);
                            break;
                        // Другие состояния будут добавлены позже
                        default:
                            message.setText("Вы написали: " + text + "\nИспользуйте кнопки или команды для взаимодействия с ботом.");
                    }
                } else {
                    message.setText("Вы написали: " + text + "\nИспользуйте кнопки или команды для взаимодействия с ботом.");
                }
        }
        
        return message;
    }
} 