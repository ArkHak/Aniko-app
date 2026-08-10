package com.aniko.database.di

import com.aniko.database.AnikoDatabase
import com.aniko.database.driver.DatabaseDriverFactory
import com.aniko.database.store.EpisodeProgressStore
import com.aniko.database.store.ListMembershipStore
import com.aniko.database.store.ReleaseCacheStore
import com.aniko.database.store.ReleaseListStore
import com.aniko.database.store.SqlDelightEpisodeProgressStore
import com.aniko.database.store.SqlDelightListMembershipStore
import com.aniko.database.store.SqlDelightReleaseCacheStore
import com.aniko.database.store.SqlDelightReleaseListStore
import com.aniko.database.store.SqlDelightSyncQueueStore
import com.aniko.database.store.SyncQueueStore
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin-модуль `:shared:database` (P4.T7, S3 — интеграция).
 *
 * `Clock` и диспетчер `named("io")`, которыми пользуются реализации сторов ниже, сюда намеренно
 * НЕ добавлены — они уже зарегистрированы в `dataModule` (`:shared:data`), и Koin резолвит `get()`
 * по всем загруженным модулям сразу, независимо от того, в каком из них объявлен `single`, и
 * независимо от порядка модулей в `modules(...)` в `Koin.kt`.
 */
val databaseModule =
    module {
        single<AnikoDatabase> { AnikoDatabase(driver = get<DatabaseDriverFactory>().create()) }

        single<ReleaseCacheStore> { SqlDelightReleaseCacheStore(get(), get(named("io"))) }
        single<ReleaseListStore> { SqlDelightReleaseListStore(get(), get(named("io"))) }
        single<ListMembershipStore> { SqlDelightListMembershipStore(get(), get(named("io"))) }
        single<EpisodeProgressStore> { SqlDelightEpisodeProgressStore(get(), get(named("io"))) }
        single<SyncQueueStore> { SqlDelightSyncQueueStore(get(), get(named("io"))) }
    }
