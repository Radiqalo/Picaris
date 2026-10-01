package io.github.radiqalo.picaris

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.radiqalo.picaris.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenVaultTest {
    @Test
    fun pkceSessionSurvivesRepositoryRecreationAndIsConsumedByCallback() =
        kotlinx.coroutines.runBlocking {
            val context =
                object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
                    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                        super.getSharedPreferences("pkce_test_$name", mode)
                }
            val prefs = context.getSharedPreferences("credential_vault", Context.MODE_PRIVATE)
            var submitted: Map<String, String> = emptyMap()
            val exchange =
                object : OAuthExchange {
                    override suspend fun exchange(fields: Map<String, String>): Account {
                        submitted = fields
                        return Account(
                            User(42, "test"),
                            "test-access-only",
                            "test-refresh-only",
                            999999,
                        )
                    }
                }
            try {
                val url =
                    android.net.Uri.parse(
                        AuthRepository(TokenVault(context), exchange).startLogin()
                    )
                val recreated = AuthRepository(TokenVault(context), exchange)
                val verifier = recreated.data.value.pending!!.verifier
                val expected =
                    android.util.Base64.encodeToString(
                        java.security.MessageDigest.getInstance("SHA-256")
                            .digest(verifier.toByteArray()),
                        android.util.Base64.URL_SAFE or
                            android.util.Base64.NO_WRAP or
                            android.util.Base64.NO_PADDING,
                    )
                assertEquals(43, verifier.length)
                assertEquals(expected, url.getQueryParameter("code_challenge"))
                recreated.finishLogin("test-code-only")
                assertEquals(verifier, submitted["code_verifier"])
                assertEquals("test-code-only", submitted["code"])
                assertEquals("authorization_code", submitted["grant_type"])
                assertNull(recreated.data.value.pending)
                assertEquals(42L, recreated.active!!.user.id)
            } finally {
                prefs.edit().clear().commit()
            }
        }

    @Test
    fun keystoreRoundTripUsesFreshIvAndNeverStoresPlaintextTokens() {
        val context =
            object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
                override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                    super.getSharedPreferences("test_$name", mode)
            }
        val prefs = context.getSharedPreferences("credential_vault", Context.MODE_PRIVATE)
        val vault = TokenVault(context)
        val data =
            VaultData(
                accounts =
                    listOf(
                        Account(User(42, "test"), "test-access-only", "test-refresh-only", 999999)
                    ),
                activeId = 42,
                pending = AuthSession("test-verifier", 123),
            )
        try {
            vault.write(data)
            val first = prefs.getString("payload", null)!!
            assertFalse(first.contains("test-refresh-only"))
            assertEquals(data, vault.read())
            vault.write(data)
            assertNotEquals(first, prefs.getString("payload", null))
            assertEquals(data, vault.read())
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
