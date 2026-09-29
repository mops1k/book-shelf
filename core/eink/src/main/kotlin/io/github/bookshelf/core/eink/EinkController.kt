package io.github.bookshelf.core.eink

import android.view.View
import io.github.bookshelf.core.eink.vendor.EinkVendor

/**
 * Единая точка управления экраном электронной бумаги (docs/architecture.md §6).
 *
 * Контракт намеренно маленький и не зависит от вендорских SDK: всё, что специфично для Onyx
 * или Rockchip, живёт в реализациях и деградирует до [io.github.bookshelf.core.eink.noop.NoopEinkController],
 * если вендорский API недоступен. Вызывающий код (в том числе дизайн-система через
 * `EinkRefreshHost`) обязан работать и когда методов вендора нет.
 *
 * Правила вызова:
 * * [setRefreshMode] и [fullRefresh] вызываются на UI-потоке и получают корневой `View` экрана;
 * * [setGcInterval] задаётся один раз при старте (или при смене настройки) — это «полный GC каждые
 *   N частичных обновлений», главный инструмент против ghosting (docs/eink-ux.md §4);
 * * [setFrontLight] безопасен всегда: при отсутствии подсветки это no-op, а [frontLight] вернёт `null`.
 */
interface EinkController {

    /** `true`, если устройство распознано как e-ink (дисплей требует редких полных перерисонов). */
    val isEinkDevice: Boolean

    /** Вендор устройства; [EinkVendor.UNKNOWN], если определить не удалось. */
    val vendor: EinkVendor

    /**
     * Доступны ли команды вендора: режим обновления, интервал полного обновления, подсветка.
     *
     * Отдельно от [isEinkDevice]: устройство с электронной бумагой может остаться без вендорского
     * контроллера (Bigme, Hisense, PocketBook и другие), и тогда «какой экран» и «что мы умеем» —
     * разные ответы. Пока эти два вопроса были склеены, диагностика показывала «обычный экран»
     * устройству с настоящей бумагой.
     */
    val hasVendorCommands: Boolean

    /** Доступен ли «быстрый» режим (A2/DU/FAST): набор текста, чекбоксы, мелкие переключения. */
    val supportsFastMode: Boolean

    /**
     * Просит вендора отрисовать [view] в нужном режиме.
     *
     * Реализации без вендорской поддержки обязаны просто вызвать `view.invalidate()` —
     * это безвредно на любом устройстве.
     */
    fun setRefreshMode(view: View, mode: RefreshMode)

    /** Принудительный полный перерисон экрана (GC): убирает «призраков» после смены кадра. */
    fun fullRefresh(view: View)

    /**
     * Полный GC каждые [partialUpdates] частичных обновлений.
     *
     * Настройка приложения; на устройствах без поддержки — no-op.
     */
    fun setGcInterval(partialUpdates: Int)

    /** Уровень подсветки 0..100; no-op, если подсветки нет. */
    fun setFrontLight(level: Int)

    /** Текущий уровень подсветки 0..100 или `null`, если устройство её не отдаёт/не поддерживает. */
    fun frontLight(): Int?
}

/**
 * Режим обновления экрана (docs/architecture.md §6, docs/eink-ux.md §4).
 *
 * * [NORMAL] — штатная отрисовка средствами платформы;
 * * [FAST] — быстрый режим для мелких изменений (если [EinkController.supportsFastMode]);
 * * [FULL] — полный перерисон с очисткой «призраков».
 */
enum class RefreshMode { NORMAL, FAST, FULL }
