# Food Rescue

## 1. Що це за проєкт

Food Rescue — REST API платформи, що допомагає не викидати їжу. Донор публікує лот із залишками їжі, волонтер його резервує й забирає, а доставляє в пункт призначення, де отримання підтверджують кодом. Дані зберігаються в базі H2 (в пам'яті) через Spring Data JPA. Планувальника поки що немає.

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

`✓` — перехід дозволений. Порожня клітинка — заборонений. Джерело істини — метод `LotStatus.canTransitionTo`; перевірку виконує `LotStatusChanger` (єдине місце в проєкті). Недозволений перехід дає `InvalidLotStateException` → HTTP 409 у форматі `ProblemDetail`.

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

HTTP-статуси помилок: **404** — не знайдено, **409** — дублікат, конфлікт або недозволений перехід стану, **422** — порушення бізнес-правила, **403** — дію заборонено. Усі помилки повертаються як `ProblemDetail`.

### 5.1. БК-1: публікація лоту (модуль `lot`)

| # | Правило | Виняток | HTTP |
|---|---------|---------|------|
| 1.1 | `pickupTo` пізніше за `pickupFrom`, `pickupFrom` у майбутньому, вікно не коротше за мінімум для категорії (стратегія `PickupWindowStrategy`). Діє і при створенні, і при оновленні | `InvalidPickupWindowException` | 422 |
| 1.2 | Оновлення (`PUT`) дозволене лише для лота в стані `DRAFT`. Позиції лота при цьому замінюються цілком | `LotNotDraftException` | 409 |
| 1.3 | Публікація: `DRAFT` → `PUBLISHED` і виставляється `publishedAt`. Якщо в донора не менше 5 лотів і скасовано понад 20%, лот іде в `PENDING_MODERATION`, а `publishedAt` не виставляється | `InvalidLotStateException` | 409 |
| 1.4 | Скасування дозволене зі станів `DRAFT`, `PENDING_MODERATION`, `PUBLISHED`. З `RESERVED` і далі заборонене | `InvalidLotStateException` | 409 |
| 1.5 | Видалення лота (`DELETE`) і будь-яка зміна його позицій (додати, змінити, видалити) дозволені лише в стані `DRAFT` | `LotNotDraftException` | 409 |
| | Лот не існує | `LotNotFoundException` | 404 |
| | Позиція не існує в цьому лоті (зокрема належить іншому лоту) | `FoodItemNotFoundException` | 404 |

Рішення модератора (`approve`): `approved = true` веде в `PUBLISHED` і виставляє `publishedAt`, `approved = false` повертає лот у `DRAFT`. Недозволений перехід за матрицею дає `InvalidLotStateException` (409).

Поріг скасувань рахується лише за лотами того самого донора: 1 скасований з 5 це рівно 20%, тому лот публікується, а 2 з 5 це вже модерація. Менше 5 лотів у донора скасування не враховуються взагалі.

Статистика донора (`DonorStatsService.recordOutcome`) не має власних ендпоінтів: лічильники `confirmedLots` і `disputedLots` оновлюються після події про завершену доставку (розділ 7) атомарним `UPDATE ... SET x = x + 1`. Якщо запису донора ще немає, він створюється і `UPDATE` повторюється.

### 5.2. БК-2: волонтери та резервування (модуль `volunteer`)

| # | Правило | Виняток | HTTP |
|---|---------|---------|------|
| 2.1 | Реєстрація: волонтер із таким email уже існує | `DuplicateVolunteerException` | 409 |
| 2.2 | Волонтера не знайдено: за `id` у шляху (`/volunteers/{id}`) або за профілем користувача, що увійшов (`CurrentUser.profileId()`) | `VolunteerNotFoundException` | 404 |
| 2.3 | Резервування дозволене лише для лота у стані `PUBLISHED`. У разі успіху виставляються `reservedByVolunteerId` (профіль користувача, що увійшов; тіла запит не має) та `reservedUntil = now + 30 хв` | `InvalidLotStateException` | 409 |
| 2.4 | Обмежений волонтер (`RESTRICTED`) має доступ лише до категорій `BAKERY` та `GROCERY` (стратегія `ReservationAccessStrategy`) | `ReservationDeniedException` | 403 |
| 2.5 | Великий лот (≥ 20 кг) перші 10 хвилин з моменту публікації доступний тільки волонтерам рівнів `TRUSTED` | `ReservationDeniedException` | 403 |
| 2.6 | Скасування резервації дозволене лише для лота у стані `RESERVED`. Лот повертається в `PUBLISHED`, а поля резервації очищуються | `InvalidLotStateException` | 409 |
| 2.7 | Оновлення волонтера (`PUT`): email має бути унікальним серед інших користувачів (`existsByEmailAndIdNot`) | `DuplicateVolunteerException` | 409 |
| 2.8 | Видалення волонтера (`DELETE`): заборонене, якщо у волонтера є активні лоти зі статусом `RESERVED` або `PICKED_UP` | `VolunteerHasActiveLotsException` | 409 |
| 2.9 | Бажані пункти призначення: додавання/видалення/перегляд прив'язки до `DestinationPoint`. Якщо волонтер або пункт не знайдено | `VolunteerNotFoundException`, `NotFoundException` | 404 |
| 2.10 | Резервувати і скасовувати може лише користувач, пов'язаний із профілем волонтера; скасувати резерв може лише той волонтер, який його зробив | `ForbiddenActionException` | 403 |
| 2.11 | Рівень волонтера (`VolunteerTier`). Показник відповідальності `score = %успішних − 2 × запізнення − 10 × зриви` у межах 0–100, де `%успішних = completed / (completed + noShows) × 100` (без жодної доставки 100). `RESTRICTED`, якщо зривів більше 3 або запізнень більше 15% від `completed + noShows`; інакше `TRUSTED` при `score > 90`, решта `STANDARD` | — | — |
| | Лот не існує при спробі резервування | `LotNotFoundException` | 404 |

### 5.3. БК-3: передача, доставка, підтвердження (модуль `delivery`)

| # | Правило | Виняток | HTTP |
|---|---------|---------|------|
| 3.1 | `pickup`: перехід `RESERVED` → `PICKED_UP`; ознака `latePickup` фіксується, якщо поточний час пізніше `reservedUntil` | `InvalidLotStateException` | 409 |
| 3.2 | `deliver`: перехід `PICKED_UP` → `DELIVERED`; пункт призначення має приймати категорію лоту; після успіху генерується 6-значний код підтвердження | `InvalidLotStateException` (409), `CategoryNotAcceptedException` (422) | 409, 422 |
| 3.3 | `confirm`: код має збігатися; якщо відхилення ваги перевищує допуск стратегії — `DISPUTED`, інакше `CONFIRMED`; після збереження публікується `DeliveryFinishedEvent` | `InvalidLotStateException` (409), `InvalidConfirmationCodeException` (422) | 409, 422 |
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
| `GET /api/v1/lots` | `LotRepository.findAllWithItems()`: `SELECT DISTINCT l FROM FoodLot l LEFT JOIN FETCH l.items ORDER BY l.createdAt DESC` | один `select … from food_lots … left join food_items … order by created_at desc` |
| `GET /api/v1/lots?status=…` | `LotRepository.findAllByStatusWithItems(status)`: те саме з `WHERE l.status = :status` | один `select` з `left join food_items` |
| `GET /api/v1/lots/{id}` | `LotRepository.findByIdWithItems(id)` | один `select` з `left join food_items` |
| `GET /api/v1/lots/{lotId}/items` | `FoodItemRepository.findByLotId(lotId)`: позиції читаються напряму, лот не потрібен | один `select … from food_items where lot_id = ?` |
| `GET /api/v1/destination-points` | `DestinationPointRepository.findAllWithCategories()` | один `select` з `left join destination_point_categories` |
| `GET /api/v1/volunteers` | `VolunteerRepository.findAllWithPoints()`: `SELECT DISTINCT v FROM VolunteerProfile v LEFT JOIN FETCH v.preferredPoints ORDER BY v.fullName` | один `select` з `left join volunteer_points` і `destination_points` |
| `GET /api/v1/volunteers/{id}` | `VolunteerRepository.findByIdWithPoints(id)`: `SELECT DISTINCT v FROM VolunteerProfile v LEFT JOIN FETCH v.preferredPoints WHERE v.id = :id` | один `select` з `left join volunteer_points` і `destination_points` |

Без `JOIN FETCH` список із 5 лотів по 2 позиції дав би 6 запитів (1 на лоти і по одному на позиції кожного лота). Це перевіряє тест `LotRepositoryTest.findAllWithItems_loadsLotsWithItemsInSingleQuery` через статистику Hibernate (`getPrepareStatementCount() == 1`).

### 8.4. Каскад і `orphanRemoval`

Лот (`FoodLot`) і його позиції (`FoodItem`) пов'язані двостороннім зв'язком: власник зв'язку, тобто сторона з зовнішнім ключем `lot_id`, це `FoodItem.lot`, а `FoodLot.items` позначений `mappedBy = "lot"`. Обидві сторони `LAZY`.

```java
@OneToMany(mappedBy = "lot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private List<FoodItem> items = new ArrayList<>();
```

| Що робимо | Що відбувається в базі |
|-----------|------------------------|
| `lotRepository.save(lot)` для нового лота з позиціями | Каскад `PERSIST`: позиції зберігаються разом із лотом, окремий `save` для них не потрібен |
| `lot.addItem(item)` для лота, який уже є в базі | Позиція зберігається каскадом при коміті транзакції |
| `lot.removeItem(item)` | `orphanRemoval`: рядок позиції видаляється, бо вона більше не в колекції лота. Окремий `delete` не потрібен |
| `lot.replaceItems(newItems)` (це робить `PUT /lots/{id}`) | Старі позиції видаляються як «сироти», нові вставляються |
| `DELETE /lots/{id}` | Каскад `REMOVE`: разом із лотом видаляються всі його позиції |

Чим відрізняються `CascadeType.REMOVE` і `orphanRemoval`: `REMOVE` видаляє дітей, коли видаляють батька, а `orphanRemoval` видаляє дитину, щойно її прибрали з колекції батька, навіть якщо сам батько лишається.

Зв'язок змінюється лише допоміжними методами `addItem` / `removeItem` / `replaceItems`, які тримають обидві сторони узгодженими (`items.add(item)` і `item.setLot(this)`). Геттер `getItems()` повертає саму колекцію, а не копію, інакше Hibernate не побачить змін. Усе це змінюється лише в стані `DRAFT` (правило 1.5).

Каскад і `orphanRemoval` перевіряють тести `LotRepositoryTest`: `save_cascadesInsertToItems`, `removeItem_deletesOrphanRowFromDatabase`, `replaceItems_deletesOldItemsAndInsertsNewOnes`, `delete_lotRemovesAllItemsByCascade`.

### 8.5. Зв'язок many-to-many «улюблені пункти»

Волонтер (`VolunteerProfile`) може обирати улюблені пункти призначення (`DestinationPoint`) для отримання або доставки лотів. Зв'язок налаштовано як односторонній `@ManyToMany`:

```java
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
        name = "volunteer_points",
        joinColumns = @JoinColumn(name = "volunteer_id"),
        inverseJoinColumns = @JoinColumn(name = "point_id"))
private Set<DestinationPoint> preferredPoints = new HashSet<>();
```

Особливості реалізації:
- **Напрямок залежності:** `VolunteerProfile` посилається на `DestinationPoint` із модуля `delivery`. Згідно з правилами модуля Spring Modulith, залежність `volunteer ──► delivery` є дозволеною, а зворотний імпорт відсутній, що унеможливлює циклічні залежності.
- **Тип колекції `Set`:** Використання `Set<DestinationPoint>` замість `List` запобігає дублюванню однакових пунктів у списку волонтера та усуває ризик декартового добутку при джойнах.
- **Відсутність каскаду видалення:** На зв'язку навмисно немає `CascadeType.REMOVE` чи `orphanRemoval`. Видалення волонтера видаляє лише його зв'язки в таблиці `volunteer_points`, але самі фізичні пункти призначення (`DestinationPoint`) не видаляються.
- **Запобігання N+1:** Для читання списку волонтерів або одного волонтера разом із його пунктами використовуються методи `VolunteerRepository.findAllWithPoints()` та `VolunteerRepository.findByIdWithPoints(id)` із `LEFT JOIN FETCH v.preferredPoints`. Це протестовано в `VolunteerRepositoryTest.findByIdWithPoints_withPreferredPoints_loadsPointsInSingleQuery` за допомогою `Statistics.getPrepareStatementCount() == 1`.
- **Ендпоінти для взаємодії:**
    - `GET /api/v1/volunteers/{id}/preferred-points` — отримання списку улюблених пунктів волонтера;
    - `PUT /api/v1/volunteers/{id}/preferred-points/{pointId}` — додавання пункту до списку улюблених;
    - `DELETE /api/v1/volunteers/{id}/preferred-points/{pointId}` — видалення пункту зі списку улюблених.

### 8.6. Чому `Delivery.volunteerId` це `UUID`, а не зв'язок

`Delivery` належить модулю `delivery`, а профіль волонтера — модулю `volunteer`. Якби `Delivery` мав `@ManyToOne VolunteerProfile`, модуль `delivery` почав би залежати від `volunteer`, що створило б небажаний цикл залежностей між модулями. Тому `Delivery.volunteerId` лишається звичайним `UUID`.

Водночас зв'язки, які не порушують межі модулів, зроблені як JPA-відношення: `Delivery.lot` — `@OneToOne(fetch = LAZY)`, а `Delivery.destinationPoint` — `@ManyToOne(fetch = LAZY)`. Для читання доставок використовуються `JOIN FETCH`-запити, щоб лот і пункт призначення завантажувались одним SQL-запитом.

### 8.7. Конкуренція: `@Version` і атомарні `UPDATE`

Кожна сутність із `id`, який призначається в коді (`FoodLot`, `VolunteerProfile`, `DestinationPoint`, `Delivery`, `DonorStats`), має поле `@Version private Long version;`. Без нього Spring Data вважає сутність неновою і перед кожним `INSERT` робить зайвий `SELECT` (`merge`). Замість `synchronized` конкуренцію забезпечують:

1. **Оптимістичне блокування (`@Version`):**
    - Будь-яка паралельна модифікація сутності (наприклад, одночасне оновлення профілю `PUT /volunteers/{id}` або паралельне резервування) перевіряє збіг версії.
    - У разі конфлікту виникає `ObjectOptimisticLockingFailureException`, яке глобальний обробник перетворює на HTTP 409 Conflict (`ProblemDetail`).

2. **Атомарні SQL `UPDATE` для лічильників:**
    - Оновлення лічильників статистики волонтера (`completedDeliveries`, `noShows`, `latePickups`) виконується асинхронним слухачем `VolunteerStatsListener` за подією `DeliveryFinishedEvent`.
    - Замість `synchronized` у методі сервісу (який відпускає монітор до фіксації транзакції в БД і не рятує від гонки в базі) та замість циклу read-modify-write (який би генерував конфлікти оптимістичного блокування при паралельних подіях) використовуються прямі SQL-запити на рівні БД:
   ```java
   @Modifying
   @Query("UPDATE VolunteerProfile v SET v.completedDeliveries = v.completedDeliveries + 1 WHERE v.id = :id")
   int incrementCompleted(@Param("id") UUID id);
   ```
    - Завдяки цьому лічильники оновлюються атомарно всередині однієї операції в базі даних без блокування сутності в пам'яті.

## 9. Автоконфігурація

`ObservabilityAutoConfiguration` за замовчуванням реєструє `TraceIdFilter`, який додає `X-Trace-Id` до відповіді та MDC кожного HTTP-запиту. Фільтр можна вимкнути властивістю `foodrescue.observability.trace-id-enabled=false`, а назву заголовка змінити через `foodrescue.observability.trace-header`. Профілі запускаються аргументом `--spring.profiles.active=dev` або `--spring.profiles.active=prod`: `dev` вмикає DEBUG для проєкту й показ SQL, а `prod` лишає кореневий рівень INFO. Автоконфігурація перевіряється через `ApplicationContextRunner` для значень `true`, `false` і властивості за замовчуванням. Під час аудиту модуля `lot` блоків `catch`, що ковтають винятки, не знайдено.

## 10. Логування і маскування

Конфігурація логування: `src/main/resources/logback-spring.xml`. Поточний файл журналу: `logs/foodrescue.log`. При досягненні 10 MB або опівночі файл ротується і стискається до `logs/archived/foodrescue-%d{yyyy-MM-dd}.%i.log.gz`. Архіви зберігаються за останні 14 днів, а їхній загальний обсяг не перевищує 200 MB. Кожен рядок журналу має поле `[%X{traceId:-}]`; під час HTTP-запитів його заповнює `TraceIdFilter`.

Маскування персональних даних (клас `SensitiveDataMasker` у модулі `common`):
- **Email волонтера** — персональні дані (GDPR-чутливі). У журналі записується у вигляді `v***@example.com` замість `volunteer@example.com`. Застосовується при реєстрації: `log.info("Зареєстровано волонтера {} з email {}", id, SensitiveDataMasker.maskEmail(email))`.
- **6-значний код підтвердження** доставки — одноразовий секрет, що діє як пароль доступу. Маскується в `DeliveryServiceImpl` при помилковому введенні: `*****0` замість `482910`.

Аудит блоків `catch` у модулі `volunteer`: блоків `catch`, що ковтають або приховують виняток, не знайдено (`grep -rn "catch" src/main/java/com/example/foodrescue/volunteer` — 0 результатів). Усі помилки обробляються через типізовані бізнес-винятки.


## 11. OpenAPI і Postman

Swagger UI доступний за адресою `http://localhost:8080/swagger-ui/index.html`, специфікація — `http://localhost:8080/v3/api-docs`; у `DestinationPointRequest.name` додано приклад `Їдальня "Тепла хата"`.
Колекція `postman/food-rescue-collection.json` та оточення `postman/food-rescue-environment.json` виконують один сценарій із дев'яти кроків: вхід донором → пункт → волонтер → лот → публікація → резервування → pickup → delivery → confirm.
Після запуску застосунку (`./mvnw spring-boot:run`, Java 25) виконайте команду нижче за наявності Node.js/npm: колекція перевіряє точні HTTP-статуси (створення та резервування — 201, інші кроки — 200), `X-Trace-Id` та кінцевий `lotStatus: "CONFIRMED"`.
ID беруться з `Location`, код підтвердження — з відповіді доставки, а майбутні дати, унікальна назва пункту та email генеруються перед першим запитом; усі дев'ять кроків слід запускати послідовно з увімкненим `TraceIdFilter` і стандартним заголовком `X-Trace-Id`.
Аудит пакета `delivery`: блоків `catch` і викликів `System.out` не знайдено, типізовані винятки передаються глобальному обробнику; невірний код у новому параметризованому `log.warn` проходить через `SensitiveDataMasker.maskCode`.

```bash
npx newman run postman/food-rescue-collection.json -e postman/food-rescue-environment.json
```


## 12. Безпека

REST API під `/api/**` захищений Spring Security зі stateless JWT: сесій немає (`SessionCreationPolicy.STATELESS`), тому CSRF вимкнено, бо API не працює з браузерними сесіями. Вхід: `POST /api/v1/auth/login` з тілом `{"username": "...", "password": "..."}`, у відповіді `accessToken`, який передається в заголовку `Authorization: Bearer <token>`. Токен живе `security.jwt.expiration-minutes` (15 хвилин), секрет обов'язково задається через змінну середовища `JWT_SECRET` або файл `.env` у корені проєкту (скопіюйте `.env.example` у `.env` та замініть приклад на власний випадковий ключ довжиною щонайменше 32 байти; `.env` у gitignore). Без секрету застосунок не стартує. Токен вимкненого користувача відхиляється з 401.
Паролі зберігаються як хеші Argon2id. Помилки 401 і 403 повертаються у форматі `ProblemDetail` з `instance`. Публічні лише логін, Swagger/OpenAPI та `actuator/health`; CORS дозволено для `http://localhost:3000` і `http://localhost:5173`.
Ролі: `DONOR`, `VOLUNTEER`, `COORDINATOR`, `ADMIN`. У модулі `lot` створення, оновлення, видалення, публікація й скасування лотів та зміна позицій дозволені лише `DONOR`, схвалення лоту лише `ADMIN`, читання будь-якому автентифікованому користувачу (`@PreAuthorize` на `LotService` і `LotItemService`). У модулі `volunteer` резервування лоту й скасування резервування дозволені лише `VOLUNTEER` (`@PreAuthorize` на `VolunteerService`), решта методів вимагає будь-якого валідного токена.
У модулі `delivery` роль `COORDINATOR` потрібна для створення, оновлення та видалення пунктів призначення; роль `VOLUNTEER` — для передачі лоту, доставки й підтвердження отримання. Перегляд пунктів, доставок та історії доступний усім автентифікованим користувачам. Перевірка ролей виконується через `@PreAuthorize` на методах `DeliveryService`, без змін бізнес-логіки та маршрутів.
Демо-користувачі створюються при старті: `donor1`, `volunteer1`, `coordinator1`, `admin1`. Паролі задаються в налаштуваннях `foodrescue.demo.passwords.<логін>` (`application.properties`), у коді їх немає; для Postman вони лежать у змінних середовища `donorPassword`, `volunteerPassword`, `coordinatorPassword` (`postman/food-rescue-environment.json`). Для Swagger UI є схема `BearerAuth`.


### 12.1. Вебінтерфейс

Сторінки на Thymeleaf працюють під окремим ланцюгом безпеки: вхід через форму `/login`, автентифікація в сесії, CSRF увімкнено (усі форми з `th:action`, вихід лише POST-запитом). REST під `/api/**` лишається на JWT, CSRF там вимкнено, бо токен передається заголовком, а не кукою.
Адреси: `/login` (вхід), `/lots` (список лотів, бачать усі залогінені), `/lots/new` і `POST /lots` (форма створення лота, лише `DONOR`; кнопка «Додати лот» показується лише донору через `sec:authorize`, а права перевіряє `@PreAuthorize` на `LotService`). Сторінки `403` і `404` мають власні шаблони в `templates/error`, для запитів із `Accept: text/html`.
Для входу підходять ті самі демо-користувачі, що й для JWT (`donor1`, `volunteer1` тощо). Стилі лежать у `static/css/app.css` і підключаються з хешем вмісту в імені файлу; спільна навігація й `head` у фрагменті `templates/fragments/layout.html`.
Сторінка `/reservations` (лише `VOLUNTEER`, вхід `volunteer1`) показує поточні резервування і форму резервування: волонтер обирає опублікований лот, а резервування записується на профіль волонтера, який увійшов (`CurrentUser`), після успіху редірект назад на список (Post-Redirect-Get), а якщо поле не вибране, форма показується знову з помилками під полями. Посилання в навігації бачить лише волонтер (`sec:authorize`), а на сервері `@PreAuthorize("hasRole('VOLUNTEER')")` стоїть і на `VolunteerService.reserve`, і на методах читання для сторінки, тому інша роль за прямою адресою отримує `403`.
Сторінки пунктів призначення: `/destination-points` (список), `/destination-points/new` (форма) і `POST /destination-points` (створення); вебінтерфейс доступний лише ролі `COORDINATOR`, зокрема демо-користувачу `coordinator1`. Форма перевіряє ID організації, назву, адресу, години роботи й категорії; після успішного створення повертає на список (Post-Redirect-Get). Захист форми забезпечують наявні сесія, CSRF і `@PreAuthorize`, REST-маршрути `/api/v1/destination-points` не змінено.
### Вебінтерфейс модуля delivery (розширення після контракту №7)

Для входу використовується `/login` і сесія браузера (CSRF увімкнений);
REST `/api/**` продовжує використовувати JWT. Вебконтролери звертаються до
`DeliveryService`, а не до REST-ендпоінтів.

- `/destination-points` — пункти призначення: перегляд, створення,
  редагування та видалення (`COORDINATOR`). Для редагування повторно
  використовується форма створення; пункт, задіяний у доставці, не видаляється.
- `/deliveries` — список зарезервованих лотів, готових до отримання,
  та наявних доставок.
- `/deliveries/{lotId}` — дані лоту й доставки, історія змін статусів,
  форми отримання (`RESERVED`), передачі (`PICKED_UP`) та підтвердження
  (`DELIVERED`). Ці дії доступні тільки ролі `VOLUNTEER`.

Валідація відображається під полями, після успішного POST використовується
Post-Redirect-Get. Усі POST-форми мають CSRF-токени Thymeleaf.
Для перегляду завантажте демо-сторінку `/deliveries` під `volunteer1`.
Спершу можна зафіксувати отримання зарезервованого демо-лоту; для передачі
до пункту призначення координатор має попередньо створити відповідний пункт.


## 13. Тестування

Для інтеграційних і end-to-end тестів має працювати Docker Desktop (перевірка: `docker info` без помилок, перший запуск тягне образ `postgres:17-alpine` і Chromium для Playwright, тому потрібен інтернет). `./mvnw test` запускає швидкі тести (`*Test`) і інтеграційні тести через MockMvc на PostgreSQL у Testcontainers, `./mvnw verify` додає end-to-end тести (`*E2EIT`: REST через `RestTestClient` і браузерні через Playwright).
Тести з Testcontainers беруть спільні налаштування з анотацій `@IntegrationTest` (MockMvc) і `@E2ETest` (запущений сервер): один контекст і один контейнер на групу. Профіль `test` задає окремий секрет JWT, паролі демо-акаунтів тести беруть із `foodrescue.demo.passwords.*`. Перед кожним тестом таблиці очищаються скриптами в `src/test/resources/sql`, тому тести не залежать від демо-даних.
У модулі `lot` інтеграційні тести покривають CRUD лотів на PostgreSQL (успішні сценарії, кожен негативний код, права ролей DONOR, VOLUNTEER, COORDINATOR і ADMIN, результат читається з таблиць), форму сторінки (CSRF, валідація, роль), `X-Trace-Id` і `CurrentUser`; end-to-end тести проходять сценарій лота через REST з токеном і через браузер від входу до рядка в списку. Звіт про тести лежить у `report-tests.md`, браузерні тести пишуть trace у `target/playwright` (відкривається командою `npx playwright show-trace <файл>`).
