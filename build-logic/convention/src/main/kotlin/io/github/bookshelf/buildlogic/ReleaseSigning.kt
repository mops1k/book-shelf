package io.github.bookshelf.buildlogic

import java.io.File
import java.util.Properties
import org.gradle.api.Project

/** Ключ подписи релиза: хранилище ключей и доступы к нему. */
internal data class ReleaseSigningKey(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

/** Файл с ключом подписи в корне репозитория. В `.gitignore`: в репозиторий ключ не попадает. */
private const val KEYSTORE_PROPERTIES = "keystore.properties"

/** Переменные окружения CI: путь к файлу ключа и доступы к нему. */
private const val ENV_KEYSTORE_FILE = "BOOKSHELF_KEYSTORE_FILE"
private const val ENV_KEYSTORE_PASSWORD = "BOOKSHELF_KEYSTORE_PASSWORD"
private const val ENV_KEY_ALIAS = "BOOKSHELF_KEY_ALIAS"
private const val ENV_KEY_PASSWORD = "BOOKSHELF_KEY_PASSWORD"

/**
 * Ключ подписи релиза или `null`, если ключа нет.
 *
 * Локально ключ описывается файлом `keystore.properties` в корне репозитория:
 *
 * ```
 * storeFile=release.jks
 * storePassword=…
 * keyAlias=bookshelf
 * keyPassword=…
 * ```
 *
 * В CI ключ приходит переменными окружения ([ENV_KEYSTORE_FILE] и три доступа): сам файл ключа
 * workflow собирает из секрета GitHub и не пишет в репозиторий.
 *
 * Отсутствие ключа — не ошибка: release-сборка просто останется неподписанной, поэтому локальная
 * работа и debug-сборки не страдают. Релизный workflow проверяет подпись явно (`apksigner verify`)
 * и падает, если ключа не было. А вот неполный ключ — ошибка: молча выпустить неподписанный релиз
 * хуже, чем остановиться.
 */
internal fun Project.releaseSigningKey(): ReleaseSigningKey? = envSigningKey() ?: fileSigningKey()

private fun Project.envSigningKey(): ReleaseSigningKey? {
    val storePath = System.getenv(ENV_KEYSTORE_FILE)?.trim().orEmpty()
    if (storePath.isEmpty()) return null
    val store = File(storePath)
    require(store.exists()) { "Файл ключа подписи не найден: $ENV_KEYSTORE_FILE=$storePath" }
    return ReleaseSigningKey(
        storeFile = store,
        storePassword = requiredEnv(ENV_KEYSTORE_PASSWORD),
        keyAlias = requiredEnv(ENV_KEY_ALIAS),
        keyPassword = requiredEnv(ENV_KEY_PASSWORD),
    )
}

private fun Project.fileSigningKey(): ReleaseSigningKey? {
    val propertiesFile = rootProject.file(KEYSTORE_PROPERTIES)
    if (!propertiesFile.exists()) return null
    val properties = Properties().apply { propertiesFile.inputStream().use(::load) }
    val storeFile = rootProject.file(properties.requiredProperty("storeFile"))
    require(storeFile.exists()) {
        "$KEYSTORE_PROPERTIES указывает на несуществующий файл ключа: ${storeFile.absolutePath}"
    }
    return ReleaseSigningKey(
        storeFile = storeFile,
        storePassword = properties.requiredProperty("storePassword"),
        keyAlias = properties.requiredProperty("keyAlias"),
        keyPassword = properties.requiredProperty("keyPassword"),
    )
}

private fun requiredEnv(name: String): String =
    System.getenv(name)?.trim().orEmpty().ifEmpty {
        error("Переменная окружения $name не задана, а без неё релиз не подписать")
    }

private fun Properties.requiredProperty(name: String): String =
    getProperty(name)?.trim().orEmpty().ifEmpty {
        error("В $KEYSTORE_PROPERTIES не заполнено поле '$name'")
    }
