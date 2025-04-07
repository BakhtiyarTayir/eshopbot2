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
import uz.uportal.telegramshop.service.bot.core.StateHandler;

/**
 * Обработчик для управления пользователями
 */
@Component
public class UserManagementHandler implements StateHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(UserManagementHandler.class);
    
    private final TelegramUserRepository telegramUserRepository;
    
    public UserManagementHandler(TelegramUserRepository telegramUserRepository) {
        this.telegramUserRepository = telegramUserRepository;
    }
    
    @Override
    public boolean canHandleState(Update update, String state) {
        return state != null && (
                state.equals("CHANGING_USER_ROLE") ||
                state.equals("ADDING_MANAGER"));
    }
    
    @Override
    public boolean canHandle(Update update) {
        // This handler only handles state-based updates
        return false;
    }
    
    @Override
    public BotApiMethod<?> handle(Update update) {
        // This handler only handles state-based updates
        return null;
    }
    
    @Override
    public BotApiMethod<?> handleState(Update update, String state) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return null;
        }
        
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String text = message.getText();
        
        // Получаем текущего пользователя
        TelegramUser currentUser = telegramUserRepository.findById(chatId).orElse(null);
        if (currentUser == null) {
            return createTextMessage(chatId, "Пользователь не найден. Пожалуйста, перезапустите бота командой /start");
        }
        
        logger.info("Handling user management for chatId: {} in state: {}", chatId, state);
        
        switch (state) {
            case "CHANGING_USER_ROLE":
                return handleChangingUserRole(chatId, text, currentUser);
            case "ADDING_MANAGER":
                return handleAddingManager(chatId, text, currentUser);
            default:
                return null;
        }
    }
    
    private BotApiMethod<?> handleChangingUserRole(Long chatId, String text, TelegramUser currentUser) {
        // Проверяем права доступа
        if (!"ADMIN".equals(currentUser.getRole())) {
            currentUser.setState(null);
            telegramUserRepository.save(currentUser);
            return createTextMessage(chatId, "У вас нет прав на изменение ролей пользователей.");
        }
        
        try {
            // Ожидаемый формат: chatId|role
            String[] parts = text.split("\\|");
            if (parts.length != 2) {
                return createTextMessage(chatId, "Неверный формат данных. Ожидается: chatId|role");
            }
            
            Long targetChatId = Long.parseLong(parts[0]);
            String newRole = parts[1].toUpperCase();
            
            if (!newRole.equals("ADMIN") && !newRole.equals("MANAGER") && !newRole.equals("USER")) {
                return createTextMessage(chatId, "Недопустимая роль. Допустимые значения: ADMIN, MANAGER, USER");
            }
            
            // Получаем пользователя
            TelegramUser targetUser = telegramUserRepository.findById(targetChatId).orElse(null);
            if (targetUser == null) {
                return createTextMessage(chatId, "Пользователь с ID " + targetChatId + " не найден.");
            }
            
            // Изменяем роль
            String oldRole = targetUser.getRole();
            targetUser.setRole(newRole);
            telegramUserRepository.save(targetUser);
            
            // Сбрасываем состояние текущего пользователя
            currentUser.setState(null);
            telegramUserRepository.save(currentUser);
            
            return createTextMessage(chatId, "Роль пользователя " + targetUser.getFirstName() + " " + 
                (targetUser.getLastName() != null ? targetUser.getLastName() : "") + 
                " изменена с " + oldRole + " на " + newRole);
        } catch (NumberFormatException e) {
            return createTextMessage(chatId, "Ошибка при парсинге ID пользователя. Убедитесь, что ID - это число.");
        } catch (Exception e) {
            logger.error("Error changing user role: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при изменении роли пользователя: " + e.getMessage());
        }
    }
    
    private BotApiMethod<?> handleAddingManager(Long chatId, String text, TelegramUser currentUser) {
        // Проверяем права доступа
        if (!"ADMIN".equals(currentUser.getRole())) {
            currentUser.setState(null);
            telegramUserRepository.save(currentUser);
            return createTextMessage(chatId, "У вас нет прав на добавление менеджеров.");
        }
        
        try {
            // Ожидаемый формат: chatId|firstName|lastName
            String[] parts = text.split("\\|");
            if (parts.length < 1 || parts.length > 3) {
                return createTextMessage(chatId, "Неверный формат данных. Ожидается: chatId|firstName|lastName (lastName - опционально)");
            }
            
            Long targetChatId = Long.parseLong(parts[0]);
            
            // Проверяем, существует ли уже пользователь с таким chatId
            TelegramUser existingUser = telegramUserRepository.findById(targetChatId).orElse(null);
            
            if (existingUser != null) {
                // Если пользователь существует, меняем ему роль на MANAGER
                existingUser.setRole("MANAGER");
                telegramUserRepository.save(existingUser);
                
                // Сбрасываем состояние текущего пользователя
                currentUser.setState(null);
                telegramUserRepository.save(currentUser);
                
                return createTextMessage(chatId, "Пользователь " + existingUser.getFirstName() + " " + 
                    (existingUser.getLastName() != null ? existingUser.getLastName() : "") + 
                    " теперь имеет роль MANAGER.");
            } else {
                // Создаем нового пользователя с ролью MANAGER
                String firstName = parts.length > 1 ? parts[1] : "Manager";
                String lastName = parts.length > 2 ? parts[2] : null;
                
                TelegramUser newManager = new TelegramUser();
                newManager.setChatId(targetChatId);
                newManager.setFirstName(firstName);
                newManager.setLastName(lastName);
                newManager.setRole("MANAGER");
                newManager.setState("NEW");
                newManager.setRegisteredAt(java.time.LocalDateTime.now());
                
                telegramUserRepository.save(newManager);
                
                // Сбрасываем состояние текущего пользователя
                currentUser.setState(null);
                telegramUserRepository.save(currentUser);
                
                return createTextMessage(chatId, "Создан новый менеджер с ID " + targetChatId + 
                    ", имя: " + firstName + (lastName != null ? " " + lastName : ""));
            }
        } catch (NumberFormatException e) {
            return createTextMessage(chatId, "Ошибка при парсинге ID пользователя. Убедитесь, что ID - это число.");
        } catch (Exception e) {
            logger.error("Error adding manager: {}", e.getMessage(), e);
            return createTextMessage(chatId, "Произошла ошибка при добавлении менеджера: " + e.getMessage());
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
        return message;
    }
} 