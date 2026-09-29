package io.github.bookshelf.core.storage.saf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Путь документа внутри дерева SAF (`SafRelativePath.kt`).
 *
 * Тест появился после дефекта на устройстве (29.09.2026): сканер писал в `relativePath` только имя
 * файла, и у 66 из 77 книг путь в карточке был неполным, а группировка «По папкам» не разделяла
 * книги. Здесь проверяются ровно те случаи, на которых это ломалось.
 */
class SafRelativePathTest {

    @Test
    fun `nested document keeps its folders`() {
        val path = relativePathInTree(
            treeDocumentId = "primary:Books",
            documentId = "primary:Books/Морган Ричард К./Сталь остается.epub",
        )

        assertThat(path).isEqualTo("Морган Ричард К./Сталь остается.epub")
    }

    @Test
    fun `document in the tree root has no folders`() {
        val path = relativePathInTree(
            treeDocumentId = "primary:Books",
            documentId = "primary:Books/Солярис.fb2",
        )

        assertThat(path).isEqualTo("Солярис.fb2")
    }

    @Test
    fun `tree root itself gives an empty path`() {
        // Корень деревом обхода не является книгой, но функция обязана не отдавать «primary:Books».
        assertThat(relativePathInTree("primary:Books", "primary:Books")).isEmpty()
    }

    @Test
    fun `foreign identifier falls back to the file name`() {
        // Провайдер вернул документ без общего с корнем префикса: полный идентификатор в поле
        // «Путь» выглядел бы мусором, поэтому берётся имя файла.
        val path = relativePathInTree(
            treeDocumentId = "primary:Books",
            documentId = "com.other.provider:root/Чужая книга.fb2",
        )

        assertThat(path).isEqualTo("Чужая книга.fb2")
    }

    @Test
    fun `trailing slash in the root does not break the prefix`() {
        val path = relativePathInTree(
            treeDocumentId = "primary:Books/",
            documentId = "primary:Books/Автор/Книга.fb2",
        )

        assertThat(path).isEqualTo("Автор/Книга.fb2")
    }
}
