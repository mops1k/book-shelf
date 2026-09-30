package io.github.bookshelf.core.fb2opt

import com.google.common.truth.Truth.assertThat
import java.util.Base64
import org.junit.Test

/**
 * Дедупликация одинаковых картинок внутри книги (`Fb2BinaryDeduplicator`).
 *
 * Зачем: в книгах, собранных конвертерами, одна и та же картинка нередко лежит несколькими
 * блоками `<binary>` под разными идентификаторами (орнамент главы, повторённая обложка). Хранить
 * её дважды незачем — ссылки можно перевести на одну копию.
 *
 * Проверяется и обратное: **разные** картинки не склеиваются, блок без `id` не трогается, а
 * ссылка внутри `CDATA` — это текст, а не разметка, и переписывать её нельзя.
 *
 * Фикстура настоящая: содержимое картинок взято из книги «Книга Уткина Мы тут жизнь живём».
 */
class Fb2BinaryDeduplicatorTest {

    private val deduplicator = Fb2BinaryDeduplicator()

    @Test
    fun `an identical picture is reported as a duplicate`() {
        val picture = jpeg()
        val content = bookWith(listOf("i_001.jpg" to picture, "i_002.jpg" to picture))

        val redirect = deduplicator.duplicateIds(content, Fb2Binaries.scan(content))

        assertThat(redirect).containsExactly("i_002.jpg", "i_001.jpg")
    }

    @Test
    fun `the kept copy is the first one in the file`() {
        val picture = jpeg()
        val content = bookWith(listOf("first.jpg" to picture, "second.jpg" to picture))

        val redirect = deduplicator.duplicateIds(content, Fb2Binaries.scan(content))

        // Остаётся именно первый: он стоит раньше, и читалке не приходится искать ссылку назад.
        assertThat(redirect).containsExactly("second.jpg", "first.jpg")
    }

    @Test
    fun `different pictures are both kept`() {
        val content = bookWith(listOf("photo.jpg" to jpeg(), "shot.png" to png()))

        assertThat(deduplicator.duplicateIds(content, Fb2Binaries.scan(content))).isEmpty()
    }

    @Test
    fun `a binary without an id is not a duplicate`() {
        val picture = jpeg()
        val content = bookWith(listOf("i_001.jpg" to picture)) +
            "<binary content-type=\"image/jpeg\">".toByteArray() + base64(picture).toByteArray() +
            "</binary>".toByteArray()

        // Идентификатора нет — ссылаться не на что, и склеивать нечего.
        assertThat(deduplicator.duplicateIds(content, Fb2Binaries.scan(content))).isEmpty()
    }

    @Test
    fun `line breaks inside base64 do not hide a duplicate`() {
        val picture = jpeg()
        val oneLine = base64(picture)
        val wrapped = oneLine.chunked(BASE64_LINE).joinToString("\n")
        val content = bookWithPayloads(listOf("i_001.jpg" to oneLine, "i_002.jpg" to wrapped))

        val redirect = deduplicator.duplicateIds(content, Fb2Binaries.scan(content))

        // Переносы строк в base64 ничего не значат: картинка та же.
        assertThat(redirect).containsExactly("i_002.jpg", "i_001.jpg")
    }

    @Test
    fun `references to the removed copy are rewritten`() {
        val markup = ("<body><section><image l:href=\"#i_002.jpg\"/></section></body>")
            .toByteArray(Charsets.UTF_8)

        val result = String(
            deduplicator.rewriteReferences(markup, mapOf("i_002.jpg" to "i_001.jpg")),
            Charsets.UTF_8,
        )

        assertThat(result).contains("l:href=\"#i_001.jpg\"")
        assertThat(result).doesNotContain("i_002.jpg")
    }

    @Test
    fun `a reference inside cdata is text and stays as it is`() {
        val markup = ("<section><code><![CDATA[<image l:href=\"#i_002.jpg\"/>]]></code></section>")
            .toByteArray(Charsets.UTF_8)

        val result = String(
            deduplicator.rewriteReferences(markup, mapOf("i_002.jpg" to "i_001.jpg")),
            Charsets.UTF_8,
        )

        assertThat(result).isEqualTo(String(markup, Charsets.UTF_8))
    }

    @Test
    fun `markup without redirects is returned as it is`() {
        val markup = "<p>Текст</p>".toByteArray(Charsets.UTF_8)

        assertThat(deduplicator.rewriteReferences(markup, emptyMap())).isEqualTo(markup)
    }

    /** Собрать маленькую книгу: ссылки в теле и блоки картинок в конце. Картинки — настоящие. */
    private fun bookWith(images: List<Pair<String, ByteArray>>): ByteArray =
        bookWithPayloads(images.map { (id, raw) -> id to base64(raw) })

    /** То же, но содержимое блоков задаётся готовым base64 — так проверяются переносы строк. */
    private fun bookWithPayloads(images: List<Pair<String, String>>): ByteArray {
        val text = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<FictionBook xmlns:l=\"http://www.w3.org/1999/xlink\">")
            append("<body><section>")
            images.forEach { (id, _) -> append("<image l:href=\"#$id\"/>") }
            append("</section></body>")
            images.forEach { (id, payload) ->
                append("<binary id=\"$id\" content-type=\"image/jpeg\">")
                append(payload)
                append("</binary>")
            }
            append("</FictionBook>")
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun jpeg(): ByteArray = fixture("image-plain.jpg")

    private fun png(): ByteArray = fixture("image-real.png")

    private fun fixture(name: String): ByteArray = checkNotNull(
        javaClass.getResourceAsStream("/$name"),
    ) { "нет фикстуры $name" }.use { stream -> stream.readBytes() }

    private companion object {
        /** Длина строки в base64 с переносами: так их пишут конвертеры. */
        const val BASE64_LINE = 76
    }
}
