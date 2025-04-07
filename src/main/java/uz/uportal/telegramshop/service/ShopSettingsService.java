package uz.uportal.telegramshop.service;

import org.springframework.stereotype.Service;
import uz.uportal.telegramshop.model.ShopSettings;
import uz.uportal.telegramshop.repository.ShopSettingsRepository;

@Service
public class ShopSettingsService {
    
    private final ShopSettingsRepository shopSettingsRepository;
    
    public ShopSettingsService(ShopSettingsRepository shopSettingsRepository) {
        this.shopSettingsRepository = shopSettingsRepository;
    }
    
    /**
     * Получить настройки магазина
     * @return настройки магазина
     */
    public ShopSettings getShopSettings() {
        ShopSettings settings = shopSettingsRepository.findFirstByOrderById();
        
        // Если настройки не найдены, создаем новые со значениями по умолчанию
        if (settings == null) {
            settings = new ShopSettings(
                "+7 (XXX) XXX-XX-XX",
                "info@example.com",
                "www.example.com",
                "Если у вас возникли вопросы, свяжитесь с нами по телефону",
                "Наш магазин предлагает широкий ассортимент товаров высокого качества.",
                "Пн-Пт: 9:00 - 20:00\nСб-Вс: 10:00 - 18:00"
            );
            shopSettingsRepository.save(settings);
        }
        
        return settings;
    }
    
    /**
     * Обновить настройки магазина
     * @param phone номер телефона
     * @param email email
     * @param website сайт
     * @param supportInfo информация о поддержке
     * @param aboutInfo информация о магазине
     * @param workingHours часы работы
     * @return обновленные настройки
     */
    public ShopSettings updateShopSettings(String phone, String email, String website, 
                                         String supportInfo, String aboutInfo, String workingHours) {
        ShopSettings settings = getShopSettings();
        
        settings.setPhone(phone);
        settings.setEmail(email);
        settings.setWebsite(website);
        settings.setSupportInfo(supportInfo);
        settings.setAboutInfo(aboutInfo);
        settings.setWorkingHours(workingHours);
        
        return shopSettingsRepository.save(settings);
    }
    
    /**
     * Обновить контактную информацию
     * @param phone номер телефона
     * @param email email
     * @param website сайт
     * @return обновленные настройки
     */
    public ShopSettings updateContactInfo(String phone, String email, String website) {
        ShopSettings settings = getShopSettings();
        
        settings.setPhone(phone);
        settings.setEmail(email);
        settings.setWebsite(website);
        
        return shopSettingsRepository.save(settings);
    }
    
    /**
     * Обновить информацию о поддержке
     * @param supportInfo информация о поддержке
     * @return обновленные настройки
     */
    public ShopSettings updateSupportInfo(String supportInfo) {
        ShopSettings settings = getShopSettings();
        settings.setSupportInfo(supportInfo);
        return shopSettingsRepository.save(settings);
    }
    
    /**
     * Обновить информацию о магазине
     * @param aboutInfo информация о магазине
     * @return обновленные настройки
     */
    public ShopSettings updateAboutInfo(String aboutInfo) {
        ShopSettings settings = getShopSettings();
        settings.setAboutInfo(aboutInfo);
        return shopSettingsRepository.save(settings);
    }
    
    /**
     * Обновить часы работы
     * @param workingHours часы работы
     * @return обновленные настройки
     */
    public ShopSettings updateWorkingHours(String workingHours) {
        ShopSettings settings = getShopSettings();
        settings.setWorkingHours(workingHours);
        return shopSettingsRepository.save(settings);
    }
} 