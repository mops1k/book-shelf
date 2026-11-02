package io.github.bookshelf.core.applist.rule

import io.github.bookshelf.core.applist.model.InstalledApp
import java.text.Collator
import java.util.Locale

/**
 * Правила списка приложений: отбор, поиск и порядок (ADR-015).
 *
 * Чистые функции без `PackageManager`, `Context` и Compose: всё, что решает «что видно и в каком
 * порядке», проверяется юнит-тестами, а Android-часть (`AndroidAppCatalog`) только поставляет
 * данные. Это правило проекта: логика, которую можно проверить без устройства, проверяется без
 * устройства (docs/tdd.md).
 */
object AppListRules {

    /**
     * Русский `Collator`, а не `String.compareTo`: иначе «Ёлкин» и «Ежов» уезжают в конец списка,
     * потому что сравниваются коды символов, а не буквы алфавита.
     */
    private val ruCollator: Collator = Collator.getInstance(Locale.forLanguageTag("ru")).apply {
        strength = Collator.PRIMARY
    }

    /** Сравнение подписей так, как их ждёт увидеть читатель: по алфавиту и без учёта регистра. */
    val byLabel: Comparator<InstalledApp> = Comparator { left, right ->
        val byName = ruCollator.compare(left.label, right.label)
        if (byName != 0) byName else left.packageName.compareTo(right.packageName)
    }

    /**
     * Что показывать в списке.
     *
     * Себя исключаем всегда: пункт «BookShelf» в собственном лаунчере запускает сам лаунчер.
     * Скрытые пользователем приложения — тоже всегда. Системные — только по галочке и только
     * полезные из [SystemAppWhitelist]: служебные записи вендора (тестовые пакеты, отчёты,
     * «настройки клавиатуры») не показываются даже с галочкой.
     */
    fun visible(
        apps: List<InstalledApp>,
        hiddenPackages: Set<String>,
        showSystem: Boolean,
        ownPackage: String,
    ): List<InstalledApp> = apps.filter { app ->
        !isOwnPackage(app.packageName, ownPackage) &&
            app.packageName !in hiddenPackages &&
            isVisibleSystem(app.isSystem, app.packageName, showSystem)
    }

    /** Системное приложение видно, только если галочка включена и оно в белом списке. */
    private fun isVisibleSystem(isSystem: Boolean, packageName: String, showSystem: Boolean): Boolean =
        !isSystem || (showSystem && SystemAppWhitelist.isUseful(packageName))

    /**
     * Наша сборка, в любом flavor.
     *
     * Отладочный пакет носит суффикс `.debug`, релизный — нет, и на устройстве они вполне могут
     * стоять одновременно (проверка релиза рядом с разработкой). Для лаунчера это одно и то же
     * приложение: запускать «Книжную полку» из её же списка приложений бессмысленно, поэтому
     * скрываем оба имени, а не только текущий пакет.
     */
    private fun isOwnPackage(packageName: String, ownPackage: String): Boolean =
        packageName == ownPackage || packageName == ownPackage.removeSuffix(".debug")

    /**
     * Поиск по подписи без учёта регистра.
     *
     * Ищем подстрокой, а не по началу слова: на e-ink пользователь набирает мало букв, и «книг»
     * должно находить «Книжный магазин». Пустой запрос возвращает всё — так строка поиска
     * не «съедает» список, когда её очистили.
     */
    fun search(apps: List<InstalledApp>, query: String): List<InstalledApp> {
        val needle = query.trim()
        if (needle.isEmpty()) return apps
        return apps.filter { app -> app.label.contains(needle, ignoreCase = true) }
    }

    /**
     * Порядок «по имени» с избранными сверху.
     *
     * Избранное — отдельный блок, а не просто закреплённые строки в общем списке: на e-ink
     * граница между блоками видна без цвета и без анимации.
     */
    fun byName(apps: List<InstalledApp>, favorites: Set<String> = emptySet()): List<InstalledApp> =
        splitFavorites(apps, favorites).let { (favoriteApps, otherApps) ->
            favoriteApps.sortedWith(byLabel) + otherApps.sortedWith(byLabel)
        }

    /**
     * Порядок «по частоте запуска»: чаще запускали — выше, при равенстве — по имени.
     *
     * Счётчики приходят из хранилища; приложение, которого там нет, считается запущенным ноль раз
     * и не теряется. Приложение, обновлённое пользователем, место не меняет: частота считается
     * по пакету, а не по версии.
     */
    fun byLaunchFrequency(
        apps: List<InstalledApp>,
        launchCounts: Map<String, Int>,
        favorites: Set<String> = emptySet(),
    ): List<InstalledApp> {
        val (favoriteApps, otherApps) = splitFavorites(apps, favorites)
        val byFrequency = compareByDescending<InstalledApp> { app -> launchCounts[app.packageName] ?: 0 }
            .then(byLabel)
        return favoriteApps.sortedWith(byFrequency) + otherApps.sortedWith(byFrequency)
    }

    /** Делит список на избранные и остальные, сохраняя исходный порядок внутри частей. */
    private fun splitFavorites(
        apps: List<InstalledApp>,
        favorites: Set<String>,
    ): Pair<List<InstalledApp>, List<InstalledApp>> = apps.partition { app ->
        app.packageName in favorites
    }
}
