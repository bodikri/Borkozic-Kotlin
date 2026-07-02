# Plugin Integration Plan: Location Share → Borkozic Core

**Начало:** 2026-05-28 10:40  
**Завършено:** 2026-05-28 12:10  
**Цел:** Интегриране на `borkozik-plugin-locationshare3d` в основното Kotlin приложение и премахване на plugin инфраструктурата.

---

## Стъпка 1: Java → Kotlin конвертиране ✅ ЗАВЪРШЕНА
- ✅ `Situation.java` → вече съществува в borkoziclib `Situation.kt` — запазен
- ✅ `SharingService.java` → `com.borkozic.location.share.SharingService.kt` (нов)
- ✅ `SituationList.java` → `com.borkozic.location.share.SituationListActivity.kt` (нов)
- ✅ `Geo.java` → вече съществува в borkoziclib `Geo.kt`
- ✅ `StringFormatter.java` → вече съществува в borkoziclib `StringFormatter.kt`
- ✅ `PreferencesFragment.java` → интегриран в `Preferences.kt` (Стъпка 2)
- ✅ `Preferences.java` → ❌ изтрит (ненужен)
- ✅ `Executor.java` → ❌ изтрит (ненужен)
- ✅ `PreferencesHelpDialog.java` → не е нужен отделен клас (summary logic в BasePreferenceFragment)

## Стъпка 2: Preferences интеграция ✅ ЗАВЪРШЕНА
- ✅ Нов `LocationSharingPreferencesFragment : BasePreferenceFragment()`
- ✅ Нов XML: `res/xml/pref_sharing.xml` с AndroidX-съвместими елементи
- ✅ `anasthase.SeekBarPreference` → borkoziclib `com.borkozic.ui.SeekbarPreference`
- ✅ `kizitonwose.ColorPreference` → borkoziclib `com.borkozic.ui.ColorPreference`
- ✅ `PluginsPreferencesFragment` → изтрит, заменен от `LocationSharingPreferencesFragment`
- ✅ `preferences.xml`: pref_plugins → pref_sharing
- ✅ `usertag`/`usertagwithalpha` вече са в colors.xml

## Стъпка 3: SituationList интеграция ✅ ЗАВЪРШЕНА
- ✅ `SituationListActivity` (AppCompatActivity) — Kotlin
- ✅ `options_menu.xml`: `menuSituationList` в `menuLocation` submenu
- ✅ `MapActivity.kt`: handler за `R.id.menuSituationList`
- ✅ `situation_list.xml` меню със Switch
- ✅ `AndroidManifest.xml`: регистриран

## Стъпка 4: SharingService интеграция ✅ ЗАВЪРШЕНА
- ✅ Нов пакет: `com.borkozic.location.share`
- ✅ Директен достъп до `Borkozic.getLocationAsLocation()`
- ✅ Директен достъп до `addMapObject()`/`removeMapObject()`/`clearMapObjects()`
- ✅ Без AIDL (ILocationRemoteService, ILocationCallback премахнати)
- ✅ Apache HttpClient → HttpURLConnection
- ✅ Абстрактен `LocationShareClient` интерфейс за бъдещи backend-и
- ✅ Preferences достъп директно през `PreferenceManager.getDefaultSharedPreferences()`

## Стъпка 5: Почистване на plugin инфраструктура ✅ ЗАВЪРШЕНА
- ✅ `Borkozic.kt`: pluginPreferences, pluginViews, initializePlugins(), getPluginsPreferences/Views изтрити
- ✅ `Borkozic.kt`: FINALIZE broadcast изтрит
- ✅ `MapActivity.kt`: динамично добавяне на plugins в onCreateOptionsMenu() изтрито
- ✅ `Splash.kt`: initializePlugins() call изтрит
- ✅ `Preferences.kt`: PluginsPreferencesFragment изтрит
- ✅ `AndroidManifest.xml`: стари plugin entries коментирани, нови добавени
- ✅ `plugin/` директория (stubs) изтрита

## Стъпка 6: Компилация ✅ BUILD SUCCESSFUL
- ✅ `compile_wsl.sh` → BUILD SUCCESSFUL

---

### Нови файлове (създадени):
| Файл | Описание |
|---|---|
| `location/share/SharingService.kt` | Foreground service, direct integration |
| `location/share/SituationListActivity.kt` | AppCompatActivity list UI |
| `location/share/LocationShareClient.kt` | Abstract backend interface + Androzic impl |
| `res/xml/pref_sharing.xml` | Location Sharing preferences |
| `res/menu/situation_list.xml` | Menu with Switch for SituationList |

### Изтрити файлове:
- `plugin/locationshare/Executor.kt`, `PreferencesFragment.kt`, `PreferencesHelpDialog.kt`, `SharingPreferences.kt`, `SharingService.kt`, `SituationList.kt` (stubs)
- `plugin/` директория

---

## Референции
- Стар Python backend: `D:\Borkozic_Versions\LocationShare\androzic.com\uwsgi\loc.py`
- Оригинален plugin: `D:\Borkozic_Versions\borkozik-plugin-locationshare3d`
- Целеви проект: `D:\Borkozic_Versions\Borkozic-Kotlin_Oki`
