package io.github.bookshelf.core.opds.model

/**
 * Версия OPDS-каталога (колонка `opds_catalogs.kind` в docs/opds.md §2).
 *
 * OPDS 1.2 обязателен и поддерживается всегда; OPDS 2.0 — опция за флагом, реализуется в
 * Sprint 12 / Phase 4 (docs/opds.md §1, docs/roadmap.md).
 *
 * @param mimeType MIME, который каталог отдаёт в `Content-Type`.
 */
enum class OpdsCatalogKind(val mimeType: String) {
    /** Atom XML: `application/atom+xml;profile=opds-catalog;kind=acquisition`. */
    OPDS_1_2("application/atom+xml;profile=opds-catalog;kind=acquisition"),

    /** JSON: `application/opds+json`; поддержка за флагом. */
    OPDS_2_0("application/opds+json"),
}
