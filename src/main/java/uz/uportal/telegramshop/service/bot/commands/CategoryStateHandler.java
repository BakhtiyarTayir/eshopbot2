package uz.uportal.telegramshop.service.bot.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.CategoryService;
import uz.uportal.telegramshop.service.bot.core.StateHandler;
import uz.uportal.telegramshop.service.bot.keyboards.KeyboardFactory;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Обработчик состояний, связанных с категориями
 */
@Component
public class CategoryStateHandler implements StateHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CategoryStateHandler.class);
    
    private final TelegramUserRepository telegramUserRepository;
    private final CategoryService categoryService;
    private final KeyboardFactory keyboardFactory;
    
    // Регулярное выражение для извлечения ID категории из состояния
    private static final Pattern EDITING_CATEGORY_PATTERN = Pattern.compile("EDITING_CATEGORY_(\\d+)");
    private static final Pattern EDITING_CATEGORY_DESCRIPTION_PATTERN = Pattern.compile("EDITING_CATEGORY_DESCRIPTION_(\\d+)");
    
    public CategoryStateHandler(
            TelegramUserRepository telegramUserRepository,
            CategoryService categoryService,
            KeyboardFactory keyboardFactory) {
        this.telegramUserRepository = telegramUserRepository;
        this.categoryService = categoryService;
        this.keyboardFactory = keyboardFactory;
    }
    
    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }
        
        Long chatId = update.getMessage().getChatId();
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        
        if (user == null || user.getState() == null) {
            return false;
        }
        
        String state = user.getState();
        return state.equals("ADDING_CATEGORY_NAME") || 
               state.equals("ADDING_CATEGORY_DESCRIPTION") ||
               state.startsWith("EDITING_CATEGORY_") ||
               state.startsWith("EDITING_CATEGORY_DESCRIPTION_");
    }
    
    @Override
    public BotApiMethod<?> handle(Update update) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String text = message.getText();
        
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        
        if (user == null || user.getState() == null) {
            return null;
        }
        
        String state = user.getState();
        return handleState(update, state);
    }
    
    @Override
    public boolean canHandleState(Update update, String state) {
        return state.equals("ADDING_CATEGORY_NAME") || 
               state.equals("ADDING_CATEGORY_DESCRIPTION") ||
               state.startsWith("EDITING_CATEGORY_") ||
               state.startsWith("EDITING_CATEGORY_DESCRIPTION_");
    }
    
    @Override
    public BotApiMethod<?> handleState(Update update, String state) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String text = message.getText();
        
        if (state.equals("ADDING_CATEGORY_NAME")) {
            return handleAddingCategoryName(chatId, text);
        } else if (state.equals("ADDING_CATEGORY_DESCRIPTION")) {
            return handleAddingCategoryDescription(chatId, text);
        } else {
            // Проверяем, является ли состояние редактированием категории
            Matcher editingMatcher = EDITING_CATEGORY_PATTERN.matcher(state);
            if (editingMatcher.matches()) {
                Long categoryId = Long.parseLong(editingMatcher.group(1));
                return handleEditingCategoryName(chatId, categoryId, text);
            }
            
            // Проверяем, является ли состояние редактированием описания категории
            Matcher descriptionMatcher = EDITING_CATEGORY_DESCRIPTION_PATTERN.matcher(state);
            if (descriptionMatcher.matches()) {
                Long categoryId = Long.parseLong(descriptionMatcher.group(1));
                return handleEditingCategoryDescription(chatId, categoryId, text);
            }
        }
        
        return null;
    }
    
    /**
     * Создает текстовое сообщение
     * @param chatId ID чата
     * @param text текст сообщения
     * @return объект сообщения
     */
    private SendMessage createTextMessage(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        sendMessage.enableMarkdown(true);
        return sendMessage;
    }
    
    /**
     * Обрабатывает ввод названия категории
     * @param chatId ID чата
     * @param name название категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddingCategoryName(Long chatId, String name) {
        // Проверяем, хочет ли пользователь отменить операцию
        if (name.equalsIgnoreCase("отмена") || name.equalsIgnoreCase("cancel")) {
            // Сбрасываем состояние пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState(null);
                user.setTempData(null);
                telegramUserRepository.save(user);
            }
            
            // Отправляем сообщение об отмене
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("❌ *Добавление категории отменено*");
            sendMessage.enableMarkdown(true);
            sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
            
            return sendMessage;
        }
        
        // Проверяем, что название не пустое
        if (name == null || name.trim().isEmpty()) {
            return createTextMessage(chatId, "Название категории не может быть пустым. Пожалуйста, введите название категории (или 'отмена' для отмены):");
        }
        
        // Проверяем, существует ли категория с таким названием
        Category existingCategory = categoryService.getCategoryByName(name);
        if (existingCategory != null) {
            return createTextMessage(chatId, "Категория с таким названием уже существует. Пожалуйста, введите другое название (или 'отмена' для отмены):");
        }
        
        // Сохраняем название категории во временном хранилище (в данном случае, в состоянии пользователя)
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setTempData(name); // Используем поле tempData для хранения названия категории
            user.setState("ADDING_CATEGORY_DESCRIPTION");
            telegramUserRepository.save(user);
        }
        
        // Запрашиваем описание категории
        return createTextMessage(chatId, "Введите описание категории (или введите 'Не указано', если описание не требуется, или 'отмена' для отмены):");
    }
    
    /**
     * Обрабатывает ввод описания категории
     * @param chatId ID чата
     * @param description описание категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddingCategoryDescription(Long chatId, String description) {
        // Проверяем, хочет ли пользователь отменить операцию
        if (description.equalsIgnoreCase("отмена") || description.equalsIgnoreCase("cancel")) {
            // Сбрасываем состояние пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState(null);
                user.setTempData(null);
                telegramUserRepository.save(user);
            }
            
            // Отправляем сообщение об отмене
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("❌ *Добавление категории отменено*");
            sendMessage.enableMarkdown(true);
            sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
            
            return sendMessage;
        }
        
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        
        if (user == null || user.getTempData() == null) {
            // Если данные пользователя не найдены, сбрасываем состояние
            if (user != null) {
                user.setState(null);
                user.setTempData(null);
                telegramUserRepository.save(user);
            }
            
            return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните добавление категории заново.");
        }
        
        String name = user.getTempData();
        
        // Если описание не указано, используем значение по умолчанию
        if (description.equalsIgnoreCase("Не указано")) {
            description = "Не указано";
        }
        
        // Создаем новую категорию
        try {
            Category category = categoryService.createCategory(name, description);
            
            // Сбрасываем состояние пользователя
            user.setState(null);
            user.setTempData(null);
            telegramUserRepository.save(user);
            
            // Отправляем сообщение об успешном создании категории
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("✅ Категория \"*" + category.getName() + "*\" успешно создана!");
            sendMessage.enableMarkdown(true);
            sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
            
            return sendMessage;
        } catch (Exception e) {
            logger.error("Ошибка при создании категории: {}", e.getMessage());
            
            // Сбрасываем состояние пользователя
            user.setState(null);
            user.setTempData(null);
            telegramUserRepository.save(user);
            
            return createTextMessage(chatId, "Произошла ошибка при создании категории. Пожалуйста, попробуйте еще раз.");
        }
    }
    
    /**
     * Обрабатывает редактирование названия категории
     * @param chatId ID чата
     * @param categoryId ID категории
     * @param name новое название категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditingCategoryName(Long chatId, Long categoryId, String name) {
        // Проверяем, хочет ли пользователь отменить операцию
        if (name.equalsIgnoreCase("отмена") || name.equalsIgnoreCase("cancel")) {
            // Сбрасываем состояние пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState(null);
                user.setTempData(null);
                telegramUserRepository.save(user);
            }
            
            // Отправляем сообщение об отмене
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("❌ *Редактирование категории отменено*");
            sendMessage.enableMarkdown(true);
            sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
            
            return sendMessage;
        }
        
        // Проверяем, что название не пустое
        if (name == null || name.trim().isEmpty()) {
            return createTextMessage(chatId, "Название категории не может быть пустым. Пожалуйста, введите название категории (или 'отмена' для отмены):");
        }
        
        // Получаем категорию из базы данных
        Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
        if (categoryOpt.isEmpty()) {
            // Сбрасываем состояние пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState(null);
                telegramUserRepository.save(user);
            }
            
            return createTextMessage(chatId, "Категория не найдена. Возможно, она была удалена.");
        }
        
        Category category = categoryOpt.get();
        
        // Проверяем, существует ли другая категория с таким названием
        Category existingCategory = categoryService.getCategoryByName(name);
        if (existingCategory != null && !existingCategory.getId().equals(categoryId)) {
            return createTextMessage(chatId, "Категория с таким названием уже существует. Пожалуйста, введите другое название (или 'отмена' для отмены):");
        }
        
        // Сохраняем старое название для сообщения
        String oldName = category.getName();
        
        // Сохраняем новое название во временном хранилище
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setTempData(name);
            user.setState("EDITING_CATEGORY_DESCRIPTION_" + categoryId);
            telegramUserRepository.save(user);
        }
        
        // Отправляем сообщение с запросом описания
        StringBuilder messageText = new StringBuilder();
        messageText.append("✏️ *Редактирование категории*\n\n");
        messageText.append("Название категории изменено с \"*").append(oldName).append("*\" на \"*").append(name).append("*\".\n\n");
        messageText.append("Текущее описание: ").append(category.getDescription() != null ? category.getDescription() : "Не указано").append("\n\n");
        messageText.append("Введите новое описание категории (или введите 'Не менять', чтобы оставить текущее описание, или 'отмена' для отмены):");
        
        return createTextMessage(chatId, messageText.toString());
    }
    
    /**
     * Обрабатывает редактирование описания категории
     * @param chatId ID чата
     * @param categoryId ID категории
     * @param description новое описание категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditingCategoryDescription(Long chatId, Long categoryId, String description) {
        // Проверяем, хочет ли пользователь отменить операцию
        if (description.equalsIgnoreCase("отмена") || description.equalsIgnoreCase("cancel")) {
            // Сбрасываем состояние пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState(null);
                user.setTempData(null);
                telegramUserRepository.save(user);
            }
            
            // Отправляем сообщение об отмене
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("❌ *Редактирование категории отменено*");
            sendMessage.enableMarkdown(true);
            sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
            
            return sendMessage;
        }
        
        // Получаем категорию из базы данных
        Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
        if (categoryOpt.isEmpty()) {
            // Сбрасываем состояние пользователя
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState(null);
                user.setTempData(null);
                telegramUserRepository.save(user);
            }
            
            return createTextMessage(chatId, "Категория не найдена. Возможно, она была удалена.");
        }
        
        Category category = categoryOpt.get();
        
        // Получаем новое название из временного хранилища
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user == null || user.getTempData() == null) {
            // Если данные пользователя не найдены, сбрасываем состояние
            if (user != null) {
                user.setState(null);
                telegramUserRepository.save(user);
            }
            
            return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование категории заново.");
        }
        
        String newName = user.getTempData();
        String newDescription = description;
        
        // Если пользователь не хочет менять описание
        if (description.equalsIgnoreCase("Не менять")) {
            newDescription = category.getDescription();
        }
        
        // Обновляем категорию
        try {
            Category updatedCategory = categoryService.updateCategory(categoryId, newName, newDescription);
            
            // Сбрасываем состояние пользователя
            user.setState(null);
            user.setTempData(null);
            telegramUserRepository.save(user);
            
            // Отправляем сообщение об успешном обновлении категории
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("✅ Категория \"*" + updatedCategory.getName() + "*\" успешно обновлена!");
            sendMessage.enableMarkdown(true);
            sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
            
            return sendMessage;
        } catch (Exception e) {
            logger.error("Ошибка при обновлении категории: {}", e.getMessage());
            
            // Сбрасываем состояние пользователя
            user.setState(null);
            user.setTempData(null);
            telegramUserRepository.save(user);
            
            return createTextMessage(chatId, "Произошла ошибка при обновлении категории. Пожалуйста, попробуйте еще раз.");
        }
    }
} 