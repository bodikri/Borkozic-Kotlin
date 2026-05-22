# 📖 Borkozic Documentation (Документация)

> **Last updated:** 2026-05-22
> **Kotlin migration:** ~346 .kt files, 35 Java files remaining (3rd party)
> **Compose migration:** 2/5 screens migrated (AreaList ✅, RouteList ✅)
> **Build status:** 0 compilation errors, BUILD SUCCESSFUL

---

## 🧭 Table of Contents (Съдържание)

### Architecture (Архитектура)

| Document (Документ) | Description (Описание) |
|---|---|
| [Overview](architecture/overview.md) | High-level application architecture (архитектура на приложението) |
| [Tech Stack](architecture/tech-stack.md) | Dependencies, versions, build process (зависимости, версии, компилация) |

### Components (Компоненти)

| Document (Документ) | Status (Състояние) | Description (Описание) |
|---|---|---|
| [Sliding Right Panel](components/sliding-right-panel.md) | 🔴 Legacy — planned replacement | Side panel with action buttons (страничен панел с бутони за действия) |
| Area List | 🟢 Compose | Area list screen (списък с области) |
| Route List | 🟢 Compose | Route list screen (списък с маршрути) |
| Track List | 🟡 Java → Compose planned | Track list screen (списък с тракове) |
| Waypoint List | 🟡 Java → Compose planned | Waypoint list screen (списък с точки) |
| Map Activity | 🟡 Compose planned | Main map screen (главен екран с карта) |
| Map View | 🟢 Kotlin | Custom map rendering view (изглед за рендиране на карта) |
| Navigation Service | 🟢 Kotlin | Route navigation service (услуга за навигация по маршрут) |
| Location Service | 🟢 Kotlin | GPS location service (услуга за GPS локация) |

### Migration (Миграция)

| Document (Документ) | Description (Описание) |
|---|---|
| [Java → Kotlin Migration](migration/kotlin-migration.md) | Migration history, gotchas, stats (история, уловки, статистика) |
| [Compose Migration](compose-migration/index.md) | Overview of Compose migration progress (прогрес на Compose миграцията) |
| [AreaList Compose Migration](compose-migration/arealist-migration.md) | AreaList Java→Compose details (детайли за AreaList) |
| [RouteList Compose Migration](compose-migration/routelist-migration.md) | RouteList Java→Compose details (детайли за RouteList) |

### Settings & Preferences (Настройки)

| Document (Документ) | Description (Описание) |
|---|---|
| [Settings Overview](settings/overview.md) | Preference screens and keys (екрани с настройки и ключове) |

---

## 📝 Document Format (Формат на документите)

Each component document follows this structure (всеки компонентен документ следва тази структура):

1. **Overview** — Brief description (кратко описание)
2. **Current Implementation** — How it works now (как работи сега)
3. **Technical Details** — Deep dive (технически детайли)
4. **Dependencies** — All affected files (всички засегнати файлове)
5. **Behavior** — User interaction (потребителско поведение)
6. **Settings & Configuration** — Related preferences (свързани настройки)
7. **Migration Plan** — If a rewrite is planned (ако е планирана замяна)
8. **History** — Version changelog (хронология на промените)

## 🏷️ Status Legend (Легенда на състоянията)

| Icon | Meaning (Значение) |
|---|---|
| 🟢 | Complete / migrated / stable (завършено / мигрирано / стабилно) |
| 🟡 | Planned migration (планирана миграция) |
| 🔴 | Legacy / needs replacement (старо решение / нуждае се от замяна) |
| ⚪ | Not started (не е започнато) |

## 🔗 Quick Links (Бързи връзки)

- [Repository (repo)](..)
- [MEMORY.md (дългосрочна памет)](../MEMORY.md)
- [SOUL.md (личност)](../SOUL.md)
- [Compile script](../compile_wsl.sh)

---

*Documentation maintained by Kоки & the Borkozic migration team.*
