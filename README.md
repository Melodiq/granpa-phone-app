# Помощник Алёша · Alyosha Assistant · 阿廖沙助手

[Русский](#русский) · [English](#english) · [中文](#中文)

Android-приложение для почти незрячего пожилого человека: всё голосом, распознавание речи работает без интернета.
An Android voice assistant for an elderly person who is almost blind; speech recognition works offline.
为几乎失明的老人设计的安卓语音助手，语音识别可离线使用。

**Скачать / Download / 下载:** [Releases → granpa-phone.apk](https://github.com/Melodiq/granpa-phone-app/releases/latest)

---

## Русский

### Как пользоваться

- **Нажать боковую кнопку** (экран включился) → «Здравствуйте. Слушаю.» и сигнал, можно говорить.
- **Коснуться экрана** в любом месте или **нажать виджет** → сигнал, можно говорить.
- **Сказать «Алёша»** (или «Алексей») → «Чего изволите, Иван Петрович?», даже при выключенном экране. Имя задаётся в настройке.
- **Пять быстрых касаний по кругу** → отбой на час.
- После каждой команды звук и вибрация: команда услышана.
- Помощник реагирует только на свой язык и только на уверенно сказанные слова: видео, телевизор и чужая речь его не будят.

### Команды

| Сказать | Что произойдёт |
|---|---|
| «позвони дочери», «дочке», «набери Андрея» | «Позвонить: Дочь? Скажите да или нет» → «да» → звонок |
| «время», «который час» | «Сейчас пятнадцать часов двадцать минут» |
| «какое сегодня число», «какой день недели» | «Сегодня воскресенье, первое ноября» |
| «погода» | погода сейчас и через 1–2 часа |
| «погода на завтра», «погода послезавтра» | прогноз на день: температура, осадки, сильный ветер |
| «запиши» | «Что записать?» → «Когда?» → «Записываю… Верно?» |
| «что у меня сегодня», «что у меня завтра», «мои записи» | читает записи |
| «прочитай экран», «что на экране» | читает вслух весь текст на экране, края экрана светятся |
| «удали запись» | по очереди предлагает записи на удаление |
| «повтори» | повторяет последний ответ |
| «помощь» | перечисляет команды |
| «отбой» | помощник сворачивается и не здоровается при включении экрана |
| «отбой на полчаса», «отбой на час» | то же, но через 30 минут или час помощник сам возвращается |
| «Алёша», «Алёша, привет» | зовёт помощника |

### Чтение экрана

«Прочитай экран» читает вслух весь текст текущего экрана: сообщения, письма, любое приложение. Пока идёт чтение, края экрана светятся. Остановить: коснуться экрана, кнопка громкости или «Алёша, стоп».
**«Отбой» во время звонка** сбрасывает его (в том числе пока номер ещё набирается).
Работает и без открытия помощника: «Алёша, прочитай экран». Включается в настройке, пункт 9 (Специальные возможности).

### Входящие звонки

Экран «Звонит: Дочь», имя вслух и подсказка.
- **Ответить:** «ответить», «подними трубку», «возьми трубку», «алло» или касание экрана.
- **Сбросить:** «сбросить», «не отвечать», «положи трубку».

Пока помощник слушает, мелодия звонка тихая, потом громкость возвращается.

### Напоминания

За сутки, за 6 часов, за 3 часа и за час до события. Каждое повторяется каждые 5 минут, пока экран не включат.

### Экран

Крупные часы и дата, в центре цветной круг состояния:
- жёлтый дышит — говорите;
- зелёный — услышал;
- светлый — помощник говорит;
- луна — отдыхает.

**Виджет:** большая кнопка на пол-экрана или весь экран (часы, дата, «Алёша»). Нажатие — помощник сразу слушает.

### Язык

Русский, английский или китайский, выбирается в настройке. Меняется всё: надписи, голос помощника и голосовые команды.

### Установка

1. Скачайте и установите `granpa-phone.apk` (разрешите установку из этого источника).
2. Откройте «Помощник Алёша» → экран настройки. Нажмите все жёлтые плашки, пока они не станут зелёными.
3. В разделе «Голос помощника» выберите спокойный голос.
4. Настройка потом открывается **удержанием пальца на экране 5 секунд**.
5. Виджет: долгое нажатие на рабочем столе → Виджеты → «Алёша — большая кнопка» → растянуть.

**Tecno / Infinix:** включите автозапуск, батарея «Без ограничений», закрепите приложение в недавних (замок).

**Xiaomi / Poco / Redmi:** в разделе Xiaomi настройки включите «Всплывающие окна в фоне», «Экран блокировки» и «Автозапуск», батарея «Нет ограничений». Счётчик «услышано / открылось» показывает, где теряется «Алёша».

**Советы:**
- Подписывайте контакты коротко: «Дочь», «Сын Андрей». В настройке видно, какие распознаются.
- Отключите блокировку экрана.
- «Алёша» постоянно слушается и расходует батарею. Можно оставить только «в отбое».
- Если «Алёшу» трудно позвать или он срабатывает от телевизора, поменяйте чувствительность в настройке.
- Погода: Open-Meteo, при сбое — MET Norway. Нужны интернет и геолокация.

---

## English

### How to use

- **Press the side button** (the screen turns on) → a greeting and a beep, then speak.
- **Tap anywhere** on the screen or **tap the widget** → a beep, then speak.
- **Say "Alyosha"** or **"Alex"** → "What can I do for you, John?", even with the screen off. The name is set in setup.
- **Tap the circle five times quickly** → rest for an hour.
- Every command is confirmed with a sound and a vibration.
- The assistant reacts only to its own language and to clearly spoken words, so videos, TV and other speech don't wake it.

### Commands (English mode)

| Say | What happens |
|---|---|
| "call my daughter", "call Andrew" | "Call Daughter? Say yes or no" → "yes" → dials |
| "time", "what time is it" | "It's 3:20 PM." |
| "what's the date", "what day is it" | "Today is Sunday, November 1." |
| "weather" | weather now and in the next 1–2 hours |
| "weather tomorrow", "weather the day after tomorrow" | the day's forecast: temperature range, precipitation, strong wind |
| "take a note" | "What should I write down?" → "When?" → "Is that right?" |
| "what do I have today", "what do I have tomorrow", "my notes" | reads your notes |
| "read the screen", "what's on the screen" | reads all on-screen text aloud while the screen edges glow |
| "delete a note" | offers notes one by one for deletion |
| "repeat" | repeats the last answer |
| "help" | lists the commands |
| "rest" | minimizes; no greeting on screen-on |
| "rest for half an hour", "rest for an hour" | the same, and the assistant returns by itself after 30 or 60 minutes |
| "Alyosha", "Alex" | wakes the assistant |

### Screen reading

"Read the screen" reads all text on the current screen aloud: messages, emails, any app. The screen edges glow while it reads. To stop: tap the screen, press a volume button or say "Alyosha, stop".
**Saying "rest" during a call** hangs it up (including while it's still dialing).
It also works without opening the assistant: "Alyosha, read the screen". Turn it on in setup, step 9 (Accessibility).

### Incoming calls

A full-screen card announces the caller by name.
- **Answer:** say "answer" or "pick up", or tap the screen.
- **Decline:** say "decline" or "hang up".

The ringtone is lowered while the assistant listens and restored afterwards.

### Reminders

24, 6, 3 and 1 hour before an event, each repeated every 5 minutes until the screen is turned on.

### Screen

A large clock and date, plus a big colored circle:
- breathing amber — speak now;
- green — heard you;
- light — the assistant is speaking;
- a moon — resting.

**Widget:** a half-screen or full-screen button with the clock and date; tapping it starts listening right away.

### Language

Russian, English or Chinese, chosen in setup. Everything follows the choice: on-screen text, the assistant's voice and voice commands.

### Installation

1. Download and install `granpa-phone.apk` (allow installs from this source).
2. Open the app; the setup screen appears. Tap every amber tile until it turns green.
3. Under "Assistant voice", pick a calm voice.
4. To reopen setup later, **hold a finger on the main screen for 5 seconds**.
5. Widget: long-press the home screen → Widgets → "Alyosha — big button", then resize it.

**Tecno / Infinix:** turn on Autostart, set battery to No restrictions, and lock the app in recent apps.

**Xiaomi / Poco / Redmi:** in the Xiaomi section of setup, allow pop-up windows in the background, showing on the lock screen and autostart, and set battery to No restrictions. The heard/opened counter shows where the wake word gets lost.

**Tips:**
- Keep contact names short. Setup shows which ones are recognized.
- Turn off the screen lock.
- Always listening for the wake word uses battery; you can limit it to rest mode.
- If the wake word is hard to trigger or reacts to TV, change the sensitivity in setup.
- Weather comes from Open-Meteo, with MET Norway as a fallback; it needs internet and location.

---

## 中文

### 使用方法

- **按侧键**（屏幕亮起）→ 问候和提示音，然后说话。
- **点击屏幕**任意位置或**点击小部件** → 提示音，然后说话。
- **说「阿廖沙」**或**「小助手」** → 「王爷爷，有什么吩咐？」，熄屏时也可以。称呼在设置中填写。
- **快速点击圆圈五次** → 休息一小时。
- 每条命令都有声音和振动确认。
- 助手只响应所选语言中清晰说出的词，视频、电视和他人说话不会唤醒它。

### 命令（中文模式）

| 说 | 结果 |
|---|---|
| 「给女儿打电话」 | 「给女儿打电话吗？请说是或者不是」→「是」→ 拨号 |
| 「几点了」 | 「现在是15点20分。」 |
| 「今天几号」「星期几」 | 「今天是11月1日，星期日。」 |
| 「天气」 | 现在及未来1–2小时的天气 |
| 「明天天气」「后天天气」 | 当天预报：气温范围、降水、大风 |
| 「记一下」 | 「要记什么？」→「什么时候？」→「对吗？」 |
| 「今天有什么安排」「明天有什么安排」「我的记录」 | 朗读记录 |
| 「读屏幕」「屏幕上有什么」 | 朗读屏幕上的全部文字，屏幕边缘发光 |
| 「删除记录」 | 逐条询问是否删除 |
| 「重复」 | 重复上一个回答 |
| 「帮助」 | 列出命令 |
| 「休息」 | 最小化，亮屏时不再问候 |
| 「休息半小时」「休息一小时」 | 同上，30 分钟或 1 小时后自动恢复 |
| 「阿廖沙」「小助手」 | 唤醒助手 |

### 读屏

「读屏幕」会朗读当前屏幕上的全部文字：消息、邮件或任何应用。朗读时屏幕边缘发光。停止：点击屏幕、按音量键或说「阿廖沙，停」。
**通话时说「休息」**会挂断电话（包括正在拨号时）。
不打开助手也可以：说「阿廖沙，读屏幕」。在设置第 9 项（无障碍）中开启。

### 来电

全屏显示来电人姓名并朗读。
- **接听：** 说「接听」或点击屏幕。
- **挂断：** 说「挂断」或「不接」。

助手聆听时铃声会调低，之后恢复。

### 提醒

在事件前24小时、6小时、3小时和1小时提醒，每次提醒每5分钟重复一次，直到点亮屏幕。

### 屏幕

大号时钟和日期，中间是彩色状态圆圈：
- 黄色呼吸 — 请说话；
- 绿色 — 已听到；
- 浅色 — 助手正在说话；
- 月亮 — 休息中。

**小部件：** 半屏或全屏的大按钮，显示时钟和日期，点击后助手立即开始聆听。

### 语言

在设置中选择俄语、英语或中文。屏幕文字、助手语音和语音命令全部随之切换。

### 安装

1. 下载并安装 `granpa-phone.apk`（允许从此来源安装）。
2. 打开应用，进入设置界面。点击每个黄色方块，直到全部变绿。
3. 在「助手语音」中选择平静的声音。
4. 之后在主屏幕上**按住5秒**即可再次打开设置。
5. 小部件：长按桌面 → 小部件 →「阿廖沙 — 大按钮」，然后调整大小。

**Tecno / Infinix：** 开启自启动，电池设为无限制，并在最近任务中锁定应用。

**小米 / Poco / 红米：** 在设置的小米部分开启「后台弹出界面」「锁屏显示」和「自启动」，电池设为无限制。「听到 / 打开」计数可显示唤醒词在哪一步丢失。

**提示：**
- 联系人名称越短越好。设置中可以看到哪些能被识别。
- 关闭锁屏。
- 始终监听唤醒词会耗电，可以改为仅在休息时监听。
- 如果难以唤醒或被电视触发，请在设置中调整灵敏度。
- 天气来自 Open-Meteo，失败时使用 MET Norway，需要网络和定位。

---

## Tech

Kotlin with plain Android views, no UI framework. Offline speech recognition uses [Vosk](https://alphacephei.com/vosk/) with small Russian, English and Chinese models; Russian and English listen against a restricted grammar of commands and contact names for accuracy, Chinese uses free recognition with substring matching. Speech output uses the system text-to-speech engine. Screen reading is an accessibility service that collects text from the active window and speaks it with a glowing edge overlay. Weather comes from [Open-Meteo](https://open-meteo.com/) with [MET Norway](https://api.met.no/) as a fallback. GitHub Actions builds the APK on every push to `main` and publishes it to Releases.
