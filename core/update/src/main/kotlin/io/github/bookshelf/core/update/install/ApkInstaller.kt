package io.github.bookshelf.core.update.install

import java.io.File

/**
 * Установка скачанного APK (ADR-018).
 *
 * Порт, чтобы ViewModel проверялась юнит-тестами: действия с `Intent` и системным установщиком
 * проверяются инструментально, а не моком чужих типов. Все методы — «сделать», без возврата
 * `Intent`: Android-типы не должны протекать в слой состояния.
 */
interface ApkInstaller {

    /** Нужно ли сначала выдать разрешение «установка неизвестных приложений». */
    fun gate(): InstallGate

    /** Открыть экран разрешения для нашего пакета. */
    fun requestPermission()

    /** Передать файл системному установщику. */
    fun install(file: File)
}
