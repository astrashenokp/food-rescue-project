# Food Rescue

## 1. Що це за проєкт

Food Rescue — REST API платформи, що допомагає не викидати їжу. Донор публікує лот із залишками їжі, волонтер його резервує й забирає, а доставляє в пункт призначення, де отримання підтверджують кодом. Дані зберігаються в пам'яті: бази даних, безпеки й планувальника поки що немає.

## 2. Як запустити й перевірити

Потрібні Java 25 і Maven Wrapper (лежить у репозиторії).

```
./mvnw test                  # усі тести, серед них ModulesTest (verify() меж модулів)
./mvnw spring-boot:run       # запуск застосунку на http://localhost:8080
```

При старті застосунок сам створює демо-лоти й демо-волонтера з фіксованими id (див. `DemoData` і `VolunteerDemoData`).

## 3. Модулі й правила залежностей

Кожен пакет першого рівня в `com.example.foodrescue` — окремий модуль Spring Modulith. Кордони між ними перевіряє `ModulesTest` (`ApplicationModules.verify()`).

| Модуль | Що в ньому |
|--------|------------|
| `common` | Спільне ядро: лот і його статуси, сховище лотів, історія переходів, спільні винятки, глобальний обробник помилок, інфраструктура (`Clock`, менеджер транзакцій), демо-дані |
| `lot` | БК-1: публікація лоту |
| `volunteer` | БК-2: волонтери та резервування |
| `delivery` | БК-3: передача, доставка, підтвердження; публікує подію `DeliveryFinishedEvent` |

Дозволені залежності:

```
lot        ──►  common
volunteer  ──►  common
delivery   ──►  common
lot        ──►  delivery     (лише тип події та DeliveryOutcome)
volunteer  ──►  delivery     (лише тип події та DeliveryOutcome)
```

Правила:

- `delivery` не імпортує `lot` і `volunteer`; `lot` і `volunteer` не імпортують один одного — інакше виникає цикл модулів.
- У `common` немає підпакетів: клас, яким користуються інші модулі, лежить прямо в `common`.
- Прямі виклики між бізнес-модулями замінюються подією.
- Компоненти взаємодіють через інтерфейси; залежності впроваджуються лише через конструктор.

## 4. Матриця переходів станів лоту

`✓` — перехід дозволений. Порожня клітинка — заборонений. Джерело істини — метод `LotStatus.canTransitionTo`; перевірку виконує `LotStatusChanger` (єдине місце в проєкті). Недозволений перехід дає `InvalidLotStateException` → HTTP 422 у форматі `ProblemDetail`.

| з \ до | DRAFT | PENDING_MODERATION | PUBLISHED | RESERVED | PICKED_UP | DELIVERED | CONFIRMED | DISPUTED | EXPIRED | CANCELLED | FAILED |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **DRAFT** |  | ✓ | ✓ |  |  |  |  |  |  | ✓ |  |
| **PENDING_MODERATION** | ✓ |  | ✓ |  |  |  |  |  |  | ✓ |  |
| **PUBLISHED** |  |  |  | ✓ |  |  |  |  | ✓ | ✓ |  |
| **RESERVED** |  |  | ✓ |  | ✓ |  |  |  |  |  |  |
| **PICKED_UP** |  |  |  |  |  | ✓ |  |  |  |  | ✓ |
| **DELIVERED** |  |  |  |  |  |  | ✓ | ✓ |  |  |  |
| **CONFIRMED** |  |  |  |  |  |  |  |  |  |  |  |
| **DISPUTED** |  |  |  |  |  |  |  |  |  |  |  |
| **EXPIRED** |  |  |  |  |  |  |  |  |  |  |  |
| **CANCELLED** |  |  |  |  |  |  |  |  |  |  |  |
| **FAILED** |  |  |  |  |  |  |  |  |  |  |  |

`CONFIRMED`, `DISPUTED`, `EXPIRED`, `CANCELLED`, `FAILED` — кінцеві стани. `EXPIRED` і `FAILED` у матриці є, але жоден сервіс їх зараз не виставляє.

## 5. Бізнес-правила

HTTP-статуси помилок: **404** — не знайдено, **409** — дублікат або конфлікт, **422** — порушення бізнес-правила чи недозволений перехід, **403** — дію заборонено. Усі помилки повертаються як `ProblemDetail`.

### 5.1. БК-1: публікація лоту (модуль `lot`)

| # | Правило | Виняток | HTTP |
|---|---------|---------|------|
| 1.1 | `pickupTo` пізніше за `pickupFrom`, `pickupFrom` у майбутньому, вікно не коротше за мінімум для категорії (стратегія `PickupWindowStrategy`). Діє і при створенні, і при оновленні | `InvalidPickupWindowException` | 422 |
| 1.2 | Оновлення (`PUT`) дозволене лише для лота в стані `DRAFT` | `LotNotDraftException` | 409 |
| 1.3 | Публікація: `DRAFT` → `PUBLISHED` і виставляється `publishedAt`. Якщо в донора не менше 5 лотів і скасовано понад 20%, лот іде в `PENDING_MODERATION`, а `publishedAt` не виставляється | `InvalidLotStateException` | 422 |
| 1.4 | Скасування дозволене зі станів `DRAFT`, `PENDING_MODERATION`, `PUBLISHED`. З `RESERVED` і далі заборонене | `InvalidLotStateException` | 422 |
| | Лот не існує | `LotNotFoundException` | 404 |

Рішення модератора (`approve`): `approved = true` веде в `PUBLISHED` і виставляє `publishedAt`, `approved = false` повертає лот у `DRAFT`. Недозволений перехід за матрицею дає `InvalidLotStateException` (422).

Поріг скасувань рахується лише за лотами того самого донора: 1 скасований з 5 це рівно 20%, тому лот публікується, а 2 з 5 це вже модерація. Менше 5 лотів у донора скасування не враховуються взагалі.

Статистика донора (`DonorStatsService.recordOutcome`) не має власних ендпоінтів: лічильники `confirmedLots` і `disputedLots` оновлюються після події про завершену доставку (розділ 7).

### 5.2. БК-2: волонтери та резервування (модуль `volunteer`)

| # | Правило | Виняток | HTTP |
|---|---------|---------|------|
| 2.1 | Волонтера з вказаним `volunteerId` не знайдено | `VolunteerNotFoundException` | 404 |
| 2.2 | Резервування дозволене лише для лота у стані `PUBLISHED`. У разі успіху виставляються `reservedByVolunteerId` та `reservedUntil = now + 30 хв` | `InvalidLotStateException` | 422 |
| 2.3 | Обмежений волонтер (`RESTRICTED`) має доступ лише до категорій `BAKERY` та `GROCERY` (стратегія `ReservationAccessStrategy`) | `ReservationDeniedException` | 403 |
| 2.4 | Великий лот (≥ 20 кг) перші 10 хвилин з моменту публікації доступний тільки волонтерам рівнів `TRUSTED` | `ReservationDeniedException` | 403 |
| 2.5 | Скасування резервації дозволене лише для лота у стані `RESERVED`. Лот повертається в `PUBLISHED`, а поля резервації очищуються | `InvalidLotStateException` | 422 |
| | Лот не існує при спробі резервування | `LotNotFoundException` | 404 |

### 5.3. БК-3: передача, доставка, підтвердження (модуль `delivery`)

| # | Правило | Виняток | HTTP |
|---|---------|---------|------|
| 3.1 | `pickup`: перехід `RESERVED` → `PICKED_UP`; ознака `latePickup` фіксується, якщо поточний час пізніше `reservedUntil` | `InvalidLotStateException` | 422 |
| 3.2 | `deliver`: перехід `PICKED_UP` → `DELIVERED`; пункт призначення має приймати категорію лоту; після успіху генерується 6-значний код підтвердження | `InvalidLotStateException`, `CategoryNotAcceptedException` | 422 |
| 3.3 | `confirm`: код має збігатися; якщо відхилення ваги перевищує допуск стратегії — `DISPUTED`, інакше `CONFIRMED`; після збереження публікується `DeliveryFinishedEvent` | `InvalidLotStateException`, `InvalidConfirmationCodeException` | 422 |
| 3.4 | Назва пункту призначення має бути унікальною | `DuplicateDestinationPointException` | 409 |
| | Пункт призначення не існує | `DestinationPointNotFoundException` | 404 |
| | Запис доставки для лоту не існує | `DeliveryNotFoundException` | 404 |

## 6. Стратегії

Варіативна логіка винесена в стратегії: кожна реалізація — окремий `@Component`, сервіс отримує `List<…>` у конструкторі й обирає реалізацію за ключем.

| Стратегія | Модуль | Ключ | Значення за реалізаціями |
|-----------|--------|------|--------------------------|
| `PickupWindowStrategy` | `lot` | категорія | `PREPARED_MEAL` 45 хв, `BAKERY` 60 хв, `VEGETABLES` 90 хв, `GROCERY` 120 хв (мінімальне вікно самовивозу) |
| `ReservationAccessStrategy` | `volunteer` | рівень волонтера (`VolunteerTier`) | `TRUSTED` (без обмежень), `STANDARD` (блокування лотів ≥ 20 кг у перші 10 хв публікації), `RESTRICTED` (лише `BAKERY`/`GROCERY` + блокування великих лотів у перші 10 хв) |
| `WeightToleranceStrategy` | `delivery` | категорія | `PREPARED_MEAL` 10%, `BAKERY` 10%, `VEGETABLES` 15%, `GROCERY` 5% (максимальне допустиме відхилення ваги) |

## 7. Події

Подія `DeliveryFinishedEvent` лежить у базовому пакеті модуля `delivery`:

| Поле | Тип |
|------|-----|
| `lotId` | `UUID` |
| `donorOrgId` | `UUID` |
| `volunteerId` | `UUID` |
| `outcome` | `DeliveryOutcome` (`CONFIRMED`, `DISPUTED`) |
| `latePickup` | `boolean` |

| Роль | Хто | Що робить |
|------|-----|-----------|
| Видавець | `DeliveryService.confirm` | Після успішного збереження лоту й доставки публікує `DeliveryFinishedEvent` через `ApplicationEventPublisher`; метод `confirm` є транзакційним |
| Слухач | `lot` | `DonorStatsListener` асинхронно приймає подію і викликає `DonorStatsService.recordOutcome`: для `CONFIRMED` збільшує `confirmedLots` донора, для `DISPUTED` збільшує `disputedLots` |
| Слухач | `volunteer` | `VolunteerStatsListener` асинхронно приймає подію (`@ApplicationModuleListener`) та викликає `VolunteerService.recordOutcome`: для `CONFIRMED` збільшує `completedDeliveries`, для `DISPUTED` збільшує `noShows`, при `latePickup = true` збільшує `latePickups` |
