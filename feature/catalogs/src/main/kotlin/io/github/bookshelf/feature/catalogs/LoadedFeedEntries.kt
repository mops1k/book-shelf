package io.github.bookshelf.feature.catalogs

import io.github.bookshelf.core.data.model.OpdsEntry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Записи фидов, которые пользователь уже видел в браузере каталога.
 *
 * Нужны карточке записи. Раньше она находила книгу повторным чтением фида по адресу страницы, и
 * этого не хватало: список догружает страницы 2, 3… (`onLoadNextPage`), а карточке отдавался адрес
 * первой — книга, видимая в списке, объявлялась «больше не входит в этот фид» (сообщение Александра,
 * 08.10.2026: «Новинки → Все новинки → Боевая целительница»; на живой Флибусте эта книга лежит на
 * третьей странице `/opds/new/2/new`, а на первой её нет). Второй случай того же класса — фид успел
 * измениться, пока список оставался на экране: карточка обязана показывать то, что видел
 * пользователь, а не требовать запись в свежем ответе сервера.
 *
 * Существует как общее состояние сессии (как [CatalogFeedRequest]): браузер каталога кладёт записи
 * уровня, карточка их читает. Вместе с записью хранится адрес страницы, откуда она пришла: это
 * запасной путь карточки, если записи в памяти уже нет (процесс перезапущен) — тогда фид
 * перечитывается, но именно та страница, где запись лежала.
 *
 * Обращения идут с главного потока (обе ViewModel работают в `viewModelScope`), поэтому
 * синхронизация не нужна.
 */
@Singleton
class LoadedFeedEntries @Inject constructor() {

    /** Запись вместе с адресом страницы, с которой её отдал фид. */
    private data class Remembered(val entry: OpdsEntry, val feedUrl: String?)

    private val catalogs = mutableMapOf<String, MutableMap<String, Remembered>>()

    /**
     * Запомнить записи страницы.
     *
     * Запись обновляется всегда (фид мог прислать другие метаданные), а адрес страницы — только при
     * первом появлении записи: догрузка страниц не должна переписывать его адресом первой страницы.
     *
     * @param catalogId каталог, которому принадлежат записи.
     * @param entries записи, отданные страницей фида.
     * @param feedUrl адрес страницы; `null` — корневой фид каталога.
     */
    fun remember(catalogId: String, entries: Collection<OpdsEntry>, feedUrl: String?) {
        val known = catalogs.getOrPut(catalogId) { mutableMapOf() }
        entries.forEach { entry ->
            val previous = known[entry.id]
            known[entry.id] = Remembered(entry = entry, feedUrl = previous?.feedUrl ?: feedUrl)
        }
    }

    /** Запись, которую показывал список; `null` — список её не отдавал. */
    fun find(catalogId: String, entryId: String): OpdsEntry? =
        catalogs[catalogId]?.get(entryId)?.entry

    /** Адрес страницы, откуда пришла запись; `null` — корневой фид или запись не найдена. */
    fun feedUrlOf(catalogId: String, entryId: String): String? =
        catalogs[catalogId]?.get(entryId)?.feedUrl
}
