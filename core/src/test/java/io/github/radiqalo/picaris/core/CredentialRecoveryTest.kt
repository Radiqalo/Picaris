package io.github.radiqalo.picaris.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CredentialRecoveryTest {
    private class Store : CredentialStore {
        var failure = true
        var writes = 0
        var writeFailure = false
        var stored =
            VaultData(listOf(Account(User(42, "test"), "access", "refresh", Long.MAX_VALUE)), 42)

        override fun read(): VaultData {
            if (failure) throw CredentialReadException(IllegalStateException("unavailable"))
            return stored
        }

        override fun write(data: VaultData) {
            if (writeFailure) throw CredentialReadException(IllegalStateException("damaged"))
            writes++
            stored = data
        }
    }

    private val oauth =
        object : OAuthExchange {
            override suspend fun exchange(fields: Map<String, String>): Account =
                error("OAuth must not run while credentials are unavailable")
        }

    @Test
    fun failedReadBlocksEveryMutationAndTokenAccess() =
        runBlocking {
            val store = Store()
            val auth = AuthRepository(store, oauth)
            assertNotNull(auth.readError.value)
            val operations: List<suspend () -> Unit> =
                listOf(
                    { auth.select(42) },
                    { auth.remove(42) },
                    { auth.updateUser(42, User(42, "updated")) },
                    { auth.startLogin() },
                    { auth.finishLogin("code") },
                    { auth.importToken("refresh") },
                    { auth.token(42) },
                )
            operations.forEach { operation ->
                assertThrows(CredentialReadException::class.java) { runBlocking { operation() } }
            }
            assertEquals(0, store.writes)
            assertEquals(42L, store.stored.activeId)
        }

    @Test
    fun retryRestoresAccountsAndAllowsWrites() =
        runBlocking {
            val store = Store()
            val auth = AuthRepository(store, oauth)
            auth.retryCredentials()
            assertNotNull(auth.readError.value)
            assertEquals(0, store.writes)
            store.failure = false
            auth.retryCredentials()
            assertNull(auth.readError.value)
            assertEquals(42L, auth.active?.user?.id)
            auth.remove(42)
            assertEquals(1, store.writes)
            assertEquals(emptyList<Account>(), auth.data.value.accounts)
        }

    @Test
    fun failedRetryRetainsLoadedAccountsAndBlocksWrites() =
        runBlocking {
            val store = Store().apply { failure = false }
            val auth = AuthRepository(store, oauth)
            val before = auth.data.value
            store.failure = true
            auth.retryCredentials()
            assertNotNull(auth.readError.value)
            assertEquals(before, auth.data.value)
            assertThrows(CredentialReadException::class.java) { runBlocking { auth.remove(42) } }
            assertEquals(0, store.writes)
        }

    @Test
    fun unreadableVaultDuringWritePreservesPublishedState() =
        runBlocking {
            val store = Store().apply { failure = false }
            val auth = AuthRepository(store, oauth)
            val before = auth.data.value
            store.writeFailure = true
            assertThrows(CredentialReadException::class.java) { runBlocking { auth.remove(42) } }
            assertNotNull(auth.readError.value)
            assertEquals(before, auth.data.value)
            assertEquals(before, store.stored)
            assertEquals(0, store.writes)
        }
}
