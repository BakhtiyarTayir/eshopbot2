#!/bin/bash

# Конфигурация
TELEGRAM_BOT_TOKEN="5389254369:AAHI4CyGTNebKMpLisEzHZo9FFr-RHu2qCQ"  # Замените на токен нового бота
TELEGRAM_CHAT_ID="563245011"  # Ваш chat_id
CONTAINERS=("mysql-telegramshop" "phpmyadmin-telegramshop" "app-telegramshop")
LOG_FILE="/var/log/container-monitor.log"

# Функция для логирования
log_message() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" >> "$LOG_FILE"
}

# Функция для отправки сообщения через Telegram API
send_telegram_message() {
    local message="$1"
    log_message "Отправка сообщения: $message"
    response=$(curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
        -d chat_id="${TELEGRAM_CHAT_ID}" \
        -d text="${message}" \
        -d parse_mode="HTML")
    log_message "Ответ от Telegram API: $response"
}

# Проверка состояния контейнеров
check_containers() {
    for container in "${CONTAINERS[@]}"; do
        log_message "Проверка контейнера: $container"
        local status=$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null)
        log_message "Статус контейнера $container: $status"
        
        if [ "$status" != "running" ]; then
            local message="⚠️ <b>Внимание!</b>\nКонтейнер <code>${container}</code> остановлен или перезапускается.\nСтатус: ${status}\nВремя: $(date '+%Y-%m-%d %H:%M:%S')"
            send_telegram_message "$message"
        fi
    done
}

# Создаем файл лога, если его нет
touch "$LOG_FILE"
chmod 666 "$LOG_FILE"

log_message "Скрипт мониторинга запущен"

# Основной цикл
while true; do
    check_containers
    sleep 60  # Проверка каждую минуту
done 