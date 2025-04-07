package uz.uportal.telegramshop.service.bot.keyboards;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.uportal.telegramshop.model.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * Реализация фабрики клавиатур
 */
@Component
public class KeyboardFactoryImpl implements KeyboardFactory {

    @Override
    public ReplyKeyboardMarkup createMainKeyboard() {
        // Создаем клавиатуру
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);
        
        // Создаем строки клавиатуры
        List<KeyboardRow> keyboard = new ArrayList<>();
        
        // Первая строка
        KeyboardRow row1 = new KeyboardRow();
        row1.add("🛍 Каталог");
        row1.add("🛒 Корзина");
        keyboard.add(row1);
        
        // Вторая строка
        KeyboardRow row2 = new KeyboardRow();
        row2.add("ℹ️ Информация");
        row2.add("📞 Поддержка");
        keyboard.add(row2);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    @Override
    public InlineKeyboardMarkup createCatalogKeyboard(List<Category> categories) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        for (Category category : categories) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(category.getName());
            button.setCallbackData("catalog_category_" + category.getId());
            row.add(button);
            keyboard.add(row);
        }
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    @Override
    public InlineKeyboardMarkup createSubcategoriesKeyboard(List<Category> subcategories, Category parentCategory) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Добавляем кнопки подкатегорий
        for (Category subcategory : subcategories) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(subcategory.getName());
            // Формат callback: catalog_subcategory_SUBCATEGORY_ID_PARENT_ID
            button.setCallbackData("catalog_subcategory_" + subcategory.getId() + "_" + parentCategory.getId());
            row.add(button);
            keyboard.add(row);
        }
        
        // Добавляем кнопку возврата к родительской категории или к главному каталогу
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        
        if (parentCategory.getParent() != null) {
            backButton.setText("⬅️ Назад к " + parentCategory.getParent().getName());
            backButton.setCallbackData("catalog_back_to_parent_" + parentCategory.getParent().getId());
        } else {
            backButton.setText("⬅️ Назад к категориям");
            backButton.setCallbackData("catalog_back_to_parent_0");
        }
        
        backRow.add(backButton);
        keyboard.add(backRow);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    @Override
    public InlineKeyboardMarkup createOrderConfirmationKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("✅ Подтвердить");
        confirmButton.setCallbackData("confirm_order");
        row.add(confirmButton);
        
        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отменить");
        cancelButton.setCallbackData("cancel_order");
        row.add(cancelButton);
        
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    @Override
    public InlineKeyboardMarkup createCartKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton checkoutButton = new InlineKeyboardButton();
        checkoutButton.setText("🛍 Оформить заказ");
        checkoutButton.setCallbackData("checkout");
        row1.add(checkoutButton);
        keyboard.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton clearButton = new InlineKeyboardButton();
        clearButton.setText("🗑 Очистить корзину");
        clearButton.setCallbackData("clear_cart");
        row2.add(clearButton);
        keyboard.add(row2);
        
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Назад в каталог");
        backButton.setCallbackData("back_to_catalog");
        row3.add(backButton);
        keyboard.add(row3);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    @Override
    public ReplyKeyboardMarkup createMainMenuKeyboard(boolean isAdminOrManager) {
        // Создаем клавиатуру
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);
        
        // Создаем строки клавиатуры
        List<KeyboardRow> keyboard = new ArrayList<>();
        
        // Первая строка
        KeyboardRow row1 = new KeyboardRow();
        row1.add("🛍 Каталог");
        row1.add("🛒 Корзина");
        keyboard.add(row1);
        
        // Вторая строка
        KeyboardRow row2 = new KeyboardRow();
        row2.add("ℹ️ Информация");
        row2.add("📞 Поддержка");
        keyboard.add(row2);
        
        // Если пользователь админ или менеджер, добавляем кнопку админ-панели
        if (isAdminOrManager) {
            KeyboardRow row3 = new KeyboardRow();
            row3.add("⚙️ Админ панель");
            keyboard.add(row3);
        }
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
    
    @Override
    public ReplyKeyboardMarkup createAdminPanelKeyboard() {
        // Создаем клавиатуру
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);
        
        // Создаем строки клавиатуры
        List<KeyboardRow> keyboard = new ArrayList<>();
        
        // Первая строка
        KeyboardRow row1 = new KeyboardRow();
        row1.add("📋 Список товаров");
        row1.add("➕ Добавить товар");
        keyboard.add(row1);
        
        // Вторая строка
        KeyboardRow row2 = new KeyboardRow();
        row2.add("🗂 Список категорий");
        row2.add("➕ Добавить категорию");
        keyboard.add(row2);
        
        // Третья строка
        KeyboardRow row3 = new KeyboardRow();
        row3.add("📦 Управление заказами");
        row3.add("👥 Список пользователей");
        keyboard.add(row3);
        
        // Четвертая строка
        KeyboardRow row4 = new KeyboardRow();
        row4.add("⚙️ Настройки магазина");
        keyboard.add(row4);
        
        // Пятая строка
        KeyboardRow row5 = new KeyboardRow();
        row5.add("⬅️ Вернуться в главное меню");
        keyboard.add(row5);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
    
    @Override
    public InlineKeyboardMarkup createProductManagementKeyboard(Long productId) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton editButton = new InlineKeyboardButton();
        editButton.setText("✏️ Редактировать");
        editButton.setCallbackData("edit_product_" + productId);
        row.add(editButton);
        
        InlineKeyboardButton deleteButton = new InlineKeyboardButton();
        deleteButton.setText("🗑 Удалить");
        deleteButton.setCallbackData("delete_product_" + productId);
        row.add(deleteButton);
        
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
    
    @Override
    public InlineKeyboardMarkup createCategoryManagementKeyboard(Long categoryId) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton editButton = new InlineKeyboardButton();
        editButton.setText("✏️ Редактировать");
        editButton.setCallbackData("edit_category_" + categoryId);
        row.add(editButton);
        
        InlineKeyboardButton deleteButton = new InlineKeyboardButton();
        deleteButton.setText("🗑 Удалить");
        deleteButton.setCallbackData("delete_category_" + categoryId);
        row.add(deleteButton);
        
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
    
    @Override
    public InlineKeyboardMarkup createProductPaginationKeyboard(int page, int totalPages) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопки навигации
        List<InlineKeyboardButton> navigationRow = new ArrayList<>();
        
        if (page > 1) {
            InlineKeyboardButton prevButton = new InlineKeyboardButton();
            prevButton.setText("⬅️ Предыдущая");
            prevButton.setCallbackData("products_page_" + (page - 1));
            navigationRow.add(prevButton);
        }
        
        if (page < totalPages) {
            InlineKeyboardButton nextButton = new InlineKeyboardButton();
            nextButton.setText("Следующая ➡️");
            nextButton.setCallbackData("products_page_" + (page + 1));
            navigationRow.add(nextButton);
        }
        
        if (!navigationRow.isEmpty()) {
            keyboard.add(navigationRow);
        }
        
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
    
    @Override
    public InlineKeyboardMarkup createCategoryPaginationKeyboard(int page, int totalPages, List<Category> categories) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопки для каждой категории
        for (Category category : categories) {
            // Кнопки редактирования и удаления для каждой категории
            List<InlineKeyboardButton> categoryRow = new ArrayList<>();
            
            InlineKeyboardButton editButton = new InlineKeyboardButton();
            editButton.setText("✏️ Редактировать");
            editButton.setCallbackData("edit_category_" + category.getId());
            categoryRow.add(editButton);
            
            InlineKeyboardButton deleteButton = new InlineKeyboardButton();
            deleteButton.setText("🗑 Удалить");
            deleteButton.setCallbackData("delete_category_" + category.getId());
            categoryRow.add(deleteButton);
            
            keyboard.add(categoryRow);
        }
        
        // Кнопки навигации
        List<InlineKeyboardButton> navigationRow = new ArrayList<>();
        
        if (page > 1) {
            InlineKeyboardButton prevButton = new InlineKeyboardButton();
            prevButton.setText("⬅️ Предыдущая");
            prevButton.setCallbackData("categories_page_" + (page - 1));
            navigationRow.add(prevButton);
        }
        
        if (page < totalPages) {
            InlineKeyboardButton nextButton = new InlineKeyboardButton();
            nextButton.setText("Следующая ➡️");
            nextButton.setCallbackData("categories_page_" + (page + 1));
            navigationRow.add(nextButton);
        }
        
        if (!navigationRow.isEmpty()) {
            keyboard.add(navigationRow);
        }
        
        // Кнопка возврата
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Назад в админ-панель");
        backButton.setCallbackData("back_to_admin");
        backRow.add(backButton);
        keyboard.add(backRow);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
    
    @Override
    public InlineKeyboardMarkup createCategoryPaginationKeyboard(int page, int totalPages) {
        // Для обратной совместимости
        return createCategoryPaginationKeyboard(page, totalPages, new ArrayList<>());
    }
    
    @Override
    public InlineKeyboardMarkup createUserPaginationKeyboard(int page, int totalPages) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопки навигации
        List<InlineKeyboardButton> navigationRow = new ArrayList<>();
        
        if (page > 1) {
            InlineKeyboardButton prevButton = new InlineKeyboardButton();
            prevButton.setText("⬅️ Предыдущая");
            prevButton.setCallbackData("users_page_" + (page - 1));
            navigationRow.add(prevButton);
        }
        
        if (page < totalPages) {
            InlineKeyboardButton nextButton = new InlineKeyboardButton();
            nextButton.setText("Следующая ➡️");
            nextButton.setCallbackData("users_page_" + (page + 1));
            navigationRow.add(nextButton);
        }
        
        if (!navigationRow.isEmpty()) {
            keyboard.add(navigationRow);
        }
        
        // Кнопка возврата
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Назад в админ-панель");
        backButton.setCallbackData("back_to_admin");
        backRow.add(backButton);
        keyboard.add(backRow);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
} 