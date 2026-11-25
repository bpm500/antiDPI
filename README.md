<div align="center">

<img src="icons/icon128.png" alt="antiDPI" width="80" height="80"/>

# antiDPI

<a href="https://www.donationalerts.com/r/bpm500" target="_blank">
  <img src="https://img.shields.io/badge/%D0%9F%D0%BE%D0%B4%D0%B4%D0%B5%D1%80%D0%B6%D0%BA%D0%B0_%D0%B0%D0%B2%D1%82%D0%BE%D1%80%D0%B0-DonationAlerts-FF5900?style=for-the-badge&logo=donationalerts&logoColor=white" alt="Поддержка автора DonationAlerts"/>
</a>

<br/>

**Android DPI Bypass Manager и Telegram WS Proxy — Discord, YouTube, SoundCloud и другие сервисы без root**

[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%E2%80%9334)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/bpm500/antiDPI)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![NDK](https://img.shields.io/badge/NDK-byedpi%20%2B%20hev--socks5--tunnel-000000?style=for-the-badge)](https://github.com/bpm500/antiDPI)
[![Version](https://img.shields.io/badge/Version-1.0-blue?style=for-the-badge)](https://github.com/bpm500/antiDPI/releases)

<br/>

> **antiDPI** — мой форк для Android (мобильная адаптация десктопного проекта [zapret++](https://github.com/bpm500/zapret_pp)).  
> Встроенный **tg-ws-proxy** (вкладка **Utils**) взят из проекта [AndMTProto](https://github.com/oruviyex18/AndMTProto).  
> Минималистичный чёрно-белый интерфейс, автоподбор стратегий, тестирование в параллельных слотах, две плитки в шторке Android и встроенный WebSocket-прокси для Telegram.

</div>

> [!TIP]
> ### 💎 Поддержать автора (Crypto / Tonkeeper)
>
> **TON (TON / GRAM, сеть TON — отправляйте только TON (GRAM) и токены в сети TON):** `UQBbcN8eqZB1XBLauhNA98M8EU7BiS65La36vq4ejj6RYBN9`  
> **TRON (TRX / TRC20, сеть TRON — отправляйте только TRON (TRX) и токены в сети TRON):** `TQnEJt8Djmk8NGnkN5frHdF34Hh3Fz4Tyx`  
> **Ethereum (ETH, сеть Ethereum — отправляйте только ETH и токены в сети Ethereum):** `0x83f4BaEbd284D4d29210Ca5Eb927d68012e8612B`  
> **BSC (BNB, сеть BSC — отправляйте только BSC (BNB) и токены в сети BSC):** `0x83f4BaEbd284D4d29210Ca5Eb927d68012e8612B`

---

## ⚡ Что такое antiDPI?

**antiDPI** — это Android-приложение для обхода DPI **без root-прав**, объединяющее локальный VPN-туннель на базе `byedpi` (`ciadpi`) + `hev-socks5-tunnel` и встроенный `tg-ws-proxy` для Telegram в одном интерфейсе.

**Ключевые возможности:**
- **Подключение в одно нажатие (Connect)** — кнопка питания со статусами (`Отключено` / `Подключение` / `Подключено`) и список конфигов, автоматически отсортированный по рейтингу тестирования (лучшие сверху).
- **Генератор стратегий (Create)** — автоперебор из базы **46 стратегий `ciadpi`** (`--split`, `--disorder`, `--oob`, `--disoob`, `--fake --ttl`, `--tlsrec`, `--mod-http`, `--auto`) с проверкой доступности Discord / YouTube, а также ручной конструктор конфигов с валидацией.
- **Параллельное тестирование (Test)** — проверка всех или выбранного конфига через реальный TCP + TLS + HTTP запрос поверх локального SOCKS5 в изолированных слотах (работает даже при включённом VPN).
- **Встроенный Telegram WebSocket Proxy (Utils)** — мобильный прокси-мост на базе [AndMTProto](https://github.com/oruviyex18/AndMTProto) с быстрым подключением в Telegram (`tg://proxy`), тестированием и просмотром логов.
- **Две плитки быстрых настроек в шторке Android** — независимые переключатели **antiDPI** и **tg-ws-proxy** прямо в панели быстрых настроек телефона.
- **Автопроверка обновлений** — проверка новых релизов в репозитории `bpm500/antiDPI` при запуске и вручную в настройках.

---

## 🏗️ Архитектура (без root)

На Android нет драйвера WinDivert / `winws`, поэтому трафик маршрутизируется через стандартный `VpnService`:

```text
Приложения → Android VpnService (TUN)
           → libhev-socks5-tunnel.so (tun2socks, JNI)
           → 127.0.0.1:<port> (libciadpi.so / byedpi, отдельный процесс)
           → Интернет (в обход TUN через addDisallowedApplication)
```

- **DNS внутри туннеля**: настроен публичный DNS (`1.1.1.1`, `8.8.8.8`), чтобы провайдерская подмена DNS не мешала обходу.
- **QUIC (UDP/443)**: пакеты на UDP-порт 443 сбрасываются внутри `hev-socks5-tunnel`, чтобы браузеры и клиенты переключались на TCP/TLS.
- **Домены обхода**: обход применяется к списку доменов из `app/src/main/assets/list-general.txt`.

---

## 📱 Системные требования

| Параметр | Требование |
|---|---|
| ОС | Android 8.0+ (`minSdk 26`, `targetSdk 34`) |
| Root-права | **Не требуются** |
| Архитектуры (ABI) | `arm64-v8a`, `x86_64` |

---

## 🚀 Быстрый старт (установка APK)

1. Скачайте актуальный `antiDPI.apk` из раздела **[Releases](https://github.com/bpm500/antiDPI/releases)**.
2. Установите APK на устройство (разрешите установку из неизвестных источников для файлового менеджера или браузера).
3. Если **Google Play Protect** покажет предупреждение о подписи разработчика, нажмите **«Подробнее» → «Всё равно установить»**.
4. При первом запуске отключите оптимизацию батареи (**Работа в фоне**), чтобы Android не закрывал VPN и прокси при выключенном экране.
5. При первом включении **antiDPI** подтвердите системный запрос на создание VPN-подключения.

---

## 📋 Разделы и функционал

### 1. Вкладка Connect

| Элемент | Назначение |
|---|---|
| **Кнопка питания** | Запуск и остановка локального VPN-туннеля с выбранным конфигом `ciadpi` |
| **Список конфигов** | Готовые и пользовательские конфиги, отсортированные от лучшего результата теста к худшему (`OK` / задержка). Доступны просмотр аргументов, переименование и удаление |
| **Иконка шестерёнки** | Переход в меню настроек |

---

### 2. Вкладка Create — Подбор и создание конфигов

| Элемент | Назначение |
|---|---|
| **Цели (Discord / YouTube)** | Выбор сервисов для проверки при автоподборе |
| **Количество конфигов** | Сколько рабочих стратегий нужно найти за один прогон |
| **Создать / Стоп** | Автоматический перебор базы из 46 стратегий `ciadpi` и сохранение найденных конфигов во внутреннее хранилище |
| **Ручной конфиг** | Ввод имени и произвольных аргументов `ciadpi` с проверкой синтаксиса и сохранением |

---

### 3. Вкладка Test — Тестирование конфигов

| Элемент | Назначение |
|---|---|
| **Тестировать все / выбранный** | Запускает изолированные процессы `ciadpi` на свободных локальных портах и проверяет реальное TCP + TLS-рукопожатие + HTTP-ответ |
| **Рейтинг** | Итоговый топ конфигов по количеству успешных проверок и минимальной задержке (мс) |
| **Лог теста** | Подробный лог проверок с кнопками копирования и очистки |

---

### 4. Вкладка Utils — Telegram WebSocket Proxy (`tg-ws-proxy`)

Встроенный клиент моста на базе [AndMTProto](https://github.com/oruviyex18/AndMTProto):

| Элемент | Назначение |
|---|---|
| **Старт / Стоп** | Запуск фонового сервиса `tg-ws-proxy` с постоянным уведомлением |
| **Открыть в Telegram** | Быстрое добавление локального прокси в клиент Telegram (`tg://proxy`) |
| **Копировать ссылку** | Копирование ссылки прокси в буфер обмена |
| **Диагностика и логи** | Проверка соединения, фильтрация логов (`All` / `Warnings` / `Errors`) и настройки параметров прокси |

---

### 5. Настройки (Settings)

Открываются по иконке шестерёнки на вкладке **Connect**:
1. **Параллельные слоты теста** — выбор числа одновременно тестируемых конфигов.
2. **Обновления (`check-update`)** — проверка новых релизов на GitHub при каждом запуске и кнопка ручной проверки с диалогом загрузки.
3. **About** — ссылка на репозиторий [github.com/bpm500/antiDPI](https://github.com/bpm500/antiDPI).
4. **Работа в фоне** — проверка и запрос отключения оптимизации батареи Android.
5. **Версия** — текущая версия приложения (`1.0`).

---

### 6. Плитки в шторке уведомлений (Quick Settings)

В панель быстрых настроек Android можно добавить две отдельные плитки:
- **antiDPI** — быстрое включение и выключение VPN-обхода.
- **tg-ws-proxy** — быстрое включение и выключение Telegram WebSocket Proxy.

---

## 🔧 Сборка из исходников (без Android Studio)

Все инструменты (`JDK 17`, `Android SDK 34`, `NDK 26.3.11579264`, `CMake 3.22.1`, `Gradle 8.7`) скачиваются и разворачиваются автоматически локально в папку `./toolchain/` — глобально установленные Android Studio, SDK и NDK не требуются.

### Требования

- Windows 10/11 или Linux (x64)
- **Python 3.13+** в системном `PATH` (требуется плагином Chaquopy для упаковки зависимостей `tg-ws-proxy` в APK)
- Доступ в интернет для первой загрузки toolchain и зависимостей Gradle/Chaquopy

### Сборка на Windows

```bat
git clone https://github.com/bpm500/antiDPI.git
cd antiDPI
scripts\setup.bat
scripts\build.bat
```

### Сборка на Linux

```bash
git clone https://github.com/bpm500/antiDPI.git
cd antiDPI
chmod +x scripts/*.sh
./scripts/setup.sh
./scripts/build.sh
```

Что делают скрипты:
1. `setup.(bat|sh)` скачивает portable `JDK 17`, Android `cmdline-tools`, `platform-tools`, `platforms;android-34`, `build-tools;34.0.0`, `ndk;26.3.11579264`, `cmake;3.22.1` и `Gradle 8.7` в `./toolchain/`.
2. `build.(bat|sh)` при необходимости создаёт локальный keystore (`make_keystore`), компилирует нативные бинари `byedpi` (`libciadpi.so`) и `hev-socks5-tunnel` (`libhev-socks5-tunnel.so`) через `ndk-build`, собирает release APK через `./gradlew assembleRelease`, подписывает его через `apksigner` и сохраняет в **`out/antiDPI.apk`**.

---

## 📁 Структура проекта

```text
antiDPI/
├── app/
│   ├── build.gradle.kts               ← конфигурация Android-модуля и Chaquopy
│   ├── requirements.txt               ← Python-зависимости для tg-ws-proxy
│   └── src/main/
│       ├── AndroidManifest.xml        ← сервисы VPN, tg-ws-proxy и Quick Settings Tiles
│       ├── assets/
│       │   ├── list-general.txt       ← список доменов обхода
│       │   ├── strategies.json        ← база из 46 стратегий ciadpi
│       │   └── cf-worker/             ← скрипт для Cloudflare Worker
│       ├── java/com/antidpi/app/      ← исходники Kotlin / Jetpack Compose
│       ├── python/                    ← Python-модули tg-ws-proxy (из AndMTProto)
│       └── res/                       ← векторные иконки и ресурсы (EN/RU)
├── third_party/
│   ├── byedpi/                        ← полные исходники ciadpi (byedpi) + Android.mk
│   └── hev-socks5-tunnel/             ← полные исходники tun2socks (lwip, yaml, hev-task-system)
├── scripts/                           ← setup, make_keystore, build_native, build (.bat / .sh)
├── gradle/wrapper/                    ← Gradle wrapper
├── icons/                             ← иконки проекта
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── settings.gradle.kts
```

---

## ⚠️ Известные ограничения

- **Нет UDP-десинхронизации**: голосовые каналы Discord (WebRTC/UDP) и UDP-игры через TCP-стратегии `byedpi` могут не работать (для обхода блокировок голоса требуется серверный VPN или root).
- **Отличие флагов от `zapret++` (`winws`)**: параметры `winws` (`seqovl`, `fooling=badseq/ts/md5sig`, `fakedsplit`, `syndata`) не переносятся 1-в-1 на `ciadpi`; вместо них используются механизмы `ciadpi` (`--split`, `--disorder`, `--oob`, `--disoob`, `--fake`, `--ttl`, `--tlsrec`, `--mod-http`, `--auto`).
- **Опция `--md5sig`**: зависит от поддержки `TCP_MD5SIG` в ядре Android конкретного устройства; на некоторых прошивках пакеты с `--md5sig` могут игнорироваться ядром — в этом случае используйте стратегии с `--split`, `--disorder`, `--oob`, `--tlsrec` или `--fake --ttl`.
- **Блокировки по IP / белые списки SNI**: если ресурс жёстко блокируется провайдером по IP-адресу, локальная модификация DPI-пакетов без внешнего прокси/VPN может не помочь.

---

<div align="center">

Форк для Android на базе [zapret++](https://github.com/bpm500/zapret_pp) by [bpm500](https://github.com/bpm500) · `tg-ws-proxy` от [oruviyex18/AndMTProto](https://github.com/oruviyex18/AndMTProto)

**[⭐ Поставьте звезду, если проект оказался полезным!](https://github.com/bpm500/antiDPI)**

</div>
