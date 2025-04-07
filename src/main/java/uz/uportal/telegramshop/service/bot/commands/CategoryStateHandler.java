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
import uz.uportal.telegramshop.model.TelegramUser;
import uz.uportal.telegramshop.repository.TelegramUserRepository;
import uz.uportal.telegramshop.service.CategoryService;
import uz.uportal.telegramshop.service.bot.core.UpdateHandler;
import uz.uportal.telegramshop.service.bot.keyboards.KeyboardFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Обработчик состояний, связанных с категориями
 */
@Component
public class CategoryStateHandler implements UpdateHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CategoryStateHandler.class);
    private static final Pattern EDITING_CATEGORY_PATTERN = Pattern.compile("EDITING_CATEGORY_(\\d+)");
    private static final Pattern EDITING_CATEGORY_DESCRIPTION_PATTERN = Pattern.compile("EDITING_CATEGORY_DESCRIPTION_(\\d+)");
    
    // Временное хранилище данных для создания/редактирования категорий
    private final Map<Long, CategoryDraft> categoryDrafts = new HashMap<>();
    
    private final TelegramUserRepository telegramUserRepository;
    private final CategoryService categoryService;
    private final KeyboardFactory keyboardFactory;
    
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
        
        return state.equals("ADDING_CATEGORY") ||
                state.equals("ADDING_CATEGORY_NAME") ||
                state.equals("ADDING_CATEGORY_DESCRIPTION") ||
                state.equals("ADDING_CATEGORY_PARENT_SELECTION") ||
                state.equals("EDITING_CATEGORY_NAME") ||
                state.startsWith("EDITING_CATEGORY_") ||
                state.startsWith("EDITING_CATEGORY_DESCRIPTION_");
    }
    
    @Override
    public BotApiMethod<?> handle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return null;
        }
        
        Message message = update.getMessage();
        String text = message.getText();
        Long chatId = message.getChatId();
        
        // Получаем пользователя и его состояние
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user == null || user.getState() == null) {
            return null;
        }
        
        String state = user.getState();
        
        if (state.equals("ADDING_CATEGORY") || state.equals("ADDING_CATEGORY_NAME")) {
            return handleAddingCategoryName(chatId, text);
        } else if (state.equals("ADDING_CATEGORY_DESCRIPTION")) {
            return handleAddingCategoryDescription(chatId, text);
        } else if (state.equals("ADDING_CATEGORY_PARENT_SELECTION")) {
            return handleAddingCategoryParentSelection(chatId, text);
        } else if (state.equals("EDITING_CATEGORY_NAME")) {
            // Получаем ID категории из временных данных
            String categoryIdStr = user.getTempData();
            if (categoryIdStr == null || categoryIdStr.isEmpty()) {
                return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните редактирование категории заново.");
            }
            
            Long categoryId = Long.parseLong(categoryIdStr);
            return handleEditingCategoryName(chatId, text, categoryId);
        } else {
            // Проверяем, является ли состояние редактированием категории
            Matcher matcher = EDITING_CATEGORY_PATTERN.matcher(state);
            if (matcher.matches()) {
                Long categoryId = Long.parseLong(matcher.group(1));
                return handleEditingCategoryName(chatId, text, categoryId);
            }
            
            // Проверяем, является ли состояние редактированием описания категории
            matcher = EDITING_CATEGORY_DESCRIPTION_PATTERN.matcher(state);
            if (matcher.matches()) {
                Long categoryId = Long.parseLong(matcher.group(1));
                return handleEditingCategoryDescription(chatId, text, categoryId);
            }
            
            return null;
        }
    }
    
    /**
     * Обрабатывает добавление имени категории
     * @param chatId ID чата
     * @param categoryName имя категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddingCategoryName(Long chatId, String categoryName) {
        // Проверяем, существует ли категория с таким именем
        Category existingCategory = categoryService.getCategoryByName(categoryName);
        if (existingCategory != null) {
            return createTextMessage(chatId, "Категория с таким названием уже существует. Пожалуйста, введите другое название:");
        }
        
        // Создаем черновик категории
        categoryDrafts.put(chatId, new CategoryDraft(categoryName));
        
        // Обновляем состояние пользователя
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState("ADDING_CATEGORY_DESCRIPTION");
            telegramUserRepository.save(user);
        }
        
        return createTextMessage(chatId, "Введите описание категории (или отправьте 'нет' для пропуска):");
    }
    
    /**
     * Обрабатывает добавление описания категории
     * @param chatId ID чата
     * @param description описание категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddingCategoryDescription(Long chatId, String description) {
        // Получаем черновик категории
        CategoryDraft draft = categoryDrafts.get(chatId);
        if (draft == null) {
            return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните добавление категории заново.");
        }
        
        // Если описание не "нет", сохраняем его
        if (!description.equalsIgnoreCase("нет")) {
            draft.setDescription(description);
        }
        
        // Получаем список категорий для выбора родительской
        List<Category> categories = categoryService.getAllCategories();
        
        // Обновляем состояние пользователя
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState("ADDING_CATEGORY_PARENT_SELECTION");
            telegramUserRepository.save(user);
        }
        
        // Формируем сообщение с выбором родительской категории
        StringBuilder messageText = new StringBuilder("Выберите родительскую категорию для новой категории (введите номер) или отправьте \"0\" для создания основной категории:\n\n");
        messageText.append("0. [Создать основную категорию без родителя]\n");
        
        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            messageText.append(i + 1).append(". ").append(category.getName()).append("\n");
        }
        
        return createTextMessage(chatId, messageText.toString());
    }
    
    /**
     * Обрабатывает выбор родительской категории
     * @param chatId ID чата
     * @param selection выбор пользователя
     * @return ответ бота
     */
    private BotApiMethod<?> handleAddingCategoryParentSelection(Long chatId, String selection) {
        // Получаем черновик категории
        CategoryDraft draft = categoryDrafts.get(chatId);
        if (draft == null) {
            return createTextMessage(chatId, "Произошла ошибка. Пожалуйста, начните добавление категории заново.");
        }
        
        try {
            int selectedOption = Integer.parseInt(selection);
            
            Category category;
            
            if (selectedOption == 0) {
                // Создаем основную категорию без родителя
                category = categoryService.createCategory(draft.getName(), draft.getDescription());
            } else {
                // Создаем подкатегорию
                List<Category> categories = categoryService.getAllCategories();
                int categoryIndex = selectedOption - 1;
                
                if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                    return createTextMessage(chatId, "Некорректный номер категории. Пожалуйста, выберите из списка:");
                }
                
                Category parentCategory = categories.get(categoryIndex);
                category = categoryService.createSubcategory(
                        draft.getName(), 
                        draft.getDescription(), 
                        parentCategory.getId()
                );
            }
            
            // Очищаем черновик
            categoryDrafts.remove(chatId);
            
            // Обновляем состояние пользователя на обычное
            TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
            if (user != null) {
                user.setState(null);
                telegramUserRepository.save(user);
            }
            
            // Формируем сообщение об успешном создании
            String successMessage;
            if (category.getParent() == null) {
                successMessage = "✅ Основная категория \"" + category.getName() + "\" успешно добавлена!";
            } else {
                successMessage = "✅ Подкатегория \"" + category.getName() + "\" успешно добавлена в категорию \"" 
                        + category.getParent().getName() + "\"!";
            }
            
            // Отправляем сообщение об успешном создании
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText(successMessage);
            sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
            
            return sendMessage;
            
        } catch (NumberFormatException e) {
            return createTextMessage(chatId, "Некорректный ввод. Пожалуйста, введите номер категории или 0 для основной категории:");
        }
    }
    
    /**
     * Обрабатывает редактирование имени категории
     * @param chatId ID чата
     * @param newName новое имя категории
     * @param categoryId ID категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditingCategoryName(Long chatId, String newName, Long categoryId) {
        // Проверяем, существует ли категория с таким именем
        Category existingCategory = categoryService.getCategoryByName(newName);
        if (existingCategory != null && !existingCategory.getId().equals(categoryId)) {
            return createTextMessage(chatId, "Категория с таким названием уже существует. Пожалуйста, введите другое название:");
        }
        
        // Получаем категорию для редактирования
        Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
        if (categoryOpt.isEmpty()) {
            return createTextMessage(chatId, "Категория не найдена. Пожалуйста, вернитесь в меню категорий.");
        }
        
        Category category = categoryOpt.get();
        String oldName = category.getName();
        
        // Обновляем категорию
        category = categoryService.updateCategory(categoryId, newName, category.getDescription());
        
        // Обновляем состояние пользователя на обычное
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState(null);
            telegramUserRepository.save(user);
        }
        
        // Отправляем сообщение об успешном редактировании
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("✅ Название категории изменено с \"" + oldName + "\" на \"" + category.getName() + "\"!");
        sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
        
        return sendMessage;
    }
    
    /**
     * Обрабатывает редактирование описания категории
     * @param chatId ID чата
     * @param newDescription новое описание категории
     * @param categoryId ID категории
     * @return ответ бота
     */
    private BotApiMethod<?> handleEditingCategoryDescription(Long chatId, String newDescription, Long categoryId) {
        // Получаем категорию для редактирования
        Optional<Category> categoryOpt = categoryService.getCategoryById(categoryId);
        if (categoryOpt.isEmpty()) {
            return createTextMessage(chatId, "Категория не найдена. Пожалуйста, вернитесь в меню категорий.");
        }
        
        Category category = categoryOpt.get();
        
        // Обновляем категорию
        category = categoryService.updateCategory(categoryId, category.getName(), newDescription);
        
        // Обновляем состояние пользователя на обычное
        TelegramUser user = telegramUserRepository.findById(chatId).orElse(null);
        if (user != null) {
            user.setState(null);
            telegramUserRepository.save(user);
        }
        
        // Отправляем сообщение об успешном редактировании
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("✅ Описание категории \"" + category.getName() + "\" успешно обновлено!");
        sendMessage.setReplyMarkup(keyboardFactory.createAdminPanelKeyboard());
        
        return sendMessage;
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
        return sendMessage;
    }
    
    /**
     * Класс для хранения черновика категории
     */
    private static class CategoryDraft {
        private String name;
        private String description;
        private Long parentId; // ID родительской категории (для подкатегорий)
        
        public CategoryDraft(String name) {
            this.name = name;
        }
        
        public CategoryDraft(String name, String description) {
            this.name = name;
            this.description = description;
        }
        
        public CategoryDraft(String name, String description, Long parentId) {
            this.name = name;
            this.description = description;
            this.parentId = parentId;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public Long getParentId() {
            return parentId;
        }
        
        public void setParentId(Long parentId) {
            this.parentId = parentId;
        }
    }
} 