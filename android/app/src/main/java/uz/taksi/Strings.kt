package uz.taksi

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * Ilova matnlari o'zbek tilida yoziladi. Boshqa tillar shu jadval orqali, ekranga chiqish paytida
 * tarjima qilinadi. Qator: o'zbekcha § ruscha § inglizcha.
 * "~" bilan boshlangan qator — regex (guruhlar \1, \2). "=" bilan boshlangan qator — matn bo'lagi (qismini almashtiradi).
 */
private const val TABLE = """
Yuklanmoqda…§Загрузка…§Loading…
Ustuvorlik va ballar§Приоритет и баллы§Priority and points
ball§баллы§points
Ball qanday ortadi§Как растут баллы§How points increase
Ball qanday kamayadi§Как уменьшаются баллы§How points decrease
Ustuvorlik nimaga ta'sir qiladi§На что влияет приоритет§What priority affects
Ball tarixi§История баллов§Points history
Hali o'zgarish bo'lmagan§Изменений пока нет§No changes yet
Har 4 ta safar§Каждые 4 поездки§Every 4 trips
200 safargacha hisoblanadi (eng ko'pi +50 ball).§Считается до 200 поездок (максимум +50 баллов).§Counted up to 200 trips (max +50 points).
Reyting 4.0 dan yuqori§Рейтинг выше 4.0§Rating above 4.0
Har 0.1 reyting uchun +4 ball.§+4 балла за каждые 0.1 рейтинга.§+4 points for every 0.1 of rating.
Yakunlangan safar§Завершённая поездка§Completed trip
Har bir tugagan safar jarimadan 1 ball qaytaradi.§Каждая завершённая поездка возвращает 1 балл штрафа.§Each completed trip returns 1 penalty point.
Buyurtmani olmaslik§Пропуск заказа§Missing an order
Qabul qilib bekor qilish§Отмена после принятия§Cancel after accepting
Qabul qilingan buyurtmani bekor qilsangiz.§Если вы отменяете принятый заказ.§If you cancel an accepted order.
Buyurtmani bekor qilish (−4 ball)§Отменить заказ (−4 балла)§Cancel order (−4 points)
Buyurtmani bekor qilasizmi?§Отменить заказ?§Cancel the order?
Qabul qilingan buyurtmani bekor qilsangiz, 4 ball ayiriladi va yangi buyurtmalar kechroq ko'rinadi.§Если отмените принятый заказ, спишется 4 балла и новые заказы будут появляться позже.§If you cancel an accepted order, 4 points are deducted and new orders appear to you later.
Ha, bekor qilaman§Да, отменяю§Yes, cancel
🔔 Yangi buyurtma!§🔔 Новый заказ!§🔔 New order!
Rad etish (−7 ball)§Отклонить (−7 баллов)§Decline (−7 points)
3 km ichidagi buyurtmani olmasangiz, 7 ball ayiriladi va yangi buyurtmalar kechroq ko'rinadi.§Если не примете заказ в радиусе 3 км, спишется 7 баллов и новые заказы будут появляться позже.§If you don't take an order within 3 km, 7 points are deducted and new orders appear to you later.
⏱ Vaqt tugadi: buyurtma olinmadi, 7 ball ayiriladi§⏱ Время вышло: заказ не принят, спишется 7 баллов§⏱ Time is up: order not taken, 7 points will be deducted
Pul qabul qilish§Приём оплаты§Receiving payments
💵 Naqd pul§💵 Наличные§💵 Cash
💳 Naqdsiz§💳 Безналичные§💳 Cashless
Naqd jami§Всего наличными§Cash total
Naqdsiz jami§Всего безналичными§Cashless total
Yo'lovchidan naqd olingan pul. Komissiya hisobingizdan yechiladi.§Деньги, полученные наличными от пассажира. Комиссия списывается с вашего счёта.§Cash received from the passenger. The commission is deducted from your balance.
Karta orqali to'langan safarlar. Karta to'lovi to'lov tizimi ulangach to'liq ishlaydi.§Поездки, оплаченные картой. Оплата картой заработает полностью после подключения платёжной системы.§Trips paid by card. Card payment will work fully once a payment system is connected.
Bu turdagi to'lov hali yo'q§Такой оплаты пока нет§No payments of this type yet
To'lov kartalari§Платёжные карты§Payment cards
Hali karta qo'shilmagan§Карты ещё не добавлены§No cards added yet
Asosiy karta§Основная карта§Main card
Asosiy qilish§Сделать основной§Make main
Yangi karta qo'shish§Добавить новую карту§Add a new card
Karta raqami (16 xona)§Номер карты (16 цифр)§Card number (16 digits)
Qo'shish§Добавить§Add
Karta raqami noto'g'ri§Неверный номер карты§Invalid card number
Xavfsizlik: ilova faqat kartaning birinchi 6 va oxirgi 4 raqamini saqlaydi. To'liq raqam, muddat va CVV hech qayerda saqlanmaydi. Karta orqali avtomatik to'lov to'lov tizimi (Click/Payme) ulangach ishga tushadi, hozircha to'lov naqd.§Безопасность: приложение хранит только первые 6 и последние 4 цифры карты. Полный номер, срок и CVV нигде не хранятся. Автоплатёж картой заработает после подключения платёжной системы (Click/Payme), пока оплата наличными.§Security: the app stores only the first 6 and last 4 digits of the card. The full number, expiry and CVV are never stored. Automatic card payment will start once a payment system (Click/Payme) is connected; for now payment is cash.
Xabarlar§Сообщения§Messages
X ofisi§Офис X§X office
Ogohlantirishlar§Предупреждения§Warnings
X yangiliklari§Новости X§X news
Bonuslar§Бонусы§Bonuses
Qo'llab-quvvatlash§Поддержка§Support
Hisob balansidagi o'zgarishlar§Изменения баланса счёта§Account balance changes
Claude yordamchi · 24/7§Помощник Claude · 24/7§Claude assistant · 24/7
Maqsadli bonus va taklif kodi§Целевой бонус и код приглашения§Goal bonus and invite code
Operator bilan yozishma§Переписка с оператором§Chat with an operator
Komissiya, to'ldirish, bonus§Комиссия, пополнение, бонус§Commission, top-ups, bonus
Hozircha xabar yo'q§Пока нет сообщений§No messages yet
Yangi ogohlantirish yo'q§Нет новых предупреждений§No new warnings
Hisobni qanday to'ldiraman?§Как пополнить счёт?§How do I top up my account?
Nega buyurtmalar yo'q?§Почему нет заказов?§Why are there no orders?
Komissiya haqida§О комиссии§About the commission
Yangi haydovchi bonusi§Бонус для нового водителя§New driver bonus
Yozmoqda…§Печатает…§Typing…
Savolingiz§Ваш вопрос§Your question
🎧 Operator bilan bog'lanish§🎧 Связаться с оператором§🎧 Contact an operator
Hisobni to'ldirish§Пополнение счёта§Top up account
To'lov usuli§Способ оплаты§Payment method
Taksi ofislari§Офисы такси§Taxi offices
Boshqa summa (so'm)§Другая сумма (сум)§Other amount (UZS)
So'rov yuborish§Отправить запрос§Send request
So'rov yuborildi§Запрос отправлен§Request sent
Tushunarli§Понятно§Got it
So'rovlarim§Мои запросы§My requests
To'ldirildi§Пополнено§Topped up
To'ldirish§Пополнить§Top up
To'lov usullari hali sozlanmagan. Operatorga murojaat qiling.§Способы оплаты ещё не настроены. Обратитесь к оператору.§Payment methods are not set up yet. Contact an operator.
🤖 Yordamchi bilan suhbat (Claude)§🤖 Чат с помощником (Claude)§🤖 Chat with the assistant (Claude)
Moliya§Финансы§Finance
Yechish§Вывод§Payouts
Sozlama§Настройки§Settings
Admin daromadi (komissiya §Доход админа (комиссия §Admin income (commission 
Haydovchilar daromadi (komissiyasiz)§Доход водителей (без комиссии)§Drivers' income (after commission)
7 kun§7 дней§7 days
Jami§Итого§Total
Oxirgi 7 kun§Последние 7 дней§Last 7 days
Hisob-kitob§Расчёты§Settlements
Naqd pul qabul qilish (ofis)§Приём наличных (офис)§Cash received (office)
Haydovchi telefoni§Телефон водителя§Driver phone
Hisobga qo'shish§Зачислить на счёт§Add to account
To'ldirish so'rovlari§Запросы на пополнение§Top-up requests
Tasdiqlash§Подтвердить§Confirm
Komissiya va qoidalar§Комиссия и правила§Commission and rules
To'lov rekvizitlari§Платёжные реквизиты§Payment details
Haydovchilarga yangilik§Новость для водителей§News for drivers
Sarlavha§Заголовок§Title
Matn§Текст§Text
Bugungi daromad§Доход за сегодня§Today's earnings
Jami daromad§Общий доход§Total earnings
Hozir barcha to'lovlar naqd: pulni yo'lovchidan o'zingiz olasiz.§Сейчас все платежи наличными: вы получаете деньги от пассажира.§All payments are cash for now: you collect the money from the passenger.
Jami sarflangan§Всего потрачено§Total spent
To'lanmagan safar§Неоплаченная поездка§Unpaid trip
to'lash§оплатить§pay
Hozir to'lov naqd: haydovchiga beriladi. Karta to'lovi tez orada.§Сейчас оплата наличными водителю. Оплата картой скоро.§Payment is cash to the driver for now. Card payment is coming soon.
Safarlar tarixi§История поездок§Trip history
Hali safarlar yo'q§Поездок пока нет§No trips yet
Bekor qilingan§Отменена§Cancelled
Telefon ko'rsatilmagan§Телефон не указан§No phone number
🚖 Haydovchi§🚖 Водитель§🚖 Driver
🧍 Yo'lovchi§🧍 Пассажир§🧍 Passenger
So'nggi 30 ta buyurtma bo'yicha§По последним 30 заказам§Based on the last 30 orders
=Versiya: §Версия: §Version:
Yangilanishni tekshirish§Проверить обновления§Check for updates
Tekshirilmoqda…§Проверка…§Checking…
Yangi versiya bor§Есть новая версия§New version available
Yangi versiya topilmadi§Новых версий нет§No new version
Joylashuv ruxsatlari§Разрешения геолокации§Location permissions
Hisobdan chiqish§Выйти из аккаунта§Log out
Savol yoki muammo yozing§Напишите вопрос или проблему§Write a question or problem
Xabaringiz qo'llab-quvvatlash xizmatiga boradi, javob shu yerda chiqadi.§Сообщение уйдёт в службу поддержки, ответ появится здесь.§Your message goes to support; the reply will appear here.
Javob keldi§Ответ получен§Reply received
Javob kutilmoqda§Ожидание ответа§Awaiting reply
=Javob: §Ответ: §Reply:
Ko'p so'raladigan savollar§Частые вопросы§FAQ
SMS kod kelmasa nima qilaman?§Что делать, если не приходит SMS-код?§What if the SMS code doesn't arrive?
Kod kelmasa, 1 daqiqadan keyin qayta so'rang. Server uxlab qolgan bo'lsa, birinchi so'rov 30–60 soniya olishi mumkin.§Если код не пришёл, запросите снова через 1 минуту. Если сервер «спал», первый запрос может занять 30–60 секунд.§If the code doesn't arrive, request it again after 1 minute. If the server was asleep, the first request can take 30–60 seconds.
Narx qanday hisoblanadi?§Как рассчитывается цена?§How is the price calculated?
Minimal narx 8 000 so'm. Narx 5 000 so'm + har kilometr uchun 2 500 so'm, 500 so'mgacha yaxlitlanadi. Kilometr yo'l bo'yicha hisoblanadi.§Минимальная цена 8 000 сум. Цена: 5 000 сум + 2 500 сум за каждый километр, округляется до 500 сум. Километры считаются по дороге.§Minimum fare is 8,000 UZS. Price is 5,000 UZS + 2,500 UZS per kilometre, rounded to 500 UZS. Distance is measured along the road.
Buyurtmani qanday bekor qilaman?§Как отменить заказ?§How do I cancel an order?
Haydovchi qidirilayotganda yoki yo'lda bo'lsa, safar ekranida “Buyurtmani bekor qilish” tugmasi bor. Safar boshlangach bekor qilib bo'lmaydi.§Пока ищется водитель или он в пути, на экране поездки есть кнопка «Отменить заказ». После начала поездки отменить нельзя.§While a driver is being found or on the way, the trip screen has a “Cancel order” button. You can't cancel once the trip has started.
Qanday to'layman?§Как оплатить?§How do I pay?
Hozir to'lov naqd: safar tugagach pulni haydovchiga berasiz. Karta to'lovi tez orada ulanadi.§Сейчас оплата наличными: по окончании поездки вы отдаёте деньги водителю. Оплата картой скоро подключится.§Payment is cash for now: give the money to the driver at the end of the trip. Card payment will be added soon.
Manzilni qanday tanlayman?§Как выбрать адрес?§How do I choose an address?
“Qayerga” qatorini bosing va yozing, yoki “Xaritadan tanlash” ni bosib xaritaga tegining. Oldingi manzillaringiz ham taklif qilinadi.§Нажмите на строку «Куда» и введите адрес или выберите «Выбрать на карте» и коснитесь карты. Ваши прежние адреса тоже предлагаются.§Tap the “Where to” field and type, or tap “Pick on map” and touch the map. Your previous addresses are suggested too.
Haydovchini qanday baholayman?§Как оценить водителя?§How do I rate the driver?
Safar tugagach Safar bo'limida yulduzchalar chiqadi. Baho haydovchi reytingiga qo'shiladi.§После поездки в разделе «Поездка» появятся звёзды. Оценка добавится к рейтингу водителя.§After the trip, stars appear in the Trip tab. Your rating is added to the driver's rating.
Navigatsiya qanday ishlaydi?§Как работает навигация?§How does navigation work?
Buyurtmani qabul qilsangiz, xaritada marshrut va burilish ko'rsatmalari ilovaning o'zida chiqadi. Boshqa ilova o'rnatish shart emas. Ilovadan chiqib ketsangiz ham buyurtma saqlanib turadi.§После принятия заказа маршрут и подсказки поворотов показываются прямо в приложении. Другое приложение не нужно. Если вы выйдете из приложения, заказ сохранится.§After you accept an order, the route and turn-by-turn directions appear inside the app. No other app is needed. The order is kept even if you leave the app.
Buyurtma qanday keladi?§Как приходят заказы?§How do orders arrive?
Safar bo'limida “Ishga chiqish” tugmasini suring. Onlayn bo'lsangiz, yangi buyurtma tushganda telefon tebranadi va ovoz chiqadi.§Сдвиньте ползунок «Выйти на работу» в разделе «Поездка». Когда вы онлайн, при новом заказе телефон вибрирует и звучит сигнал.§Swipe the “Go online” slider in the Trip tab. While online, the phone vibrates and sounds when a new order comes in.
Naqd to'lovda nima qilaman?§Что делать при оплате наличными?§What do I do with cash payments?
Pulni yo'lovchidan o'zingiz olasiz va “Yakunlash” ni bosasiz.§Вы получаете деньги от пассажира и нажимаете «Завершить».§You collect the money from the passenger and tap “Finish”.
Buyurtmani rad qilsam nima bo'ladi?§Что будет, если отклонить заказ?§What happens if I decline an order?
Yo'lovchiga yetib kelguncha rad etishingiz mumkin: buyurtma boshqa haydovchilarga qayta ko'rsatiladi.§Отклонить можно до прибытия к пассажиру: заказ снова покажут другим водителям.§You can decline until you reach the passenger: the order is shown to other drivers again.
Xarita yoki joylashuv ishlamayapti§Карта или геолокация не работает§Map or location not working
Telefonda GPS ni yoqing va ilovaga joylashuv ruxsatini bering (Kabinet → Joylashuv ruxsatlari). Internet aloqasini ham tekshiring.§Включите GPS на телефоне и дайте приложению доступ к геолокации (Кабинет → Разрешения геолокации). Проверьте и интернет.§Turn on GPS and grant the app location permission (Cabinet → Location permissions). Check your internet connection too.
X Admin§X Admin§X Admin
Boshqaruv paneli§Панель управления§Control panel
Tasdiqlash kodi§Код подтверждения§Verification code
Admin kirishi§Вход для администратора§Admin sign-in
Telefon raqam§Номер телефона§Phone number
Ismingiz (birinchi marta kirganda)§Ваше имя (при первом входе)§Your name (first time only)
Kod Telegram orqali keladi. Avval botni oching, Start bosing va "Raqamni ulash" tugmasini bosing. Keyin shu yerga qaytib, "Kod olish" ni bosing.§Код придёт в Telegram. Сначала откройте бота, нажмите Start и кнопку «Привязать номер». Затем вернитесь сюда и нажмите «Получить код».§The code arrives via Telegram. First open the bot, press Start and tap "Link number". Then come back here and tap "Get code".
✈ Telegram botni ochish§✈ Открыть Telegram-бота§✈ Open Telegram bot
Telegram topilmadi. Uni o'rnating.§Telegram не найден. Установите его.§Telegram not found. Please install it.
Kod olish§Получить код§Get code
=Test kodi: §Тестовый код: §Test code:
SMS kod§SMS-код§SMS code
Kirish§Войти§Sign in
Bu hisob admin emas§Этот аккаунт не администратор§This account is not an admin
Raqamni o'zgartirish§Изменить номер§Change number
Server javob kutilmoqda… (birinchi marta 30–60 soniya ketishi mumkin)§Ожидание ответа сервера… (в первый раз может занять 30–60 секунд)§Waiting for server… (the first time may take 30–60 seconds)
Qabul qilingan§Принят§Accepted
Yetib kelgan§Прибыл§Arrived
Bugungi tushum§Выручка за сегодня§Today's revenue
=Jami: §Всего: §Total:
Faol buyurtmalar§Активные заказы§Active orders
Onlayn haydovchi§Водители онлайн§Drivers online
Yo'lovchilar§Пассажиры§Passengers
Buyurtmalar holati§Статусы заказов§Order statuses
=Buyurtma #§Заказ #§Order #
Bu buyurtmani bekor qilasizmi? Yo'lovchi va haydovchiga bekor qilingani ko'rinadi.§Отменить этот заказ? Пассажир и водитель увидят, что он отменён.§Cancel this order? The passenger and driver will see it was cancelled.
Ha, bekor qilish§Да, отменить§Yes, cancel
Buyurtma yo'q§Заказов нет§No orders
Yo'lovchi§Пассажир§Passenger
Buyurtmani bekor qilish§Отменить заказ§Cancel order
Blokdan chiqarish§Разблокировать§Unblock
= hisobini bloklaysizmi? U ilovaga kira olmaydi.§ — заблокировать аккаунт? Он не сможет войти в приложение.§ — block this account? They won't be able to sign in.
= hisobini qayta faollashtirasizmi?§ — вернуть доступ аккаунту?§ — reactivate this account?
Ha, bloklash§Да, заблокировать§Yes, block
Ha, chiqarish§Да, разблокировать§Yes, unblock
Ism yoki telefon bo'yicha qidirish§Поиск по имени или телефону§Search by name or phone
Hech kim topilmadi§Никого не найдено§Nobody found
Javob kutayotgan§Ждут ответа§Awaiting reply
Javob berilgan§Отвечено§Answered
yo'lovchi§пассажир§passenger
haydovchi§водитель§driver
Javob yuborish§Отправить ответ§Send reply
Yangi murojaat yo'q§Новых обращений нет§No new requests
Javob berilgan murojaat yo'q§Отвеченных обращений нет§No answered requests
🛡 Admin§🛡 Админ§🛡 Admin
🔎 Haydovchi qidirilmoqda§🔎 Ищем водителя§🔎 Finding a driver
🚕 Haydovchi yo'lda§🚕 Водитель в пути§🚕 Driver on the way
📍 Haydovchi yetib keldi§📍 Водитель прибыл§📍 Driver has arrived
➡️ Safar davom etmoqda§➡️ Поездка идёт§➡️ Trip in progress
✅ Yakunlandi§✅ Завершена§✅ Completed
❌ Bekor qilindi§❌ Отменена§❌ Cancelled
💳 Karta§💳 Карта§💳 Card
💵 Naqd§💵 Наличные§💵 Cash
= · to'langan§ · оплачено§ · paid
Ish holatini o'zgartirib bo'lmadi§Не удалось изменить статус работы§Couldn't change work status
Faol buyurtma — joylashuv yuborilmoqda§Активный заказ — передаётся геолокация§Active order — sharing location
Yo'lovchini olish joyi§Место посадки пассажира§Pickup point
Borish manzili§Адрес назначения§Destination
Yo'lovchi (hozir)§Пассажир (сейчас)§Passenger (now)
Ishdan chiqasizmi?§Завершить работу?§Go offline?
Offlayn bo'lsangiz, yangi buyurtmalar kelmaydi.§В офлайне новые заказы не поступают.§While offline you won't get new orders.
Ha, chiqaman§Да, выйти§Yes, go offline
Yo'q§Нет§No
🟢 ONLAYN§🟢 ОНЛАЙН§🟢 ONLINE
⚪ OFFLAYN§⚪ ОФЛАЙН§⚪ OFFLINE
🧭 Kuzatish: yoqiq§🧭 Слежение: вкл§🧭 Follow: on
🧭 Kuzatishni yoqish§🧭 Включить слежение§🧭 Turn on follow
📍 Mening joyim§📍 Моё место§📍 My location
Yo'lovchigacha§До пассажира§To passenger
Manzilgacha§До места назначения§To destination
Yo'lovchini kutmoqdasiz§Вы ждёте пассажира§Waiting for the passenger
Yo'lovchi chiqqach, safarni boshlang§Когда пассажир сядет, начните поездку§Start the trip when the passenger gets in
= marshrut hisoblanmoqda…§ — маршрут рассчитывается…§ — calculating route…
GPS kutilmoqda§Ожидание GPS§Waiting for GPS
Karta: yo'lovchi safar tugagach ilovada to'laydi§Карта: пассажир оплатит в приложении после поездки§Card: the passenger pays in the app after the trip
Yetib keldim§Я прибыл§I've arrived
Safarni boshlash§Начать поездку§Start trip
Yakunlash (naqd oldim)§Завершить (получил наличные)§Finish (cash received)
Yakunlash§Завершить§Finish
Buyurtmani rad etish§Отклонить заказ§Decline order
HAYDOVCHI HOLATI§СТАТУС ВОДИТЕЛЯ§DRIVER STATUS
Hozir ishda emassiz§Вы сейчас не на работе§You are not working now
Ishdasiz§Вы на работе§You are working
Ishdan chiqish uchun chapga suring§Сдвиньте влево, чтобы завершить§Swipe left to go offline
Ishga chiqish uchun o'ngga suring§Сдвиньте вправо, чтобы выйти на работу§Swipe right to go online
◀  ISHDAN CHIQISH UCHUN SURING§◀  СДВИНЬТЕ, ЧТОБЫ ЗАВЕРШИТЬ§◀  SWIPE TO GO OFFLINE
ISHGA CHIQISH UCHUN SURING  ▶§СДВИНЬТЕ, ЧТОБЫ НАЧАТЬ РАБОТУ  ▶§SWIPE TO GO ONLINE  ▶
ONLAYN§ОНЛАЙН§ONLINE
OFFLAYN§ОФЛАЙН§OFFLINE
Yangi buyurtmalar§Новые заказы§New orders
Kutilmoqda§Ожидание§Waiting
= ta mavjud§ доступно§ available
Hozircha buyurtma yo'q§Пока нет заказов§No orders yet
Yangi buyurtmalar shu yerda avtomatik paydo bo'ladi.§Новые заказы появятся здесь автоматически.§New orders will appear here automatically.
=Yo'lovchigacha: ~§До пассажира: ~§To passenger: ~
Qabul qilish§Принять§Accept
Safar davom etmoqda§Поездка идёт§Trip in progress
Faol safar§Активная поездка§Active trip
📞 Qo'ng'iroq§📞 Позвонить§📞 Call
Shahar ichida tez va qulay safar§Быстрые и удобные поездки по городу§Fast and comfortable rides across the city
⚙ Server sozlamalari§⚙ Настройки сервера§⚙ Server settings
Server manzili§Адрес сервера§Server address
= ilovasining yangi versiyasi chiqdi. Ilovaning o'zida yuklab o'rnatiladi.§: вышла новая версия. Она скачивается и устанавливается прямо в приложении.§: a new version is out. It downloads and installs inside the app.
Yandex MapKit kaliti ulanmagan. GitHub Actions Secret sozlamasini tekshiring.§Ключ Yandex MapKit не подключён.§Yandex MapKit key is not connected.
Manzilga yetdingiz§Вы прибыли§You have arrived
Yo'lga chiqing§Начните движение§Start driving
Aylanadan chiqing§Съезжайте с кольца§Exit the roundabout
Chapga buriling§Поверните налево§Turn left
O'ngga buriling§Поверните направо§Turn right
Chapga biroz buriling§Плавно налево§Bear left
O'ngga biroz buriling§Плавно направо§Bear right
Chapga keskin buriling§Резко налево§Sharp left
O'ngga keskin buriling§Резко направо§Sharp right
Orqaga qayriling§Развернитесь§Make a U-turn
Yo'lga qo'shiling§Перестройтесь на дорогу§Merge onto the road
To'g'ri yuring§Двигайтесь прямо§Continue straight
Mening joyim§Моё место§My location
Safar davom etmoqda — joylashuv yuborilmoqda§Поездка идёт — передаётся геолокация§Trip in progress — sharing location
Xaritadagi nuqta§Точка на карте§Point on map
Olinadigan joy§Место посадки§Pickup
✕ Chiqish§✕ Выход§✕ Exit
Haydovchi qidirilmoqda…§Ищем водителя…§Finding a driver…
Haydovchi yo'lda§Водитель в пути§Driver on the way
Haydovchi joylashuvi kutilmoqda…§Ожидание геолокации водителя…§Waiting for driver location…
Haydovchi sizni kutmoqda§Водитель вас ждёт§The driver is waiting for you
Mashinaga o'tiring§Садитесь в машину§Get in the car
Yaxshi yo'l!§Счастливого пути!§Have a good trip!
Olinadigan joyni belgilang§Укажите место посадки§Set the pickup point
Borish manzilini belgilang§Укажите адрес назначения§Set the destination
Xaritaga bosing§Нажмите на карту§Tap the map
Qidiruvga qaytish§Вернуться к поиску§Back to search
GPS bo'yicha§По GPS§By GPS
Xaritadan tanlash§Выбрать на карте§Pick on map
Xaritaga bosib belgilang§Отметьте точку на карте§Tap the map to mark
Oldingi safar§Прошлая поездка§Previous trip
Qidirilmoqda…§Поиск…§Searching…
Topilmadi. Boshqacha yozing yoki xaritadan tanlang.§Не найдено. Введите иначе или выберите на карте.§Not found. Try different words or pick on the map.
Safar yakunlandi§Поездка завершена§Trip completed
Karta bilan to'lash§Оплатить картой§Pay by card
Safar qanday o'tdi?§Как прошла поездка?§How was the trip?
Haydovchini baholang§Оцените водителя§Rate the driver
Joylashuv aniqlanmoqda…§Определяем местоположение…§Locating you…
Qayerga boramiz?§Куда поедем?§Where to?
Narx hisoblanmoqda…§Считаем цену…§Calculating price…
💵 To'lov: naqd, haydovchiga. Karta to'lovi tez orada.§💵 Оплата: наличными водителю. Карта скоро.§💵 Payment: cash to the driver. Card coming soon.
Karta bilan safar tugagach to'laysiz.§Картой вы платите после поездки.§You pay by card after the trip.
Taksi chaqirish§Вызвать такси§Order a taxi
Oldingi manzillar§Прошлые адреса§Previous addresses
Yuklangan fayl buzilgan, qayta urinib ko'ring§Файл повреждён, попробуйте ещё раз§The downloaded file is corrupted, try again
O'rnatib bo'lmadi§Не удалось установить§Couldn't install
Yuklab bo'lmadi§Не удалось скачать§Couldn't download
Ruxsat kerak§Нужно разрешение§Permission needed
O'rnatish oynasi ochildi§Окно установки открыто§Installer opened
Chiqqan oynada “O'rnatish” (Update) ni bosing. O'rnatilgach ilova o'zi qayta ochiladi.§В открывшемся окне нажмите «Установить» (Update). После установки приложение откроется само.§In the window that opens, tap “Install” (Update). The app reopens by itself afterwards.
Sozlamalar / Davom etish§Настройки / Продолжить§Settings / Continue
Qayta urinish§Повторить§Retry
Qayta ochish§Открыть снова§Reopen
Pul§Деньги§Money
Safarlar§Поездки§Trips
Naqd§Наличные§Cash
Karta§Карта§Card
Xato§Ошибка§Error
Kabinet§Кабинет§Cabinet
Foydalanuvchi§Пользователь§User
Daromad§Доход§Earnings
Sarflangan§Потрачено§Spent
Reyting§Рейтинг§Rating
Bekor§Отмены§Cancelled
Ilova§Приложение§App
Yordam§Помощь§Help
Yuborish§Отправить§Send
Murojaatlarim§Мои обращения§My requests
Umumiy§Обзор§Overview
Buyurtma§Заказ§Order
Odamlar§Люди§People
Murojaat§Обращение§Request
Profil§Профиль§Profile
Faol§Активные§Active
Tugagan§Завершённые§Finished
Hammasi§Все§All
Qidirilmoqda§Поиск§Searching
Safarda§В поездке§On trip
Yakunlangan§Завершён§Completed
Bloklangan§Заблокирован§Blocked
Buyurtmalar§Заказы§Orders
Haydovchi§Водитель§Driver
Bloklash§Заблокировать§Block
Onlayn§Онлайн§Online
Offlayn§Офлайн§Offline
Telefon§Телефон§Phone
Javobingiz§Ваш ответ§Your reply
Murojaatlar§Обращения§Requests
Admin§Админ§Admin
🙂 Haydovchi surati§🙂 Фото водителя§🙂 Driver photo
🤳 Foto nazorat (selfi)§🤳 Фотоконтроль (селфи)§🤳 Photo check (selfie)
🪪 Haydovchilik guvohnomasi§🪪 Водительское удостоверение§🪪 Driver's license
📄 Mashina tex pasporti§📄 Техпаспорт автомобиля§📄 Vehicle registration
🚗 Mashina raqami (surat)§🚗 Номер автомобиля (фото)§🚗 Car plate (photo)
‹ Orqaga§‹ Назад§‹ Back
=Yuklangan §Загружено §Uploaded 
= · Tasdiqlangan § · Подтверждено § · Approved 
Hujjatlar va tasdiqlash§Документы и подтверждение§Documents and verification
Rasmlarni aniq va to'liq oling. Admin tekshirib tasdiqlaydi.§Снимайте чётко и целиком. Администратор проверит и подтвердит.§Take clear, full photos. An admin will review and approve.
✅ Tasdiqlangan§✅ Подтверждён§✅ Verified
Tasdiqlangan§Подтверждено§Approved
Rad etildi§Отклонено§Rejected
Tekshirilmoqda§На проверке§Under review
Yuklanmagan§Не загружено§Not uploaded
Sabab: §Причина: §Reason: 
📷 Kamera§📷 Камера§📷 Camera
🖼 Galereya§🖼 Галерея§🖼 Gallery
Mashina raqami (masalan 01 A 123 BC)§Номер автомобиля (например 01 A 123 BC)§Car plate (e.g. 01 A 123 BC)
Raqamni saqlash§Сохранить номер§Save plate
Saqlandi§Сохранено§Saved
Hujjatlar§Документы§Documents
Hujjat§Документ§Docs
Kutayotgan§Ожидают§Pending
Haydovchi yo'q§Водителей нет§No drivers
Ko'rish va tekshirish§Просмотр и проверка§View and review
raqam yo'q§номера нет§no plate
Rasm yuklanmoqda…§Загрузка фото…§Loading photo…
Orqaga§Назад§Back
Rad etish sababi§Причина отказа§Rejection reason
Rad etish§Отклонить§Reject
✅ Tasdiqlash§✅ Подтвердить§✅ Approve
❌ Rad§❌ Отказ§❌ Reject
Rasmni o'qib bo'lmadi§Не удалось прочитать фото§Couldn't read the photo
Kamerani ochib bo'lmadi§Не удалось открыть камеру§Couldn't open the camera
Rasm hajmi noto'g'ri (3 MB gacha)§Неверный размер фото (до 3 МБ)§Invalid photo size (up to 3 MB)
Faqat JPEG rasm qabul qilinadi§Принимаются только JPEG-фото§Only JPEG photos are accepted
Hujjat turi noto'g'ri§Неверный тип документа§Invalid document type
Hujjat topilmadi§Документ не найден§Document not found
Rad etish sababini yozing§Укажите причину отказа§Enter a rejection reason
Mashina raqamini to'liq yozing§Введите номер автомобиля полностью§Enter the full car plate
Rasm topilmadi§Фото не найдено§Photo not found
Ruxsat yo'q§Нет доступа§No access
Men§Я§Me
Safar§Поездка§Trip
Chiqish§Выйти§Log out
Yangilash§Обновить§Update
Keyinroq§Позже§Later
Yopish§Закрыть§Close
Qayerdan§Откуда§From
Qayerga§Куда§Where to
Ishga chiqish§Выйти на работу§Go online
Sozlamalar§Настройки§Settings
Rington§Рингтон§Ringtone
Yangi buyurtma kelganda eshitiladigan ovoz. Bosib tanlang.§Звук нового заказа. Нажмите, чтобы выбрать.§Sound for a new order. Tap to choose.
Telefon ovozi§Звук телефона§Phone sound
Til§Язык§Language
Ko'rinish§Оформление§Appearance
☀ Yorug'§☀ Светлая§☀ Light
🌙 Qorong'i§🌙 Тёмная§🌙 Dark
Ovoz§Звук§Sound
🔔 Ovozli§🔔 Со звуком§🔔 Sound
📳 Tebranish§📳 Вибрация§📳 Vibrate
🔕 Ovozsiz§🔕 Без звука§🔕 Silent
Yangi buyurtma ovozi va tebranishi§Звук и вибрация новых заказов§New order sound and vibration
Shrift o'lchami§Размер шрифта§Font size
Kichik§Мелкий§Small
O'rta§Средний§Medium
Katta§Крупный§Large
Matn o'lchami butun ilovada o'zgaradi§Размер текста меняется во всём приложении§Text size changes across the whole app
Avval ishga chiqing§Сначала выйдите на работу§Go online first
Avval joriy buyurtmani tugating§Сначала завершите текущий заказ§Finish your current order first
Avvalgi murojaatlaringizga javob kelishini kuting§Дождитесь ответа на прежние обращения§Wait for replies to your earlier requests
Bekor qilib bo'lmaydi§Отменить нельзя§Can't be cancelled
Bu buyurtma allaqachon yakunlangan§Этот заказ уже завершён§This order is already completed
Bu buyurtma naqd to'lanadi§Этот заказ оплачивается наличными§This order is paid in cash
Bu hisob faqat admin ilovasi uchun§Этот аккаунт только для приложения администратора§This account is for the admin app only
Bu raqamga admin huquqi berilmagan§У этого номера нет прав администратора§This number has no admin rights
Bu safar allaqachon baholangan§Эта поездка уже оценена§This trip is already rated
Bu sizning buyurtmangiz emas§Это не ваш заказ§This is not your order
Buyurtma allaqachon olingan§Заказ уже принят§Order already taken
Buyurtma topilmadi§Заказ не найден§Order not found
Faqat admin§Только администратор§Admin only
Faqat haydovchi§Только водитель§Drivers only
Faqat yakunlangan safarni baholash mumkin§Оценить можно только завершённую поездку§Only completed trips can be rated
Faqat yo'lovchi buyurtma bera oladi§Заказывать может только пассажир§Only passengers can place orders
Foydalanuvchi topilmadi§Пользователь не найден§User not found
Hisobingiz bloklangan§Ваш аккаунт заблокирован§Your account is blocked
Ismingizni kiriting§Введите ваше имя§Enter your name
Javobni yozing§Напишите ответ§Write a reply
Karta to'lovi hali ulanmagan. Naqd to'lovni tanlang§Оплата картой пока не подключена. Выберите наличные§Card payment isn't available yet. Choose cash
Kod eskirgan, qaytadan so'rang§Код устарел, запросите заново§Code expired, request a new one
Kod noto'g'ri§Неверный код§Wrong code
Kodni 60 soniyadan keyin qayta so'rang§Запросите код повторно через 60 секунд§Request the code again after 60 seconds
Koordinata xato§Неверные координаты§Invalid coordinates
Manzil to'liq emas§Адрес неполный§Address incomplete
Murojaat topilmadi§Обращение не найдено§Request not found
Noto'g'ri holat§Неверный статус§Invalid status
Qaytadan kiring§Войдите заново§Please sign in again
SMS yuborilmadi, birozdan keyin urinib ko'ring§SMS не отправлено, попробуйте позже§SMS not sent, try again later
Safar tugagach to'lanadi§Оплата после поездки§Paid after the trip
Server xatosi§Ошибка сервера§Server error
Sizda faol buyurtma bor§У вас уже есть активный заказ§You already have an active order
Telefon raqam +998XXXXXXXXX ko'rinishida bo'lsin§Номер телефона в формате +998XXXXXXXXX§Phone number must look like +998XXXXXXXXX
Xabarni yozing (kamida 3 belgi)§Напишите сообщение (минимум 3 символа)§Write a message (at least 3 characters)
Allaqachon to'langan§Уже оплачено§Already paid
Adminni bloklab bo'lmaydi§Администратора заблокировать нельзя§Can't block an admin
Baho 1 dan 5 gacha bo'lsin§Оценка от 1 до 5§Rating must be 1 to 5
Topilmadi§Не найдено§Not found
Tarmoq xatosi§Ошибка сети§Network error
=Yo'lovchigacha§До пассажира§To passenger
=Manzilgacha§До места назначения§To destination
= daqiqada keladi§ мин§ min
= daqiqa§ мин§ min
= daq§ мин§ min
= so'm§ сум§ UZS
= ta safar§ поездок§ trips
~^Haydovchi ~(\d+) daqiqada keladi$§Водитель приедет через ~\1 мин§Driver arrives in ~\1 min
~^Manzilga ~(\d+) daqiqa$§До места ~\1 мин§~\1 min to destination
~^Safar ≈ (\d+) mins? ?·? ?(.*)$§Поездка ≈ \1 мин · \2§Trip ≈ \1 min · \2
~^Safar ≈ (\d+) daq(.*)$§Поездка ≈ \1 мин\2§Trip ≈ \1 min\2
~^To'lov: (.*)$§Оплата: \1§Payment: \1
~^Android (.*) ga yangilanishni o'rnatishga ruxsat so'raydi\..*$§Android запросит разрешение на установку обновления. Нажмите «Настройки», включите разрешение, вернитесь назад и нажмите «Продолжить».§Android will ask for permission to install the update. Tap “Settings”, enable it, go back and tap “Continue”.
Ovozli navigatsiya§Голосовая навигация§Voice navigation
🗣 Yoqilgan§🗣 Включена§🗣 On
🔇 O'chiq§🔇 Выключена§🔇 Off
🎁 Bugungi bonuslar§🎁 Бонусы за сегодня§🎁 Today's bonuses
💰 Balans§💰 Баланс§💰 Balance
Pul yechish§Вывод средств§Withdraw
Mavjud: §Доступно: §Available: 
Summa (so'm)§Сумма (сум)§Amount (UZS)
Karta raqami yoki izoh§Номер карты или комментарий§Card number or note
So'rovlar§Запросы§Requests
To'landi§Выплачено§Paid
Oxirgi harakatlar§Последние операции§Recent activity
Narsa qoldirdingizmi?§Забыли вещь?§Left something behind?
Nima qoldirganingizni yozing. Haydovchiga xabar boradi va u siz bilan bog'lanadi.§Напишите, что забыли. Водитель получит сообщение и свяжется с вами.§Tell us what you left. The driver will be notified and contact you.
Masalan: qora ryukzak§Например: чёрный рюкзак§E.g. black backpack
To'lovlar§Платежи§Payments
Pul yechish so'rovlari§Запросы на вывод§Withdrawal requests
Kutilayotgan so'rov yo'q§Нет ожидающих запросов§No pending requests
Bonus va shaharlararo tarif§Бонусы и межгородский тариф§Bonuses and intercity tariff
Saqlash§Сохранить§Save
Safar soni§Кол-во поездок§Trips
Mukofot§Награда§Reward
1 km narxi§Цена за 1 км§Price per km
Eng kam narx§Минимальная цена§Minimum price
Qaysi shaharga?§В какой город?§Which city?
Necha kishi§Сколько человек§How many people
Qachon? (masalan: ertaga 08:00)§Когда? (например: завтра 08:00)§When? (e.g. tomorrow 08:00)
Narx butun yo'l uchun. Haydovchi buyurtmani qabul qilgach siz bilan bog'lanadi.§Цена за всю поездку. Водитель свяжется с вами после принятия заказа.§Price is for the whole trip. The driver will contact you after accepting.
Shaharlararo buyurtma§Межгородский заказ§Intercity order
🚕 Shahar§🚕 Город§🚕 City
🛣 Shaharlararo§🛣 Межгород§🛣 Intercity
🔎 Yo'qolgan narsa haqida haydovchiga xabar yuborildi§🔎 Водителю отправлено сообщение о забытой вещи§🔎 The driver was notified about your lost item
Keyingi buyurtma (oldindan band qilish)§Следующий заказ (бронь заранее)§Next order (reserve in advance)
Keyingisiga band qilish§Забронировать следующим§Reserve as next
Bandni bekor qilish§Отменить бронь§Cancel reservation
✅ Band qilindi — safardan keyin avtomatik boshlanadi§✅ Забронировано — начнётся автоматически после поездки§✅ Reserved — starts automatically after this trip
🔥 Talab yuqori joylar§🔥 Зоны высокого спроса§🔥 High demand areas
Xaritada to'q sariq belgilar — talab zonalari§Оранжевые метки на карте — зоны спроса§Orange markers on the map are demand zones
Yangi buyurtma!§Новый заказ!§New order!
Yaqin atrofda yangi buyurtma bor§Рядом есть новый заказ§There is a new order nearby
Haydovchi buyurtmangizni qabul qildi§Водитель принял ваш заказ§The driver accepted your order
Haydovchi yetib keldi§Водитель приехал§The driver has arrived
Safar boshlandi§Поездка началась§Trip started
Safar tugadi§Поездка завершена§Trip finished
Buyurtma bekor qilindi§Заказ отменён§Order cancelled
Onlayn — yangi buyurtmalar kutilmoqda§Онлайн — ожидание новых заказов§Online — waiting for new orders
Yo'lovchi narsa qoldirgan: §Пассажир забыл вещь: §Passenger left an item: 
Telefon: §Телефон: §Phone: 
Yangiliklar§Новости§Updates
To'lov§Оплата§Payment
Kamida 3 ta belgi§Минимум 3 символа§At least 3 characters
Balans yetarli emas§Недостаточно средств§Insufficient balance
Karta raqami yoki izohni yozing§Укажите номер карты или комментарий§Enter a card number or note
~^Bugun (\d+) ta safar tugatdingiz$§Сегодня вы завершили \1 поездок§You completed \1 trips today
~^(\d+) ta safar$§\1 поездок§\1 trips
~^(\+[\d ]+ so'm)  ·  yana (\d+) ta$§\1  ·  ещё \2§\1  ·  \2 more
~^Pul yechish uchun kamida (.+) kerak$§Для вывода нужно минимум \1§Minimum \1 required to withdraw
~^Kamida (\d+ [\d ]*so'm)$§Минимум \1§At least \1
~^🔥 Talab: (\d+) ta$§🔥 Спрос: \1§🔥 Demand: \1
~^📍 (\d+) ta buyurtma$§📍 \1 заказов§📍 \1 orders
~^🛣 Shaharlararo: (.*) · (\d+) o'rin(.*)$§🛣 Межгород: \1 · \2 мест\3§🛣 Intercity: \1 · \2 seats\3
~^Mavjud: (.+)  ·  kamida (.+)$§Доступно: \1  ·  минимум \2§Available: \1  ·  minimum \2
⚠ Hujjatlaringiz tasdiqlanmagan. Kabinet → Hujjatlar bo'limidan yuboring.§⚠ Ваши документы не подтверждены. Отправьте их в Кабинет → Документы.§⚠ Your documents are not verified. Submit them in Cabinet → Documents.
~^(\d+) ta buyurtma$§\1 заказов§\1 orders
~^Yana (\d+) ta buyurtma$§Ещё \1 заказов§\1 more orders
~^(\d+)/(\d+) · Bugun$§\1/\2 · Сегодня§\1/\2 · Today
Shaharlararo§Межгород§Intercity
Chatlar§Чаты§Chats
Daraja§Уровень§Level
Ustuvorlik§Приоритет§Priority
Yangi§Новичок§Newbie
Tajribali§Опытный§Experienced
Ekspert§Эксперт§Expert
Naqd pul§Наличные§Cash
Mening transportim§Мой транспорт§My vehicle
Mening avtomobilim§Мой автомобиль§My car
Raqam kiritilmagan§Номер не указан§No plate added
Ilova versiyasi§Версия приложения§App version
Hozircha shaharlararo buyurtma yo'q§Пока нет межгородних заказов§No intercity orders yet
Liniyada§На линии§Online
Liniyada emas§Не на линии§Offline
Buyurtmalar qidirilmoqda§Идёт поиск заказов§Looking for orders
Buyurtmalarni qidirish o'chirilgan§Поиск заказов отключён§Order search is off
Taklif kodi (ixtiyoriy)§Код приглашения (необязательно)§Invite code (optional)
Mashina markasi (masalan Chevrolet Cobalt)§Марка автомобиля (например Chevrolet Cobalt)§Car make (e.g. Chevrolet Cobalt)
Raqam va markani saqlash§Сохранить номер и марку§Save plate and make
Promokod§Промокод§Promo code
Qo'llash§Применить§Apply
Do'stingizni taklif qiling§Пригласите друга§Invite a friend
Sizning kodingiz§Ваш код§Your code
Taklif qilganlaringiz§Ваши приглашённые§People you invited
Ulashish§Поделиться§Share
Diagnostika§Диагностика§Diagnostics
Muddat tugash sanasini yozing (YYYY-OO-KK). Muddat yaqinlashsa, asosiy oynada eslatma chiqadi.§Укажите дату окончания (ГГГГ-ММ-ДД). Когда срок подойдёт, на главном экране появится напоминание.§Enter the expiry date (YYYY-MM-DD). A reminder appears on the main screen when it is close.
🛡 Sug'urta (OSAGO)§🛡 Страховка (ОСАГО)§🛡 Insurance (OSAGO)
🔧 Texnik ko'rik§🔧 Техосмотр§🔧 Technical inspection
Muddati o'tgan§Срок истёк§Expired
Promokodlar§Промокоды§Promo codes
Promokod yaratish§Создать промокод§Create promo code
Kod (masalan X10)§Код (например X10)§Code (e.g. X10)
% foiz§% процент§% percent
So'm§Сум§UZS
Foiz (1-50)§Процент (1-50)§Percent (1-50)
Summa§Сумма§Amount
Necha marta§Сколько раз§How many times
Necha kun§Сколько дней§How many days
O'chirish§Отключить§Disable
Yoqish§Включить§Enable
Taklif bonusi (do'st necha safar qilsa, qancha beriladi)§Бонус за приглашение (сколько выплачивается, когда друг совершит поездки)§Referral bonus (paid when the friend completes the trips)
~^🎟 (.+) qo'llandi$§🎟 \1 применён§🎟 \1 applied
~^Chegirma −(.+)  →  (.+)$§Скидка −\1  →  \2§Discount −\1  →  \2
~^(\d+) kun qoldi$§Осталось \1 дн.§\1 days left
~^⚠ (.+) muddati o'tgan$§⚠ Срок истёк: \1§⚠ \1 has expired
~^⚠ (.+): (\d+) kun qoldi$§⚠ \1: осталось \2 дн.§⚠ \1: \2 days left
Yordam xizmati§Служба поддержки§Support
👋 Yordam xizmati§👋 Служба поддержки§👋 Support
Oxirgi buyurtmalar§Последние заказы§Recent orders
Hisob balansi§Баланс счёта§Account balance
Pul yechish va harakatlar tarixi uchun bosing§Нажмите для вывода средств и истории операций§Tap for withdrawals and transaction history
Taqqoslash§Сравнение§Compare
Tafsilotlar§Детали§Details
Bugun§Сегодня§Today
Bu kunda safarlar yo'q§В этот день поездок нет§No trips on this day
Naqd pulda§Наличными§In cash
Balans§Баланс§Balance
Chiqarish§Вывести§Withdraw
Tranzaksiyalar tarixi§История операций§Transaction history
Hali harakatlar yo'q§Операций пока нет§No transactions yet
Hammasini ochish§Показать все§Show all
~^Naqd pul bilan · (.+)$§Наличными · \1§In cash · \1
~^Bonuslar · (.+)$§Бонусы · \1§Bonuses · \1
~^Karta bilan · (.+)$§Картой · \1§By card · \1
~^Pul chiqarish uchun kamida (.+) kerak$§Для вывода нужно минимум \1§Minimum \1 required to withdraw
"""

private class Rules(
    val exact: HashMap<String, String>,
    val frags: List<Pair<String, String>>,
    val regexes: List<Pair<Regex, String>>,
)

private fun build(col: Int): Rules {
    val exact = HashMap<String, String>()
    val frags = ArrayList<Pair<String, String>>()
    val regexes = ArrayList<Pair<Regex, String>>()
    for (line in TABLE.lines()) {
        if (line.isBlank()) continue
        val p = line.split("§")
        if (p.size < 3) continue
        val key = p[0]
        val value = if (key.endsWith(" ") && !p[col].endsWith(" ")) p[col] + " " else p[col]
        when {
            key.startsWith("~") -> try {
                regexes.add(Regex(key.substring(1)) to value.replace("\\1", "\$1").replace("\\2", "\$2"))
            } catch (e: Exception) { }
            key.startsWith("=") -> frags.add(key.substring(1) to value)
            else -> exact[key] = value
        }
    }
    frags.sortByDescending { it.first.length }
    return Rules(exact, frags, regexes)
}

private val RU by lazy { build(1) }
private val EN by lazy { build(2) }

/** Matnni tanlangan tilga o'giradi. Til o'zbekcha bo'lsa yoki jadvalda topilmasa, matn o'zgarmaydi. */
fun translate(s: String, lang: String): String {
    if (lang == "uz" || s.isBlank()) return s
    val r = if (lang == "ru") RU else EN
    r.exact[s]?.let { return it }
    for ((re, rep) in r.regexes) {
        if (re.containsMatchIn(s)) return re.replace(s, rep)
    }
    var out = s
    for ((k, v) in r.frags) if (out.contains(k)) out = out.replace(k, v)
    out = out.replace(Regex("(\\d+) ta\\b"), "$1")
    // "Yo'lovchigacha 5 daq" kabi: bo'laklardan yig'ilgan matnning ayrim so'zlarini ham o'giramiz
    if (out == s) {
        for ((k, v) in r.exact) {
            if (k.length >= 8 && out.contains(k)) out = out.replace(k, v)
        }
    }
    return out
}

@Composable
fun tr(s: String): String = translate(s, AppSettings.lang)

/** material3.Text o'rniga: matn tanlangan tilga avtomatik o'giriladi. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    // Qorong'i rejimda: katta qalin sarlavha va raqamlar tillo, ikkinchi darajali matn kumush gradientda
    val dark = AppSettings.dark
    val gold = dark && ((color == Color.Unspecified && fontWeight == FontWeight.ExtraBold && fontSize != TextUnit.Unspecified && fontSize.value >= 20f) || color == Gold)
    val silver = dark && !gold && color == Muted
    val st = when {
        gold -> style.copy(brush = GoldTextBrush)
        silver -> style.copy(brush = SilverTextBrush)
        else -> style
    }
    androidx.compose.material3.Text(
        text = tr(text), modifier = modifier, color = if (gold || silver) Color.Unspecified else color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, letterSpacing = letterSpacing, textAlign = textAlign, lineHeight = lineHeight,
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, style = st,
    )
}
