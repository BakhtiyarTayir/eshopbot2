package uz.uportal.telegramshop.service.bot.keyboards;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import uz.uportal.telegramshop.model.Category;

import java.util.List;

/**
 * Интерфейс для фабрики клавиатур
 */
public interface KeyboardFactory {
    
    /**
     * Создает главную клавиатуру
     * @return главная клавиатура
     */
    ReplyKeyboardMarkup createMainKeyboard();
    
    /**
     * Создает клавиатуру каталога
     * @param categories список категорий
     * @return клавиатура каталога
     */
    InlineKeyboardMarkup createCatalogKeyboard(List<Category> categories);
    
    /**
     * Создает клавиатуру подкатегорий для категории
     * @param subcategories список подкатегорий
     * @param parentCategory родительская категория
     * @return клавиатура подкатегорий
     */
    InlineKeyboardMarkup createSubcategoriesKeyboard(List<Category> subcategories, Category parentCategory);
    
    /**
     * Создает клавиатуру для подтверждения заказа
     * @return клавиатура для подтверждения заказа
     */
    InlineKeyboardMarkup createOrderConfirmationKeyboard();
    
    /**
     * Создает клавиатуру для корзины
     * @return клавиатура для корзины
     */
    InlineKeyboardMarkup createCartKeyboard();
    
    /**
     * Создает главное меню с учетом прав пользователя
     * @param isAdminOrManager флаг администратора/менеджера
     * @return клавиатура главного меню
     */
    ReplyKeyboardMarkup createMainMenuKeyboard(boolean isAdminOrManager);
    
    /**
     * Создает клавиатуру админ-панели
     * @return клавиатура админ-панели
     */
    ReplyKeyboardMarkup createAdminPanelKeyboard();
    
    /**
     * Создает инлайн-клавиатуру для управления товаром
     * @param productId ID товара
     * @return клавиатура управления товаром
     */
    InlineKeyboardMarkup createProductManagementKeyboard(Long productId);
    
    /**
     * Создает инлайн-клавиатуру для управления категорией
     * @param categoryId ID категории
     * @return клавиатура управления категорией
     */
    InlineKeyboardMarkup createCategoryManagementKeyboard(Long categoryId);
    
    /**
     * Создает инлайн-клавиатуру для пагинации списка товаров
     * @param page текущая страница
     * @param totalPages общее количество страниц
     * @return клавиатура пагинации
     */
    InlineKeyboardMarkup createProductPaginationKeyboard(int page, int totalPages);
    
    /**
     * Создает инлайн-клавиатуру для пагинации списка категорий
     * @param page текущая страница
     * @param totalPages общее количество страниц
     * @return клавиатура пагинации
     */
    InlineKeyboardMarkup createCategoryPaginationKeyboard(int page, int totalPages);
    
    /**
     * Создает инлайн-клавиатуру для пагинации списка категорий с кнопками для каждой категории
     * @param page текущая страница
     * @param totalPages общее количество страниц
     * @param categories список категорий
     * @return клавиатура пагинации с кнопками категорий
     */
    InlineKeyboardMarkup createCategoryPaginationKeyboard(int page, int totalPages, List<Category> categories);
    
    /**
     * Создает инлайн-клавиатуру для пагинации списка пользователей
     * @param page текущая страница
     * @param totalPages общее количество страниц
     * @return клавиатура пагинации
     */
    InlineKeyboardMarkup createUserPaginationKeyboard(int page, int totalPages);
} 