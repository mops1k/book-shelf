package io.github.bookshelf.rar

import com.github.junrar.Archive
import com.github.junrar.io.SeekableReadOnlyByteChannel
import com.github.junrar.volume.Volume
import com.github.junrar.volume.VolumeManager
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.rar.RarArchive
import io.github.bookshelf.core.metadata.rar.RarEntry
import io.github.bookshelf.core.metadata.rar.RarNotSeekableException
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Чтение RAR через junrar — единственное место, где проект касается библиотеки под несвободной
 * UnRar License (ADR-007). Класс живёт в flavor-источнике `app/src/full`, поэтому в `fdroid`
 * и `play` его попросту нет, а в DI этих сборок пуст набор реализаций [RarArchive].
 *
 * Книга отдаётся не путём, а каналом: книги живут в SAF-дереве, и настоящий путь к файлу нам
 * неизвестен. Поэтому tom (том) для junrar собирается поверх того же канала, что даёт
 * `BookContentOpener` — без временной копии файла на диск и без чтения архива в память
 * (junar'овский `Archive(InputStream)` буферизует пропущенное и на большом CBR съел бы память).
 *
 * Многотомные архивы (`.part1.rar`, `.r00`) осознанно не поддерживаются: в книгах они
 * не встречаются, а молча читать первый том и отдавать половину страниц — хуже, чем сказать,
 * что архив не прочитан.
 */
class JunrarRarArchive : RarArchive {

    override fun forEachEntry(input: BookInput, action: (RarEntry, () -> InputStream) -> Unit) {
        val file = input.openSeekable() ?: throw RarNotSeekableException()
        file.use { seekable ->
            Archive(ChannelVolumeManager(seekable.channel), null, null).use { archive ->
                archive.fileHeaders.forEach { header ->
                    val name = header.fileNameString ?: return@forEach
                    if (header.isDirectory) return@forEach
                    action(RarEntry(name = name, sizeBytes = header.fullUnpackSize)) {
                        // junrar запускает распаковку записи именно здесь — в фоновом потоке через
                        // pipe — и ошибку распаковки проглатывает. Поэтому поток создаётся ровно
                        // тогда, когда посетитель собрался читать запись: иначе распаковки
                        // конкурируют за один канал и запись приходит пустой.
                        archive.getInputStream(header)
                    }
                }
            }
        }
    }
}

/** Том из одного канала: первый вызов отдаёт его, второй означает многотомный архив. */
private class ChannelVolumeManager(private val channel: FileChannel) : VolumeManager {

    override fun nextVolume(archive: Archive, lastVolume: Volume?): Volume {
        if (lastVolume != null) throw IOException("многотомный RAR не поддерживается")
        // Каждый проход разбора начинается с начала архива: junrar читает заголовки с текущей
        // позиции, и без сброса второй проход (за обложкой) не нашёл бы ничего.
        channel.position(0)
        return ChannelVolume(channel, archive)
    }
}

/** Один том: длина и канал берутся у книги, а не у файла на диске. */
private class ChannelVolume(
    private val channel: FileChannel,
    private val archive: Archive,
) : Volume {

    override fun getChannel(): SeekableReadOnlyByteChannel = ChannelReader(channel)

    override fun getLength(): Long = channel.size()

    override fun getArchive(): Archive = archive
}

/**
 * Мост «канал книги → то, что ждёт junrar».
 *
 * Закрытие канала здесь — пустышка: владеет каналом `SeekableFile`, и закрывает его тот, кто
 * открыл. Иначе junrar закрыл бы дескриптор под самим собой между двумя проходами.
 */
private class ChannelReader(private val channel: FileChannel) : SeekableReadOnlyByteChannel {

    private val one = ByteBuffer.allocate(1)

    override fun getPosition(): Long = channel.position()

    override fun setPosition(pos: Long) {
        channel.position(pos)
    }

    override fun read(): Int {
        one.clear()
        return if (channel.read(one) <= 0) EOF else one.array()[0].toInt() and BYTE_MASK
    }

    override fun read(buffer: ByteArray, offset: Int, count: Int): Int =
        channel.read(ByteBuffer.wrap(buffer, offset, count))

    override fun readFully(buffer: ByteArray, count: Int): Int {
        var read = 0
        while (read < count) {
            val step = channel.read(ByteBuffer.wrap(buffer, read, count - read))
            if (step <= 0) throw EOFException("архив обрывается: прочитано $read из $count байт")
            read += step
        }
        return read
    }

    override fun close() = Unit

    private companion object {
        const val EOF = -1
        const val BYTE_MASK = 0xFF
    }
}
