package uz.uportal.telegramshop.service.bot.core;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Интерфейс для отправки сообщений в Telegram
 */
public interface MessageSender {
    
    /**
     * Отправляет сообщение с фотографией
     * 
     * @param sendPhoto объект с данными для отправки фото
     * @return отправленное сообщение
     * @throws TelegramApiException если произошла ошибка при отправке
     */
    Message executePhoto(SendPhoto sendPhoto) throws TelegramApiException;
    
    /**
     * Отправляет текстовое сообщение
     * 
     * @param sendMessage объект с данными для отправки сообщения
     * @return отправленное сообщение
     * @throws TelegramApiException если произошла ошибка при отправке
     */
    Message executeMessage(SendMessage sendMessage) throws TelegramApiException;
    
    /**
     * Редактирует существующее сообщение
     * 
     * @param editMessageText объект с данными для редактирования сообщения
     * @return результат операции
     * @throws TelegramApiException если произошла ошибка при редактировании
     */
    Object executeEditMessage(EditMessageText editMessageText) throws TelegramApiException;
    
    /**
     * Удаляет сообщение
     * 
     * @param deleteMessage объект с данными для удаления сообщения
     * @return результат операции
     * @throws TelegramApiException если произошла ошибка при удалении
     */
    Boolean executeDeleteMessage(DeleteMessage deleteMessage) throws TelegramApiException;
} 