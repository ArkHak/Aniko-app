@file:OptIn(
    kotlinx.cinterop.UnsafeNumber::class, // CFIndex в cfDictionaryOf.
    kotlinx.cinterop.ExperimentalForeignApi::class, // cinterop над platform.Security/CoreFoundation.
    kotlinx.cinterop.BetaInteropApi::class, // NSString.create(NSData, encoding).
    kotlin.experimental.ExperimentalNativeApi::class, // Cleaner для освобождения retained CFStringRef.
)

package com.aniko.data.session

import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.OSStatus
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner

/**
 * Secure-хранилище токена на iOS: Apple Keychain, generic password
 * (`kSecClassGenericPassword`), сервис/аккаунт — фиксированные константы этого класса.
 *
 * `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`: элемент недоступен, пока устройство
 * заблокировано, не синкается в iCloud Keychain и не восстанавливается на другом устройстве
 * из бэкапа — токен авторизации не должен «путешествовать» за пределы конкретного телефона.
 *
 * Реализация — минимальный набор `SecItemAdd`/`SecItemCopyMatching`/`SecItemUpdate`/
 * `SecItemDelete` поверх голого `platform.Security`/`platform.CoreFoundation`
 * (без сторонних cinterop-библиотек), написанный по мотивам `KeychainSettings` из
 * `com.russhwolf:multiplatform-settings` (та же библиотека, которой в проекте уже пользуемся
 * для обычных, несекретных настроек, — здесь применён тот же низкоуровневый приём).
 *
 * `CFDictionaryCreate` вызывается без retain/release-колбэков (`null, null`), поэтому все
 * CF-значения, которые попадают в словарь, обязаны быть живы (retained) на всё время вызова
 * `SecItem*` — отсюда `cfService`/`cfAccount`, удерживаемые на весь жизненный цикл объекта
 * и освобождаемые через [Cleaner], и точечный [cfRetain] для значения токена на время записи.
 *
 * ОТКЛОНЕНИЕ ОТ ПЛАНА: тело методов уходит на [ioDispatcher] (= `Dispatchers.Default`), а не
 * на `Dispatchers.IO`, как в Android/Desktop-реализациях. `Dispatchers.IO` в kotlinx.coroutines
 * на Kotlin/Native — `internal` API (публичного эквивалента для Native нет в принципе, только
 * для JVM), компилятор явно отказывается его резолвить за пределами модуля coroutines. Это не
 * архитектурная правка, а единственный компилируемый вариант на этой платформе.
 */
class IosKeychainTokenStorage : SecureTokenStorage {
    private val ioDispatcher get() = Dispatchers.Default

    // Тип — CFTypeRef?, а не CFStringRef?: это ЗНАЧЕНИЯ для kSecAttrService/kSecAttrAccount
    // в словаре запроса, а не сами ключи (ключи — константы kSecAttrService/kSecAttrAccount).
    private val cfService: CFTypeRef? = CFBridgingRetain(SERVICE)
    private val cfAccount: CFTypeRef? = CFBridgingRetain(ACCOUNT)

    @Suppress("unused")
    private val cleaner: Cleaner =
        createCleaner(cfService to cfAccount) { (service, account) ->
            CFBridgingRelease(service)
            CFBridgingRelease(account)
        }

    private val baseProperties: Map<CFStringRef?, CFTypeRef?>
        get() =
            mapOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to cfService,
                kSecAttrAccount to cfAccount,
            )

    override suspend fun get(): String? =
        withContext(ioDispatcher) {
            readTokenData()?.let { NSString.create(it, NSUTF8StringEncoding)?.asKotlinString() }
        }

    override suspend fun set(token: String): Unit =
        withContext(ioDispatcher) {
            val data = token.asNSString().dataUsingEncoding(NSUTF8StringEncoding)
            if (!addKeychainItem(data)) {
                updateKeychainItem(data)
            }
        }

    override suspend fun clear(): Unit =
        withContext(ioDispatcher) {
            removeKeychainItem()
        }

    private fun readTokenData(): NSData? =
        memScoped {
            val cfValue = alloc<CFTypeRefVar>()
            val status =
                keychainOperation(
                    kSecReturnData to kCFBooleanTrue,
                    kSecMatchLimit to kSecMatchLimitOne,
                ) { SecItemCopyMatching(it, cfValue.ptr) }
            if (status == errSecItemNotFound) return@memScoped null
            status.checkError()
            CFBridgingRelease(cfValue.value) as? NSData
        }

    private fun addKeychainItem(value: NSData?): Boolean =
        cfRetain(value) { cfValue ->
            val status =
                keychainOperation(
                    kSecValueData to cfValue,
                    kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                ) { SecItemAdd(it, null) }
            status.checkError(errSecDuplicateItem)
            status != errSecDuplicateItem
        }

    private fun updateKeychainItem(value: NSData?): Unit =
        cfRetain(value) { cfValue ->
            val status =
                keychainOperation {
                    val attributes = cfDictionaryOf(kSecValueData to cfValue)
                    val output = SecItemUpdate(it, attributes)
                    CFBridgingRelease(attributes)
                    output
                }
            status.checkError()
        }

    private fun removeKeychainItem() {
        val status = memScoped { keychainOperation { SecItemDelete(it) } }
        status.checkError(errSecItemNotFound)
    }

    private inline fun MemScope.keychainOperation(
        vararg input: Pair<CFStringRef?, CFTypeRef?>,
        operation: (query: CFDictionaryRef?) -> OSStatus,
    ): OSStatus {
        val query = cfDictionaryOf(baseProperties + mapOf(*input))
        val output = operation(query)
        CFBridgingRelease(query)
        return output
    }

    private fun OSStatus.checkError(vararg expectedErrors: OSStatus) {
        check(this == 0 || this in expectedErrors) { "Keychain error, OSStatus=$this" }
    }

    private companion object {
        const val SERVICE = "com.aniko.token"
        const val ACCOUNT = "token"
    }
}

private fun MemScope.cfDictionaryOf(vararg items: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? = cfDictionaryOf(mapOf(*items))

private fun MemScope.cfDictionaryOf(map: Map<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
    val size = map.size
    val keys = allocArrayOf(*map.keys.toTypedArray())
    val values = allocArrayOf(*map.values.toTypedArray())
    return CFDictionaryCreate(
        kCFAllocatorDefault,
        keys.reinterpret(),
        values.reinterpret(),
        size.convert(),
        null,
        null,
    )
}

private inline fun <T> cfRetain(
    value: Any?,
    block: MemScope.(CFTypeRef?) -> T,
): T =
    memScoped {
        val cfValue = CFBridgingRetain(value)
        try {
            block(cfValue)
        } finally {
            CFBridgingRelease(cfValue)
        }
    }

// Оборачиваем каст в функции ради читаемости на месте вызова (аналогично multiplatform-settings).
@Suppress("CAST_NEVER_SUCCEEDS")
private fun String.asNSString() = this as NSString

@Suppress("CAST_NEVER_SUCCEEDS")
private fun NSString.asKotlinString() = this as String
