package com.cruiser.utils;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggingSetup {
    
    static {
        // Подавляем все SLF4J сообщения при загрузке класса
        try {
            // Устанавливаем системные свойства до инициализации SLF4J
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "ERROR");
            System.setProperty("slf4j.internal.verbosity", "WARN");
            
            // Отключаем вывод в stderr для SLF4J
            Class<?> loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory");
            
            // Принудительно инициализируем SLF4J с NOP реализацией
            loggerFactoryClass.getMethod("getLogger", String.class)
                .invoke(null, "init");
                
        } catch (Exception ignored) {
            // Игнорируем ошибки - SLF4J может быть не загружен
        }
    }
    
    public static void setupLogging(Logger pluginLogger) {
        // Перенаправляем Java util logging на Bukkit logger
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.WARNING);
        
        // Отключаем логирование HikariCP
        Logger hikariLogger = Logger.getLogger("com.zaxxer.hikari");
        hikariLogger.setLevel(Level.WARNING);
        
        // Перенаправляем критические ошибки на плагин логгер
        hikariLogger.setParent(pluginLogger);
    }
}