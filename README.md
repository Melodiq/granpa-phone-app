# Помощник Алёша

[English version below](#alesha-voice-assistant)

Android-приложение для почти незрячего пожилого человека. Всё голосом, распознавание речи работает **без интернета**.

## Что умеет

| Команда | Что происходит |
|---|---|
| нажать боковую кнопку (экран включился) | «Здравствуйте. Слушаю.» + сигнал, можно говорить |
| касание в любом месте экрана или виджета | сигнал, можно говорить |
| «позвони дочери», «дочке», «набери Андрея» | «Позвонить: Дочь? Скажите да или нет» → «да» → «Звоню: Дочь» |
| «время», «который час» | «Сейчас пятнадцать часов двадцать минут» |
| «какое сегодня число», «какой день недели», «дата» | «Сегодня воскресенье, первое ноября» |
| «погода» | погода сейчас и через 1–2 часа (нужен интернет) |
| «запиши» | «Что записать?» → «Когда?» → «Записываю… Верно?» |
| «что у меня сегодня / завтра», «мои записи» | читает записи |
| «удали запись» | по очереди предлагает записи на удаление |
| «повтори» | повторяет последний ответ |
| «помощь» | перечисляет команды |
| «отбой» | помощник сворачивается и отдыхает: не здоровается при включении экрана |
| «Алёша», «Алёша, привет» | зовёт помощника, даже при выключенном экране: «Да, слушаю» |

- **Входящий звонок:** экран «Звонит: Дочь», вслух имя и подсказка. Ответить — «ответить», «подними трубку», «возьми трубку», «алло» или касание экрана. Сбросить — «сбросить», «не отвечать», «положи трубку». Пока помощник слушает, мелодия звонка тихая, потом громкость возвращается.
- **Напоминания:** за сутки, за 6 часов, за 3 часа и за час. Повторяются каждые 5 минут, пока экран не включат.
- **Обратная связь:** после каждой команды звук и вибрация, на экране цветной круг: жёлтый дышит — говорите, зелёный — услышал, луна — отдыхает.
- **Виджет:** большая кнопка на пол-экрана или весь экран (часы, дата, «Алёша»). Нажатие — помощник сразу слушает.
- **Голос:** спокойный, мужской по умолчанию; любой скачанный русский голос можно выбрать в настройке.
- **Погода:** из Яндекс.Погоды, если в настройке введён ключ API; иначе Open-Meteo, запасной — met.no.
- **Язык интерфейса:** русский, английский или китайский (в настройке). Речь и голосовые команды — на русском.
- Регистр и падежи не важны: «Дочь», «дочь», «дочери», «дочке» — один контакт.

## Установка

1. На телефоне откройте последнюю версию: **Releases → granpa-phone.apk** (справа на странице репозитория).
2. Скачайте и установите APK (разрешите установку из этого источника).
3. Откройте «Помощник Алёша» → откроется экран настройки. Нажмите все жёлтые плашки, пока они не станут зелёными.
4. Экран настройки потом открывается **удержанием пальца на экране 5 секунд**.
5. Виджет: долгое нажатие на рабочем столе → Виджеты → «Алёша — большая кнопка» → растянуть.

### Обязательно на Tecno (HiOS)

Иначе система выключит помощника:
- Настройки приложения → **Автозапуск** → включить.
- Батарея → **Без ограничений**.
- В списке недавних приложений закрепить помощника (значок замка).

### Xiaomi / Poco / Redmi (HyperOS, MIUI)

В настройке помощника есть раздел Xiaomi: включите «Всплывающие окна, когда запущено в фоне», «Экран блокировки» и «Автозапуск», а в батарее — «Нет ограничений». Без этого «Алёша» при выключенном экране срабатывает через раз. Счётчик «услышано / открылось» в настройке показывает, где именно теряется команда.

### Советы

- Подписывайте контакты коротко: «Дочь», «Сын Андрей», «Соседка Валя». В настройке видно, какие контакты распознаются.
- Отключите блокировку экрана (Безопасность → Блокировка экрана → Нет).
- Слово «Алёша» помощник ждёт постоянно, это расходует батарею. В настройке можно оставить только режим «в отбое».
- После перезагрузки телефона Android не даёт слушать микрофон из фона, пока приложение не откроют один раз.

## Сборка

Собирается автоматически GitHub Actions при каждом изменении в `main`, готовый APK выкладывается в Releases. Модель распознавания Vosk (`vosk-model-small-ru-0.22`) скачивается во время сборки.

---

# Alesha voice assistant

An Android app for an elderly person who is almost blind. Everything works by voice, and speech recognition runs **fully offline**. The interface and speech are in Russian.

## Features

| Say / do | What happens |
|---|---|
| press the side button (screen turns on) | greeting + beep, then it listens |
| tap anywhere on the screen or the widget | beep, then it listens |
| "позвони дочери" (call my daughter) | asks to confirm the contact, then dials |
| "время" (time) | says the current time |
| "какое сегодня число", "какой день недели" (date, weekday) | says the weekday and date |
| "погода" (weather) | weather now and for the next 1–2 hours (needs internet) |
| "запиши" (take a note) | asks what and when, confirms, then reminds |
| "что у меня сегодня / завтра" | reads today's or tomorrow's notes |
| "удали запись" | offers notes one by one for deletion |
| "повтори" | repeats the last answer |
| "помощь" | lists the commands |
| "отбой" (rest) | minimizes and stops greeting on screen-on |
| "Алёша" | wake word: calls the assistant, even with the screen off |

- **Incoming calls:** a full-screen card announces the caller by name. Say "ответить" / "подними трубку" or tap the screen to answer; say "сбросить" to decline. The ringtone is lowered while the assistant listens and restored afterwards.
- **Reminders:** 24, 6, 3 and 1 hour before an event, repeated every 5 minutes until the screen is turned on.
- **Feedback:** a sound and vibration after every command, plus a large colored circle: breathing amber means "speak", green means "heard", a moon means resting.
- **Widget:** a half-screen or full-screen button with a clock and date; tapping it starts listening right away.
- **Voice:** a calm male voice by default; any installed offline Russian voice can be picked in setup.
- **Weather:** Yandex Weather when an API key is entered in setup, otherwise Open-Meteo with met.no as a backup.
- **Interface language:** Russian, English or Chinese (in setup). Speech and voice commands stay in Russian.
- Contact matching ignores letter case and Russian word endings, so "Дочь", "дочери" and "дочке" all match the same contact.

## Installation

1. On the phone, open **Releases → granpa-phone.apk** and install it (allow installs from this source).
2. Open "Помощник Алёша". The setup screen appears; tap every amber tile until it turns green.
3. To reopen setup later, **hold a finger on the main screen for 5 seconds**.
4. Widget: long-press the home screen → Widgets → "Алёша — большая кнопка", then resize it.

On Xiaomi / Poco / Redmi (HyperOS, MIUI), use the Xiaomi section in setup to allow pop-up windows in the background, showing on the lock screen and autostart, and set battery to No restrictions; otherwise the wake word works unreliably with the screen off. A heard/opened counter in setup shows where it fails.

On Tecno / Infinix (HiOS), also enable **Autostart**, set battery to **No restrictions**, and lock the app in the recent-apps list, or the system will stop it.

## Tech

Kotlin, no UI framework (plain Android views). Offline speech recognition with [Vosk](https://alphacephei.com/vosk/) (`vosk-model-small-ru-0.22`) using a restricted grammar of commands and contact names for accuracy. Text-to-speech uses the system TTS engine. GitHub Actions builds the APK on every push to `main` and publishes it to Releases.
