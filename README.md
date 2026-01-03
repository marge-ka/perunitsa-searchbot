# ПОИСКОВАЯ СИСТЕМА на Spring Boot

## ОПИСАНИЕ ПРОЕКТА

Полнофункциональный поисковый движок, реализованный на Spring Boot, позволяющий индексировать веб-сайты, выполнять
полнотекстовый поиск по проиндексированным страницам и управлять процессом индексации через REST API.

## ОСНОВНЫЕ ВОЗМОЖНОСТИ

*   Индексация сайтов с возможностью запуска и остановки процесса
*   Полнотекстовый поиск по содержимому проиндексированных страниц
*   Детальная статистика индексации для каждого сайта
*   Управление контентом с возможностью добавления и обновления отдельных страниц
*   Постраничная навигация в результатах поиска
*   Многопоточный обход сайтов
*   Морфологический анализ текста

## ТЕХНОЛОГИИ И ЗАВИСИМОСТИ

*   Язык: Java 17
*   Фреймворк: Spring Boot 2.7.1
*   База данных: PostgreSQL/MySQL
*   Парсинг HTML: Jsoup
*   Морфология: Lucene (Russian morphology)
*   Сборка: Maven
*   ORM: Spring Data JPA + Hibernate

## ТРЕБОВАНИЯ К ОКРУЖЕНИЮ

*   JDK 17 или выше
*   Maven для сборки проекта
*   Доступ к базе данных (PostgreSQL/MySQL)
*   Доступ в интернет для индексации сайтов

## УСТАНОВКА И ЗАПУСК

### 1. Клонирование репозитория
 ```bash
  git clone https://github.com/marge-ka/perunitsa-searchbot.git
  cd perunitsa-searchbot
```

### 2. Настройка базы данных
*    Установите и запустите PostgreSQL или MySQL
*    Создайте базу данных:

MySQL:
 ```sql
  CREATE DATABASE search_engine CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; 
 ```
PostgreSQL:
 ```sql
  CREATE DATABASE search_engine; 
 ```

### 3. Настройте подключение в src/main/resources/application.yaml:
 ```yaml
  spring:
    datasource:
      url: jdbc:mysql://localhost:3306/search_engine?useSSL=false&requireSSL=false&allowPublicKeyRetrieval=true
      username: ваш_логин 
      password: ваш_пароль
      driver-class-name: com.mysql.cj.jdbc.Driver
    jpa:
      hibernate:
        ddl-auto: update  # автоматическое создание таблиц
 ```

### 4. Настройка сайтов для индексации
 Отредактируйте application.yaml, добавив:
 ```yaml
indexing-settings: 
  sites:
    - url: https://example.com
      name: Example Site
 ```

### 5. Сборка и запуск
 ```bash
  mvn clean package
  java -jar target/PerunitsaSearchBot.jar
```

### 6. Проверка работы
Откройте в браузере: http://localhost:8080

##  REST API

###    Управление индексацией
*    GET /api/statistics — получение статистики
*    POST /api/startIndexing — запуск индексации
*    POST /api/stopIndexing — остановка индексации

###    Поиск
   GET /api/search — поиск по содержимому
    Параметры запроса:
       - query — поисковый запрос
       - site — URL сайта для поиска
       - offset — смещение для пагинации
       - limit — количество результатов

###    Управление страницами
   POST /api/indexPage — индексация отдельной страницы
    Параметры:
        - url — URL страницы для индексации

##    Структура проекта
```text
src/
├── main/
│   ├── java/searchengine/
│   │   ├── config/          – загрузка конфигурации сайтов
│   │   ├── controllers/     – REST API
│   │   ├── crawler/         – многопоточный обход сайтов
│   │   ├── dto/             – DTO для ответов
│   │   ├── extractor/       – извлечение текста из HTML
│   │   ├── fetcher/         – HTTP-запросы к сайтам
│   │   ├── lemmatizer/      – приведение слов к нормальной форме
│   │   ├── model/           – JPA-сущности
│   │   ├── repository/      – репозитории Spring Data JPA
│   │   ├── saver/           – сохранение страниц, лемм и индексов
│   │   └── services/        – бизнес-логика
│   └── resources/
│       ├── static.assets
│       ├── templates/       – HTML-шаблоны (index.html)
│       └── application.yaml – конфигурация приложения
└── pom.xml
```

  
## БЕЗОПАСНОСТЬ И ОГРАНИЧЕНИЯ

*  Поддерживается только HTTP/HTTPS
*  Нет внешней аутентификации (подходит для локального или защищённого развёртывания)
*  Индексируются только сайты из конфигурации


##    ЛИЦЕНЗИЯ

   Без лицензии.
