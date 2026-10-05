package io.github.bookshelf.core.readerlink.profile

/**
 * Нужна ли читалке копия книги вместо нашего `content://`.
 *
 * ДЕФЕКТ (Александр, 05.10.2026: «открываю предыстория книга 1, открывается в фбридере вселенная
 * неудачников»). FBReader Premium 3.8.31 объявляет `VIEW` для `content` и `file` с любым MIME,
 * поэтому запускается на любую нашу ссылку — и по `content://` книгу чаще всего **не открывает**,
 * оставаясь на прежней. Проверено на устройстве: `ActivityTaskManager: START … pkg=com.fbreader`
 * проходит, читалка сама поднимает свою текстовую activity, а книга не меняется. По реальному пути
 * (`file:///storage/emulated/0/Books/…` у нас нет: библиотека подключена через SAF, и реальный путь
 * книги приложению неизвестен.) Копию FBReader читает через наш `FileProvider` — она лежит в
 * `cacheDir/export`, названа по книге (то есть с расширением) и выдана с грантом на чтение.
 */
fun ReaderProfile.needsExportedCopy(): Boolean = packageName == FbReaderPreset.PACKAGE_NAME
