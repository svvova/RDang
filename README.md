# RDang

Плагин для Minecraft (Paper 1.17 – 26.1.2 / 1.21.4), который автоматически спавнит процедурные данжи из WorldEdit-схем (`.schem`) в открытом мире. Данжи защищаются WorldGuard-регионами, содержат шалкеры с лутом и требуют ключ для открытия.

---

## Возможности

- **Автоматический и ручной спавн данжей** из `.schem`-файлов
- **Привязка схем к биомам** — каждая схема спавнится только в подходящем биоме
- **Система ключей** — шалкеры открываются только при наличии ключа
- **Компас данжа** — указывает направление на ближайший данж (ПКМ с кулдауном)
- **WorldGuard-регионы** — автоматическое создание и удаление региона для каждого данжа
- **CoreProtect-интеграция** — данжи не спавнятся в зонах с постройками игроков
- **Автоудаление** — данж удаляется через заданное время после открытия всех шалкеров
- **Автоспавн по таймеру** — периодический спавн новых данжей без вмешательства администратора
- **Восстановление ландшафта** (`undo`) — полное удаление данжа с откатом блоков
- **GUI-меню** для управления данжами
- **Авто-обновление** с GitHub
- **bStats-метрики**

---

## Зависимости

| Зависимость | Тип | Ссылка |
|---|---|---|
| **WorldEdit** | Обязательная | [enginehub.org](https://enginehub.org/worldedit) |
| **WorldGuard** | Обязательная | [enginehub.org](https://enginehub.org/worldguard) |
| **CoreProtect** | Необязательная | [spigotmc.org](https://www.spigotmc.org/resources/coreprotect.8631/) |

---

## Установка

1. Скачайте последний релиз с [GitHub Releases](https://github.com/JamCorporation/RDang/releases)
2. Поместите `.jar` в папку `plugins/`
3. Убедитесь, что установлены **WorldEdit** и **WorldGuard**
4. Запустите сервер — плагин создаст папку `plugins/RDang/`
5. Поместите `.schem`-файлы в `plugins/RDang/schem/`
6. Зарегистрируйте схемы в `world.yml`
7. Перезагрузите плагин: `/rdang reload`

---

## Сборка

Проект поддерживает сборку через **Maven** и **Gradle**.

### Maven

```bash
mvn clean package
```

Готовый JAR будет находиться в `target/Rdang-1.21.4.jar`.

### Gradle

```bash
./gradlew build        # Linux / macOS
gradlew.bat build      # Windows
```

Готовый JAR будет находиться в `build/libs/Rdang-1.21.4.jar`.

> Для Gradle в `gradle.properties` указан путь к JDK 21. Если на вашей машине JDK 21 находится в другом месте, измените `org.gradle.java.home`.

---

## Команды

Основной префикс: `/rdang`  
Алиасы: `/rd`, `/dang`, `/bdang`, `/adang`, `/edang`, `/holydang`

### Основные

| Команда | Описание | Право |
|---|---|---|
| `/rdang menu` | Открыть GUI-меню управления данжами | `rdang.menu` |
| `/rdang spawn self` | Заспавнить данж в радиусе 30–150 блоков от себя | `rdang.spawn` |
| `/rdang spawn <количество>` | Заспавнить N данжей в текущем мире | `rdang.spawn` |
| `/rdang spawn <количество> <мир>` | Заспавнить N данжей в указанном мире | `rdang.spawn` |
| `/rdang give key <ник> [количество]` | Выдать ключ от данжа игроку | `rdang.give.key` |
| `/rdang give compass <ник> [количество]` | Выдать компас данжа игроку | `rdang.give.compass` |
| `/rdang schem <название>` | Вставить схему рядом с собой | `rdang.schem` |
| `/rdang undo <id>` | Удалить данж по ID региона и восстановить ландшафт | `rdang.undo` |
| `/rdang reload` | Перезагрузить все конфиги плагина | `rdang.reload` |
| `/rdang update` | Проверить и скачать обновление с GitHub | `rdang.update` |
| `/rdang migrate` | Мигрировать YML-файлы под текущую версию | `rdang.migrate` |
| `/rdang autospawn on` | Включить авто-спавн данжей | `rdang.autospawn` |
| `/rdang autospawn off` | Выключить авто-спавн данжей | `rdang.autospawn` |
| `/rdang autospawn interval <время>` | Изменить интервал авто-спавна | `rdang.autospawn` |
| `/rdang autospawn status` | Показать статус авто-спавна и время до следующего | `rdang.autospawn` |

### Административные

| Команда | Описание | Право |
|---|---|---|
| `/rdang admins test` | Зарегистрировать шалкер и заполнить его лутом (нужно смотреть на шалкер) | `rdang.admins` |
| `/rdang admins remove` | Удалить шалкер из базы (нужно смотреть на шалкер) | `rdang.admins` |
| `/rdang admins loot` | Заполнить шалкер лутом без регистрации | `rdang.admins` |

### Примеры

```
/rdang spawn self
  → Спавнит 1 данж в радиусе 30–150 блоков от вас.

/rdang spawn 5
  → Спавнит 5 данжей в случайных точках текущего мира.

/rdang spawn 3 world_nether
  → Спавнит 3 данжа в Незере.

/rdang give key Truhot 2
  → Выдаёт 2 ключа игроку Truhot.

/rdang undo dang_42
  → Удаляет данж с регионом dang_42 и восстанавливает ландшафт.
```

---

## Права (Permissions)

| Право | Описание | По умолчанию |
|---|---|---|
| `rdang.*` | Включает `rdang.admin` | `false` |
| `rdang.admin` | Все права плагина | `op` |
| `rdang.use` | Базовый доступ к команде `/rdang` | `op` |
| `rdang.spawn` | Спавн данжей | `op` |
| `rdang.menu` | Открытие GUI-меню | `op` |
| `rdang.give.key` | Выдача ключей | `op` |
| `rdang.give.compass` | Выдача компасов | `op` |
| `rdang.schem` | Вставка схемы вручную | `op` |
| `rdang.undo` | Удаление данжа с откатом | `op` |
| `rdang.reload` | Перезагрузка конфигов | `op` |
| `rdang.update` | Обновление плагина | `op` |
| `rdang.migrate` | Миграция YML-файлов | `op` |
| `rdang.autospawn` | Управление авто-спавном данжей | `op` |
| `rdang.admins` | Работа с шалкерами вручную | `op` |
| `rdang.additem` | Добавление предметов | `op` |
| `rdang.list` | Просмотр списка данжей | `op` |

> `rdang.admin` автоматически включает все перечисленные права.

---

## Структура файлов

```
plugins/RDang/
├── config.yml        — Основные настройки
├── auto.yml          — Автоудаление и автоспавн
├── world.yml         — Список схем, привязка к биомам и мирам
├── region.yml        — Размер и флаги WorldGuard-регионов
├── schem.yml         — Настройки вставки схем, высоты по мирам
├── items.yml         — Настройки ключа и компаса
├── shulker.yml       — Частицы и звуки для шалкеров
├── messages.yml      — Все сообщения плагина
├── schem/            — Папка для .schem файлов
├── data/             — Данные активных данжей
└── backups/          — Резервные копии для отката (undo)
```

---

## Конфигурация

### config.yml

```yaml
settings:
  metrics: false        # Включить bStats-метрики
  update-check: false   # Проверять обновления при старте
  need-key: true        # Требовать ключ для открытия шалкеров
```

### auto.yml

```yaml
auto:
  enabled: true         # Автоудаление данжей после открытия всех шалкеров
  time: "10s"           # Время до удаления. Форматы: "1m 30s", "1h", "1d"

  spawn:
    enabled: false      # Автоспавн данжей по таймеру
    interval: "1h"      # Интервал. Примеры: "30m", "2h", "1d"
    worlds: []          # Миры для спавна. Пусто = все миры со схемами
```

### world.yml

Здесь регистрируются схемы. Каждый данж привязывается к биомам и миру:

```yaml
spawn:
  minx: -2000
  maxx: 2000
  minz: -2000
  maxz: 2000

dang:
  1:
    fileName: dungeon1.schem
    biome: plains;sunflower_plains;savanna
    world: world
  2:
    fileName: nether_dang.schem
    biome: nether_wastes;crimson_forest
    world: world_nether
```

### region.yml

```yaml
region:
  size:
    x: 24               # Ширина региона (запад-восток)
    z: 24               # Длина региона (север-юг)
  name_format: "dang_{id}"
  height:
    min: 0
    max: 255
  flags:                # WorldGuard флаги региона
    use: "allow"
    block-break: "deny"
    build: "deny"
    pvp: "allow"
    # ... и другие

check:
  distance-dangs: 100   # Минимальное расстояние между данжами
  check_other_regions: true
  max-dungeons: 3       # Макс. активных данжей (0 = без ограничений)
```

### schem.yml

```yaml
ignore-air-blocks: true          # Игнорировать воздух из схемы
clear-vegetation: true           # Удалять растительность перед вставкой
clear-area-before-paste: true    # Очищать объём перед вставкой

schem-offset:
  x: 0
  y: 1
  z: 0

world-heights:
  normal:
    type: normal
    min: 60
    max: 256
  nether:
    type: nether
    min: 32
    max: 100
  end:
    type: the_end
    min: 40
    max: 80

# Настройки для конкретных схем (перекрывают глобальные):
schematics:
  dungeon_cave.schem:
    ignore-air-blocks: false
    clear-area-before-paste: false
```

---

## Примечания

- Если установлен **CoreProtect**, данжи не будут спавниться в зонах с постройками игроков.
- Схемы `.schem` должны находиться в `plugins/RDang/schem/`.
- Команда `/rdang undo <id>` принимает ID региона в формате `dang_<число>`.
- Формат времени поддерживает комбинации: `10s`, `1m 30s`, `2h`, `1d`.
- При превышении `max-dungeons` самый старый данж автоматически удаляется.

---

## Автор

**Truhot** — [https://truhott.github.io/RDang-site/](https://truhott.github.io/RDang-site/index.html)
