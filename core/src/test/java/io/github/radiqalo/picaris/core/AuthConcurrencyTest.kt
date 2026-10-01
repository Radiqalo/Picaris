package io.github.radiqalo.picaris.core

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AuthConcurrencyTest {
    private class Memory(var stored: VaultData) : CredentialStore {
        override fun read() = stored

        override fun write(data: VaultData) {
            stored = data
        }
    }

    private fun account(id: Long, token: String, expired: Boolean = false) =
        Account(
            User(id, "user$id"),
            token,
            "refresh$id",
            if (expired) 0 else System.currentTimeMillis() + 3600000,
        )

    @Test
    fun concurrentExpiredRequestsRefreshExactlyOnce() = runBlocking {
        val store = Memory(VaultData(listOf(account(1, "old", true)), 1))
        val calls = AtomicInteger()
        val exchange =
            object : OAuthExchange {
                override suspend fun exchange(fields: Map<String, String>): Account {
                    calls.incrementAndGet()
                    delay(25)
                    return account(1, "new")
                }
            }
        val auth = AuthRepository(store, exchange)
        val tokens = List(30) { async(Dispatchers.Default) { auth.token(1) } }.awaitAll()
        assertEquals(1, calls.get())
        assertEquals(setOf("new"), tokens.toSet())
    }

    @Test
    fun concurrent401RetriesReuseAlreadyRefreshedToken() = runBlocking {
        val store = Memory(VaultData(listOf(account(1, "old")), 1))
        val calls = AtomicInteger()
        val auth =
            AuthRepository(
                store,
                object : OAuthExchange {
                    override suspend fun exchange(fields: Map<String, String>): Account {
                        calls.incrementAndGet()
                        delay(20)
                        return account(1, "new")
                    }
                },
            )
        List(20) { async { auth.token(1, true, "old") } }.awaitAll()
        assertEquals(1, calls.get())
    }

    @Test
    fun refreshDoesNotSwitchTheSelectedAccountAndRemovalRevokesAccess() = runBlocking {
        val store = Memory(VaultData(listOf(account(1, "one"), account(2, "old", true)), 1))
        val auth =
            AuthRepository(
                store,
                object : OAuthExchange {
                    override suspend fun exchange(fields: Map<String, String>) = account(2, "two")
                },
            )
        assertEquals("two", auth.token(2))
        assertEquals(1L, auth.active!!.user.id)
        auth.remove(2)
        try {
            auth.token(2)
            fail("removed account must fail")
        } catch (e: PixivException) {
            assertEquals(401, e.code)
        }
    }
}
