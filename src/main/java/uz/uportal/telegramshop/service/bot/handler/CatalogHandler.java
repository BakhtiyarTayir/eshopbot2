package uz.uportal.telegramshop.service.bot.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.uportal.telegramshop.model.Category;
import uz.uportal.telegramshop.model.Product;
import uz.uportal.telegramshop.service.CategoryService;
import uz.uportal.telegramshop.service.ProductService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик каталога товаров
 */
@Component
public class CatalogHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CatalogHandler.class);
    private static final int PRODUCTS_PER_PAGE = 2;
    
    private final CategoryService categoryService;
    private final ProductService productService;
    
    // Хранение текущей категории и страницы для пользователей
    private final Map<Long, String> currentCategory = new HashMap<>();
    private final Map<Long, Integer> currentPage = new HashMap<>();
    
    public CatalogHandler(CategoryService categoryService, ProductService productService) {
        this.categoryService = categoryService;
        this.productService = productService;
    }
    
    /**
     * Показать каталог категорий
     * @param chatId ID чата
     * @return сообщение с каталогом
     */
    public SendMessage showCatalog(Long chatId) {
        logger.info("Showing catalog for user {}", chatId);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        
        List<Category> categories = categoryService.getAllCategories();
        
        if (categories.isEmpty()) {
            message.setText("Каталог пока пуст. Скоро здесь появятся товары!");
            return message;
        }
        
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        
        for (Category category : categories) {
            List<InlineKeyboardButton> rowInline = new ArrayList<>();
            InlineKeyboardButton categoryButton = new InlineKeyboardButton();
            categoryButton.setText(category.getName());
            categoryButton.setCallbackData("category:" + category.getSlug());
            rowInline.add(categoryButton);
            rowsInline.add(rowInline);
        }
        
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);
        message.setText("Выберите категорию:");
        
        return message;
    }
    
    /**
     * Показать товары категории
     * @param chatId ID чата
     * @param categorySlug слаг категории
     * @param page номер страницы
     * @return список сообщений с товарами
     */
    public List<Object> showCategoryProducts(Long chatId, String categorySlug, int page) {
        logger.info("Showing products for category: {}, page: {}", categorySlug, page);
        List<Object> messages = new ArrayList<>();
        
        // Сохраняем текущую категорию и страницу
        currentCategory.put(chatId, categorySlug);
        currentPage.put(chatId, page);
        
        // Получаем товары для выбранной категории
        List<Product> products;
        if (categorySlug == null || categorySlug.isEmpty()) {
            products = productService.getAllProducts();
            logger.info("Fetched all products, total count: {}", products.size());
        } else {
            // Получаем категорию по слагу
            Category category = categoryService.getCategoryBySlug(categorySlug);
            if (category != null) {
                // Получаем продукты по категории
                products = productService.getProductsByCategory(category);
                logger.info("Fetched products for category {}, total count: {}", categorySlug, products.size());
            } else {
                logger.warn("Category not found for slug: {}", categorySlug);
                SendMessage message = new SendMessage();
                message.setChatId(chatId);
                message.setText("Категория не найдена.");
                messages.add(message);
                return messages;
            }
        }
        
        // Если товаров нет, отправляем сообщение об этом
        if (products.isEmpty()) {
            logger.info("No products found for category: {}", categorySlug);
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("В данной категории нет товаров.");
            messages.add(message);
            return messages;
        }
        
        // Определяем индексы для пагинации
        int startIndex = page * PRODUCTS_PER_PAGE;
        int endIndex = Math.min(startIndex + PRODUCTS_PER_PAGE, products.size());
        
        logger.info("Showing products from index {} to {}, total products: {}", startIndex, endIndex, products.size());
        
        // Проверяем, что startIndex не выходит за пределы списка
        if (startIndex >= products.size()) {
            logger.warn("Start index {} is out of bounds for products list with size {}", startIndex, products.size());
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Нет больше товаров для отображения.");
            messages.add(message);
            return messages;
        }
        
        // Получаем товары для текущей страницы
        List<Product> pageProducts = products.subList(startIndex, endIndex);
        logger.info("Page products count: {}", pageProducts.size());
        
        // Отправляем каждый товар отдельным сообщением
        for (int i = 0; i < pageProducts.size(); i++) {
            Product product = pageProducts.get(i);
            logger.info("Adding product message for product: {} (ID: {})", product.getName(), product.getId());
            
            // Формируем текст с информацией о товаре
            StringBuilder caption = new StringBuilder();
            caption.append("Название: ").append(product.getName()).append("\n");
            caption.append("Цена: ").append(product.getPrice()).append(" $\n");
            caption.append("Артикул: ").append(String.format("%08d", product.getId())).append("\n");
            caption.append("В наличии: ").append(product.getStock());
            
            // Создаем клавиатуру с кнопками действий
            InlineKeyboardMarkup productMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> productRows = new ArrayList<>();
            
            // Кнопка "Купить" - теперь для прямого оформления заказа
            List<InlineKeyboardButton> buyRow = new ArrayList<>();
            InlineKeyboardButton buyButton = new InlineKeyboardButton();
            buyButton.setText("Купить");
            buyButton.setCallbackData("direct_buy:" + product.getId());
            buyRow.add(buyButton);
            productRows.add(buyRow);
            
            // Кнопка "В корзину" - для добавления в корзину
            List<InlineKeyboardButton> cartRow = new ArrayList<>();
            InlineKeyboardButton cartButton = new InlineKeyboardButton();
            cartButton.setText("В корзину");
            cartButton.setCallbackData("add_to_cart:" + product.getId());
            cartRow.add(cartButton);
            productRows.add(cartRow);
            
            // Если это последний товар на странице и есть еще товары, добавляем кнопку "Смотреть еще"
            boolean isLastProduct = i == pageProducts.size() - 1;
            boolean hasMoreProducts = endIndex < products.size();
            
            if (isLastProduct && hasMoreProducts) {
                // Кнопка "Смотреть еще"
                List<InlineKeyboardButton> moreRow = new ArrayList<>();
                InlineKeyboardButton moreButton = new InlineKeyboardButton();
                moreButton.setText("🔽 Смотреть еще 🔽");
                moreButton.setCallbackData("more");
                moreRow.add(moreButton);
                productRows.add(moreRow);
                logger.info("Adding 'See more' button to the last product (index: {})", i);
            }
            
            productMarkup.setKeyboard(productRows);
            
            // Создаем сообщение с товаром
            if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                // Если у товара есть изображение, отправляем фото с подписью
                SendPhoto photoMessage = new SendPhoto();
                photoMessage.setChatId(chatId);
                
                // Проверяем, является ли imageUrl ссылкой или fileId
                if (product.getImageUrl().startsWith("http")) {
                    photoMessage.setPhoto(new InputFile(product.getImageUrl()));
                } else {
                    photoMessage.setPhoto(new InputFile(product.getImageUrl()));
                }
                
                photoMessage.setCaption(caption.toString());
                photoMessage.setReplyMarkup(productMarkup);
                
                // Добавляем сообщение в список
                messages.add(photoMessage);
                logger.info("Added photo message for product: {}", product.getId());
            } else {
                // Если у товара нет изображения, отправляем обычное сообщение
                SendMessage productMessage = new SendMessage();
                productMessage.setChatId(chatId);
                productMessage.setText(caption.toString());
                productMessage.setReplyMarkup(productMarkup);
                
                // Добавляем сообщение в список
                messages.add(productMessage);
                logger.info("Added text message for product: {}", product.getId());
            }
        }
        
        // Добавляем навигационные кнопки после всех товаров
        SendMessage navigationMessage = new SendMessage();
        navigationMessage.setChatId(chatId);
        
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        
        // Кнопка "Назад"
        List<InlineKeyboardButton> navigationRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад 🔙");
        backButton.setCallbackData("back");
        navigationRow.add(backButton);
        
        // Кнопка "Главная"
        InlineKeyboardButton homeButton = new InlineKeyboardButton();
        homeButton.setText("🏠 Главная");
        homeButton.setCallbackData("home");
        navigationRow.add(homeButton);
        
        rowsInline.add(navigationRow);
        markupInline.setKeyboard(rowsInline);
        navigationMessage.setReplyMarkup(markupInline);
        navigationMessage.setText("Навигация");
        
        messages.add(navigationMessage);
        
        return messages;
    }
    
    /**
     * Получить текущую категорию пользователя
     * @param chatId ID чата
     * @return текущая категория
     */
    public String getCurrentCategory(Long chatId) {
        return currentCategory.get(chatId);
    }
    
    /**
     * Получить текущую страницу пользователя
     * @param chatId ID чата
     * @return текущая страница
     */
    public Integer getCurrentPage(Long chatId) {
        return currentPage.get(chatId);
    }
    
    /**
     * Установить текущую страницу пользователя
     * @param chatId ID чата
     * @param page номер страницы
     */
    public void setCurrentPage(Long chatId, int page) {
        currentPage.put(chatId, page);
    }
    
    /**
     * Отправить сообщение с товаром
     * @param chatId ID чата
     * @param product товар
     * @return сообщение с товаром
     */
    private SendPhoto sendProductMessage(Long chatId, Product product) {
        SendPhoto photoMessage = new SendPhoto();
        photoMessage.setChatId(chatId);
        
        // Формируем текст сообщения
        String caption = String.format(
                "Название: %s\nЦена: %.2f $\nАртикул: %d\nВ наличии: %d",
                product.getName(),
                product.getPrice(),
                product.getId(),
                product.getStock()
        );
        photoMessage.setCaption(caption);
        
        // Устанавливаем изображение
        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            InputFile photo = new InputFile(product.getImageUrl());
            photoMessage.setPhoto(photo);
        } else {
            // Если у товара нет изображения, используем заглушку
            InputFile photo = new InputFile("https://via.placeholder.com/400x300?text=No+Image");
            photoMessage.setPhoto(photo);
        }
        
        // Добавляем кнопки действий
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> productRows = new ArrayList<>();
        
        // Кнопка "Купить" - для прямого оформления заказа
        List<InlineKeyboardButton> buyRow = new ArrayList<>();
        InlineKeyboardButton buyButton = new InlineKeyboardButton();
        buyButton.setText("Купить");
        buyButton.setCallbackData("direct_buy:" + product.getId());
        buyRow.add(buyButton);
        productRows.add(buyRow);
        
        // Кнопка "В корзину" - для добавления в корзину
        List<InlineKeyboardButton> cartRow = new ArrayList<>();
        InlineKeyboardButton cartButton = new InlineKeyboardButton();
        cartButton.setText("В корзину");
        cartButton.setCallbackData("add_to_cart:" + product.getId());
        cartRow.add(cartButton);
        productRows.add(cartRow);
        
        markupInline.setKeyboard(productRows);
        photoMessage.setReplyMarkup(markupInline);
        
        return photoMessage;
    }
} 