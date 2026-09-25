# AIDL FBReader (уровни L2/L3)

Каталог пуст **осознанно**. Сюда в Sprint 7 кладутся вендоренные AIDL-файлы FBReader из
открытой кодовой базы `github.com/geometer/FBReaderJ`:

```
org/geometerplus/android/fbreader/api/ApiInterface.aidl
org/geometerplus/android/fbreader/api/ApiCallback.aidl
org/geometerplus/android/fbreader/libraryService/LibraryInterface.aidl
```

## Почему их здесь нет сейчас

* Этап каркаса: AIDL-файлы — это уже интеграция уровня L2/L3, а не структура модуля.
* Перед вендорингом обязателен чек-лист Sprint 0 (docs/reader-integration.md §8): нужно убедиться,
  что актуальная версия FBReader из Google Play действительно экспортирует сервисы
  `android.fbreader.action.API` и `android.fbreader.action.LIBRARY_SERVICE`.
* Пока AIDL не подключён, `FbReaderApi`/`FbReaderLibraryApi` в
  `src/main/kotlin/.../fbreader/FbReaderContracts.kt` — наши собственные интерфейсы, и это
  правильная граница: доменный слой не зависит от `Parcelable`.

## Что сделать в Sprint 7

1. Скопировать перечисленные `.aidl`-файлы (и только их — они помечены в исходниках как
   *public domain*) в этот каталог, сохранив структуру пакетов. Минимальный набор, без
   `BookCollectionShadow` и модели `org.geometerplus.fbreader.book.*`.
2. Добавить в `core/readerlink/build.gradle.kts`:

   ```kotlin
   android {
       buildFeatures {
           aidl = true
       }
   }
   ```

   (в файле уже стоит `TODO(Sprint 7)` на этот счёт).
3. Сохранить пометку об лицензии public domain в `NOTICE`/`licenses` (docs/reader-integration.md §4.2).
4. Написать адаптеры от сгенерированных AIDL-типов к `FbReaderApi`/`FbReaderLibraryApi`; никакие
   данные из FBReader не отправляются в сеть.

Если `bindService` к сервисам не сработает на целевой версии FBReader — приложение деградирует
до L1 (открытие файла) без потери основной функциональности; каталог в этом случае остаётся
неиспользованным.
