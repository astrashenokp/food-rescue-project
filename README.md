# Food Rescue

## 1. Що це за проєкт

Food Rescue — REST API платформи, що допомагає не викидати їжу. Донор публікує лот із залишками їжі, волонтер його резервує й забирає, а доставляє в пункт призначення, де отримання підтверджують кодом. Дані зберігаються в базі H2 (в пам'яті) через Spring Data JPA. Безпеки й планувальника поки що немає.

## 2. Як запустити й перевірити

Потрібні Java 25 і Maven Wrapper (лежить у репозиторії).

```
./mvnw test                  # усі тести, серед них ModulesTest (verify() меж модулів)
./mvnw spring-boot:run       # запуск застосунку на http://localhost:8080
```

При старті застосунок сам створює в базі демо-лоти й демо-волонтера з фіксованими id (див. `DemoData` і `VolunteerDemoData`). База в пам'яті: після перезапуску вона порожня, демо-дані створюються заново.

## 3. Модулі й правила залежностей

Кожен пакет першого рівня в `com.example.foodrescue` — окремий модуль Spring Modulith. Кордони між ними перевіряє `ModulesTest` (`ApplicationModules.verify()`).

| Модуль | Що в ньому |
|--------|------------|
| `common` | Спільне ядро: лот і його статуси, лот і його позиції як JPA-сутності, репозиторії лотів, історія переходів, спільні винятки, глобальний обробник помилок, інфраструктура (`Clock`), демо-дані |
| `lot` | БК-1: публікація лоту |
| `volunteer` | БК-2: волонтери та резервування |
| `delivery` | БК-3: передача, доставка, підтвердження; публікує подію `DeliveryFinishedEvent` |

Дозволені залежності:

```
lot        ──►  common
volunteer  ──►  common
volunteer  ──►  delivery     (сутність `DestinationPoint`, її репозиторій, виняток, подія)
delivery   ──►  common
lot        ──►  delivery     (лише подія та `DeliveryOutcome`)
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
| 2.1 | Реєстрація: волонтер із таким email уже існує | `DuplicateVolunteerException` | 409 |
| 2.2 | Волонтера з вказаним `volunteerId` не знайдено | `VolunteerNotFoundException` | 404 |
| 2.3 | Резервування дозволене лише для лота у стані `PUBLISHED`. У разі успіху виставляються `reservedByVolunteerId` та `reservedUntil = now + 30 хв` | `InvalidLotStateException` | 422 |
| 2.4 | Обмежений волонтер (`RESTRICTED`) має доступ лише до категорій `BAKERY` та `GROCERY` (стратегія `ReservationAccessStrategy`) | `ReservationDeniedException` | 403 |
| 2.5 | Великий лот (≥ 20 кг) перші 10 хвилин з моменту публікації доступний тільки волонтерам рівнів `TRUSTED` | `ReservationDeniedException` | 403 |
| 2.6 | Скасування резервації дозволене лише для лота у стані `RESERVED`. Лот повертається в `PUBLISHED`, а поля резервації очищуються | `InvalidLotStateException` | 422 |
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

## 8. База даних

Дані зберігаються в H2 в пам'яті через Spring Data JPA (Hibernate). Схема створюється автоматично при старті (`ddl-auto=update`), демо-дані створює `DemoData`.

### 8.1. Схема

```
food_lots (FoodLot)  1 ─────── N  food_items (FoodItem)        cascade ALL + orphanRemoval, LAZY
food_lots (FoodLot)  1 ─────── 1  deliveries (Delivery)        унікальний lot_id, LAZY
destination_points   1 ─────── N  deliveries                    LAZY
destination_points   1 ─────── N  destination_point_categories  @ElementCollection, enum STRING
volunteers           N ─────── M  destination_points            таблиця volunteer_points (улюблені пункти)
```

| Зв'язок | Тип | Власник (FK) | Модуль | Каскад |
|---------|-----|--------------|--------|--------|
| `FoodLot` і `FoodItem` | `@OneToMany` / `@ManyToOne`, двосторонній (`mappedBy`) | `FoodItem.lot` | `common` | `ALL` і `orphanRemoval = true` |
| `Delivery` і `FoodLot` | `@OneToOne`, односторонній | `Delivery.lot` | `delivery` | немає |
| `Delivery` і `DestinationPoint` | `@ManyToOne`, односторонній | `Delivery.destinationPoint` | `delivery` | немає |
| `VolunteerProfile` і `DestinationPoint` | `@ManyToMany`, односторонній, `Set` | `VolunteerProfile.preferredPoints` | `volunteer` | немає |
| `DestinationPoint` і категорії | `@ElementCollection` (`Set<FoodCategory>`) | таблиця `destination_point_categories` | `delivery` | разом із власником |

Посилання між модулями лишаються звичайними `UUID`-полями, а не зв'язками (`FoodLot.donorOrgId`, `FoodLot.reservedByVolunteerId`, `Delivery.volunteerId`, `LotStatusHistory.lotId`, `DonorStats.donorOrgId`), щоб не порушувати межі модулів.

### 8.2. Як дивитися базу

Після `./mvnw spring-boot:run` відкрити `http://localhost:8080/h2-console` і ввести:

| Поле | Значення |
|------|----------|
| JDBC URL | `jdbc:h2:mem:foodrescue` |
| User Name | `sa` |
| Password | порожній |

SQL усіх запитів видно в журналі (`spring.jpa.show-sql=true`).

### 8.3. N+1 і де ми його прибрали

Проблема N+1: один запит за списком батьківських записів і ще по одному запиту на дочірні записи кожного з них. Усі зв'язки в проєкті `LAZY`, а списки з дітьми читаються одним запитом із `LEFT JOIN FETCH`.

| Ендпоінт | Запит у репозиторії | У журналі |
|----------|---------------------|-----------|
| _заповнюють Людини 1, 2 і 3 у своїх треках_ | | |

### 8.4. Каскад і `orphanRemoval`

_Розділ заповнює Людина 1._

### 8.5. Зв'язок many-to-many «улюблені пункти»

_Розділ заповнює Людина 2._

### 8.6. Чому `Delivery.volunteerId` це `UUID`, а не зв'язок

_Розділ заповнює Людина 3._

### 8.7. Конкуренція: `@Version` і атомарні `UPDATE`

Кожна сутність із `id`, який призначається в коді, має поле `@Version`. Без нього Spring Data вважає сутність неновою і перед кожним `INSERT` робить зайвий `SELECT` (`merge`). Замість `synchronized` конкуренцію забезпечують оптимістичне блокування (`@Version`, помилка `ObjectOptimisticLockingFailureException` дає відповідь 409) і атомарні `UPDATE` для лічильників.
