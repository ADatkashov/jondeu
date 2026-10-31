import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

/// Lightweight localization (kk / ru / en). No user-facing string is hard-coded in widgets.
const supportedLangs = ['kk', 'ru', 'en'];

const Map<String, List<String>> _t = {
  // key: [ru, kk, en]
  'tagline': ['Жөндеу оңай.', 'Жөндеу оңай.', 'Home renovation made simple.'],
  'login': ['Войти', 'Кіру', 'Sign in'], 'register': ['Регистрация', 'Тіркелу', 'Create account'], 'logout': ['Выйти', 'Шығу', 'Sign out'],
  'phone': ['Телефон', 'Телефон', 'Phone'], 'password': ['Пароль', 'Құпиясөз', 'Password'], 'fullName': ['Имя и фамилия', 'Аты-жөні', 'Full name'],
  'noAccount': ['Нет аккаунта? Регистрация', 'Аккаунт жоқ па? Тіркелу', 'No account? Register'], 'haveAccount': ['Уже есть аккаунт? Войти', 'Аккаунт бар ма? Кіру', 'Have an account? Sign in'],
  'iAm': ['Я', 'Мен', 'I am'], 'terms': ['Принимаю условия и политику конфиденциальности', 'Шарттар мен құпиялылық саясатын қабылдаймын', 'I accept the terms and privacy policy'],
  'valPhone': ['Введите номер Казахстана', 'Қазақстан нөмірін енгізіңіз', 'Enter a Kazakhstan number'], 'valPassword': ['Минимум 8 символов', 'Кемінде 8 таңба', 'At least 8 characters'],
  'valRequired': ['Обязательное поле', 'Міндетті өріс', 'Required'], 'valNumber': ['Введите число больше 0', '0-ден үлкен сан енгізіңіз', 'Enter a number above 0'], 'valTerms': ['Примите условия', 'Шарттарды қабылдаңыз', 'Accept the terms'],
  'home': ['Главная', 'Басты', 'Home'], 'masters': ['Мастера', 'Шеберлер', 'Masters'], 'requests': ['Заявки', 'Өтінімдер', 'Requests'], 'chat': ['Чат', 'Чат', 'Chat'], 'profile': ['Профиль', 'Профиль', 'Profile'],
  'projects': ['Проекты', 'Жобалар', 'Projects'], 'estimate': ['Смета', 'Смета', 'Estimate'], 'stages': ['Этапы', 'Кезеңдер', 'Stages'], 'payments': ['Платежи', 'Төлемдер', 'Payments'],
  'budget': ['Бюджет', 'Бюджет', 'Budget'], 'paid': ['Оплачено', 'Төленді', 'Paid'], 'remaining': ['Остаток', 'Қалдық', 'Remaining'], 'progress': ['Готовность', 'Дайындық', 'Completion'],
  'deadline': ['Срок сдачи', 'Тапсыру мерзімі', 'Deadline'], 'quality': ['Качество', 'Сапа', 'Quality'], 'activity': ['Последние события', 'Соңғы оқиғалар', 'Recent activity'],
  'noProjects': ['Пока нет проектов', 'Жобалар әзірге жоқ', 'No projects yet'], 'noProjectsHint': ['Создайте заявку — мастера пришлют предложения.', 'Өтінім жасаңыз — шеберлер ұсыныс жібереді.', 'Create a request — masters will send proposals.'],
  'newRequest': ['Новая заявка', 'Жаңа өтінім', 'New request'], 'retry': ['Повторить', 'Қайталау', 'Retry'], 'error': ['Что-то пошло не так', 'Бірдеңе дұрыс болмады', 'Something went wrong'], 'offline': ['Нет соединения. Показаны сохранённые данные.', 'Байланыс жоқ. Сақталған деректер көрсетілді.', 'Offline. Showing saved data.'],
  'live': ['Синхронизация', 'Синхрондау', 'Live'], 'connecting': ['Подключение…', 'Қосылуда…', 'Connecting…'],
  'search': ['Поиск мастера', 'Шеберді іздеу', 'Search masters'], 'verified': ['Проверен', 'Тексерілген', 'Verified'], 'onlineNow': ['Свободен', 'Бос', 'Available'], 'busy': ['Занят', 'Бос емес', 'Busy'],
  'perHour': ['/час', '/сағ', '/hour'], 'years': ['лет опыта', 'жыл тәжірибе', 'yrs exp.'], 'contact': ['Написать', 'Жазу', 'Message'], 'reviews': ['Отзывы', 'Пікірлер', 'Reviews'], 'noReviews': ['Отзывов пока нет', 'Пікір әзірге жоқ', 'No reviews yet'],
  'allCats': ['Все', 'Барлығы', 'All'], 'onlyVerified': ['Проверенные', 'Тексерілгендер', 'Verified'], 'sort': ['Сортировка', 'Сұрыптау', 'Sort'], 'sortRec': ['Рекомендуемые', 'Ұсынылатын', 'Recommended'], 'sortRating': ['По рейтингу', 'Рейтинг бойынша', 'By rating'], 'sortPrice': ['По цене', 'Баға бойынша', 'By price'], 'sortExp': ['По опыту', 'Тәжірибе бойынша', 'By experience'],
  'next': ['Далее', 'Әрі қарай', 'Next'], 'back': ['Назад', 'Артқа', 'Back'], 'cancel': ['Отмена', 'Бас тарту', 'Cancel'], 'save': ['Сохранить', 'Сақтау', 'Save'], 'send': ['Отправить', 'Жіберу', 'Send'], 'done': ['Готово', 'Дайын', 'Done'],
  'stepProperty': ['Объект', 'Объект', 'Property'], 'stepNeeds': ['Требования', 'Талаптар', 'Requirements'], 'stepBudget': ['Бюджет', 'Бюджет', 'Budget'], 'stepTimeline': ['Сроки', 'Мерзімдер', 'Timeline'], 'stepConfirm': ['Подтверждение', 'Растау', 'Confirm'],
  'projectName': ['Название проекта', 'Жоба атауы', 'Project name'], 'area': ['Площадь, м²', 'Ауданы, м²', 'Area, m²'], 'rooms': ['Комнат', 'Бөлме', 'Rooms'], 'address': ['Адрес', 'Мекенжай', 'Address'], 'category': ['Вид ремонта', 'Жөндеу түрі', 'Category'],
  'description': ['Описание работ', 'Жұмыс сипаттамасы', 'Description'], 'budgetMin': ['Бюджет от, ₸', 'Бюджет бастап, ₸', 'Budget from, ₸'], 'budgetMax': ['Бюджет до, ₸', 'Бюджет дейін, ₸', 'Budget to, ₸'], 'materialsIncluded': ['Материалы включены', 'Материалдар кіреді', 'Materials included'],
  'startDate': ['Желаемое начало', 'Қалаулы басталуы', 'Desired start'], 'submitRequest': ['Отправить мастерам', 'Шеберлерге жіберу', 'Send to masters'], 'requestSent': ['Заявка отправлена', 'Өтінім жіберілді', 'Request sent'], 'apartment': ['Квартира', 'Пәтер', 'Apartment'], 'house': ['Дом', 'Үй', 'House'],
  'proposals': ['Предложения', 'Ұсыныстар', 'Proposals'], 'accept': ['Выбрать', 'Таңдау', 'Select'], 'reject': ['Отклонить', 'Қабылдамау', 'Reject'], 'days': ['дн.', 'күн', 'days'], 'noProposals': ['Предложений пока нет', 'Ұсыныстар әзірге жоқ', 'No proposals yet'],
  'approve': ['Утвердить', 'Бекіту', 'Approve'], 'requestChanges': ['Запросить правки', 'Түзетуді сұрау', 'Request changes'], 'noEstimates': ['Смет пока нет', 'Сметалар әзірге жоқ', 'No estimates yet'], 'subtotal': ['Подитог', 'Аралық сома', 'Subtotal'],
  'additional': ['Доп. расходы', 'Қосымша шығындар', 'Additional costs'], 'discount': ['Скидка', 'Жеңілдік', 'Discount'], 'tax': ['Налог', 'Салық', 'Tax'], 'total': ['Итого', 'Барлығы', 'Total'], 'locked': ['Зафиксирована', 'Бекітілген', 'Locked'], 'confirmApprove': ['Утвердить смету? Бюджет проекта будет зафиксирован.', 'Сметаны бекіту керек пе? Жоба бюджеті бекітіледі.', 'Approve the estimate? The project budget will be fixed.'],
  'requestReview': ['Сдать на приёмку', 'Қабылдауға тапсыру', 'Submit for acceptance'], 'acceptStage': ['Принять этап', 'Кезеңді қабылдау', 'Accept stage'], 'returnStage': ['Вернуть в работу', 'Жұмысқа қайтару', 'Return'], 'noStages': ['Этапы появятся после утверждения сметы', 'Кезеңдер смета бекітілгеннен кейін пайда болады', 'Stages appear once the estimate is approved'],
  'payNow': ['Оплатить', 'Төлеу', 'Pay'], 'payMethod': ['Способ оплаты', 'Төлем тәсілі', 'Payment method'], 'amount': ['Сумма, ₸', 'Сомасы, ₸', 'Amount, ₸'], 'toPay': ['К оплате', 'Төлеуге', 'To pay'], 'available': ['Доступно', 'Қолжетімді', 'Available'], 'history': ['История платежей', 'Төлем тарихы', 'Payment history'],
  'noPayments': ['Платежей пока нет', 'Төлемдер әзірге жоқ', 'No payments yet'], 'exceeds': ['Сумма больше остатка', 'Сома қалдықтан көп', 'Exceeds the balance'], 'sandboxNote': ['Тестовый режим: деньги не списываются, платёж подтверждается только подписанным ответом шлюза.', 'Сынақ режимі: ақша есептен шығарылмайды, төлем тек қол қойылған жауаппен расталады.', 'Test mode: no real money; the payment is confirmed only by a signed gateway callback.'],
  'manualNote': ['Подтверждается получателем после поступления средств.', 'Қаражат түскеннен кейін алушы растайды.', 'Confirmed by the recipient once funds arrive.'], 'confirmReceipt': ['Подтвердить получение', 'Алғанын растау', 'Confirm receipt'], 'requestPayment': ['Запросить оплату', 'Төлем сұрау', 'Request payment'], 'paymentCreated': ['Платёж создан', 'Төлем жасалды', 'Payment created'],
  'sandboxConfirm': ['Тест: подтвердить', 'Сынақ: растау', 'Sandbox: confirm'], 'message': ['Сообщение…', 'Хабарлама…', 'Message…'], 'noChats': ['Чатов пока нет', 'Чаттар әзірге жоқ', 'No chats yet'], 'language': ['Язык', 'Тіл', 'Language'], 'city': ['Город', 'Қала', 'City'],
  'openRequests': ['Открытые заявки', 'Ашық өтінімдер', 'Open requests'], 'bid': ['Предложить цену', 'Баға ұсыну', 'Submit proposal'], 'price': ['Цена, ₸', 'Бағасы, ₸', 'Price, ₸'], 'duration': ['Срок, дней', 'Мерзімі, күн', 'Duration, days'], 'noRequests': ['Открытых заявок нет', 'Ашық өтінімдер жоқ', 'No open requests'],
  'photos': ['Фотоотчёты', 'Фотоесептер', 'Photo reports'], 'addPhoto': ['Добавить фото', 'Фото қосу', 'Add photo'], 'noPhotos': ['Фото пока нет', 'Фото әзірге жоқ', 'No photos yet'], 'materials': ['Материалы', 'Материалдар', 'Materials'], 'noMaterials': ['Материалов пока нет', 'Материалдар әзірге жоқ', 'No materials yet'],
  'demo': ['ДЕМО-ДАННЫЕ', 'ДЕМО-ДЕРЕКТЕР', 'DEMO DATA'], 'notifications': ['Уведомления', 'Хабарландырулар', 'Notifications'], 'complete': ['Принять объект', 'Объектті қабылдау', 'Accept handover'], 'review': ['Оставить отзыв', 'Пікір қалдыру', 'Leave a review'],
  'leftDays': ['осталось дней', 'күн қалды', 'days left'], 'planned': ['План', 'Жоспар', 'Planned'], 'actual': ['Факт', 'Нақты', 'Actual'],
  // enums
  'DRAFT': ['Черновик', 'Жоба', 'Draft'], 'SUBMITTED': ['Отправлена', 'Жіберілді', 'Submitted'], 'UNDER_REVIEW': ['На проверке', 'Тексеруде', 'Under review'], 'PROPOSALS_RECEIVED': ['Есть предложения', 'Ұсыныстар бар', 'Proposals'],
  'CONTRACTOR_SELECTED': ['Подрядчик выбран', 'Мердігер таңдалды', 'Contractor selected'], 'IN_PROGRESS': ['В работе', 'Жұмыста', 'In progress'], 'COMPLETED': ['Завершён', 'Аяқталды', 'Completed'], 'CANCELLED': ['Отменён', 'Бас тартылды', 'Cancelled'],
  'NOT_STARTED': ['Ожидает', 'Күтуде', 'Not started'], 'BLOCKED': ['Заблокирован', 'Бұғатталған', 'Blocked'], 'DELAYED': ['Задержка', 'Кешігу', 'Delayed'], 'APPROVED': ['Утверждена', 'Бекітілді', 'Approved'], 'REJECTED': ['Отклонена', 'Қабылданбады', 'Rejected'],
  'CHANGES_REQUESTED': ['Нужны правки', 'Түзету қажет', 'Changes requested'], 'SUPERSEDED': ['Заменена', 'Ауыстырылды', 'Superseded'], 'REQUESTED': ['Запрошен', 'Сұралды', 'Requested'], 'PENDING': ['Ожидает', 'Күтуде', 'Pending'], 'PAID': ['Оплачен', 'Төленді', 'Paid'],
  'FAILED': ['Не прошёл', 'Сәтсіз', 'Failed'], 'REFUNDED': ['Возврат', 'Қайтарылды', 'Refunded'], 'PLANNED': ['Запланирован', 'Жоспарланды', 'Planned'], 'ORDERED': ['Заказан', 'Тапсырыс берілді', 'Ordered'], 'PARTIALLY_DELIVERED': ['Частично', 'Жартылай', 'Partial'],
  'DELIVERED': ['Доставлен', 'Жеткізілді', 'Delivered'], 'PASSED': ['Пройдено', 'Өтті', 'Passed'], 'NEEDS_IMPROVEMENT': ['Доработки', 'Түзету', 'Needs work'], 'ACCEPTED': ['Принято', 'Қабылданды', 'Accepted'],
  'CARD': ['Банковская карта', 'Банк картасы', 'Bank card'], 'KASPI': ['Kaspi', 'Kaspi', 'Kaspi'], 'BANK_TRANSFER': ['Банковский перевод', 'Банк аударымы', 'Bank transfer'], 'CASH': ['Наличные', 'Қолма-қол', 'Cash'],
  'ADVANCE': ['Аванс', 'Аванс', 'Advance'], 'STAGE': ['Оплата этапа', 'Кезең төлемі', 'Stage payment'], 'MATERIAL': ['Материалы', 'Материалдар', 'Materials'], 'FINAL': ['Финальный платёж', 'Қорытынды төлем', 'Final payment'], 'ADDITIONAL': ['Доп. работы', 'Қосымша жұмыстар', 'Additional work'],
  'CUSTOMER': ['Заказчик', 'Тапсырыс беруші', 'Customer'], 'MASTER': ['Мастер', 'Шебер', 'Master'], 'CONTRACTOR': ['Подрядчик', 'Мердігер', 'Contractor'], 'COMPANY_ADMIN': ['Компания', 'Компания', 'Company'],
  'e_BAD_CREDENTIALS': ['Неверный телефон или пароль', 'Телефон немесе құпиясөз қате', 'Invalid phone or password'], 'e_PHONE_TAKEN': ['Номер уже зарегистрирован', 'Нөмір тіркелген', 'Phone already registered'],
  'e_EXCEEDS_OUTSTANDING': ['Сумма больше остатка по бюджету', 'Сома бюджет қалдығынан көп', 'Amount exceeds the outstanding budget'], 'e_STAGE_BLOCKED': ['Сначала завершите предыдущий этап', 'Алдымен алдыңғы кезеңді аяқтаңыз', 'Finish the previous stage first'],
  'e_OUTSTANDING_BALANCE': ['Сначала оплатите остаток', 'Алдымен қалдықты төлеңіз', 'Pay the outstanding balance first'], 'e_PROVIDER_NOT_CONFIGURED': ['Способ оплаты пока не подключён', 'Төлем тәсілі қосылмаған', 'Payment method not connected'],
  'e_NO_APPROVED_ESTIMATE': ['Сначала нужно утвердить смету', 'Алдымен сметаны бекітіңіз', 'Approve an estimate first'], 'e_NETWORK': ['Нет соединения с сервером', 'Серверге қосылу жоқ', 'Cannot reach the server'],
};

class S {
  final String lang;
  const S(this.lang);
  int get _i => switch (lang) { 'kk' => 1, 'en' => 2, _ => 0 };
  String t(String k) => _t[k]?[_i] ?? _t[k]?[0] ?? k;
  String err(String code, String fallback) => _t['e_$code']?[_i] ?? fallback;
  String get intlLocale => switch (lang) { 'kk' => 'kk_KZ', 'en' => 'en_GB', _ => 'ru_RU' };
  String money(num v) => '${NumberFormat.decimalPattern(intlLocale).format(v.round())} ₸';
  String date(String? iso) {
    if (iso == null || iso.isEmpty) return '—';
    final d = DateTime.tryParse(iso);
    return d == null ? iso : DateFormat('dd.MM.yyyy').format(d.toLocal());
  }
  String dateTime(String iso) => DateFormat('dd MMM, HH:mm', intlLocale).format(DateTime.parse(iso).toLocal());
  String time(String iso) => DateFormat('HH:mm').format(DateTime.parse(iso).toLocal());
  static bool get testKeysComplete => _t.values.every((v) => v.length == 3 && v.every((s) => s.trim().isNotEmpty));
}

class LangNotifier extends Notifier<String> {
  @override
  String build() => 'ru';
  void set(String l) => state = l;
}

final langProvider = NotifierProvider<LangNotifier, String>(LangNotifier.new);
final sProvider = Provider<S>((ref) => S(ref.watch(langProvider)));
