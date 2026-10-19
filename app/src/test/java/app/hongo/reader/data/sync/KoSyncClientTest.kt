package app.hongo.reader.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class KoSyncClientTest {
    private val server = FakeKoSyncServer()
    private val client = KoSyncClient(connectTimeoutMs = 2_000, readTimeoutMs = 2_000)
    private val account = KoSyncAccount(server.url, "reader", KoSyncAccount.userKey("secret"))

    @After
    fun tearDown() = server.stop()

    @Test
    fun theKeyIsTheMd5OfThePassword() {
        assertEquals("5ebe2294ecd0e0f08eab7690d2a6ee69", KoSyncAccount.userKey("secret"))
    }

    @Test
    fun signsIn_savesAndReadsProgress() = runTest {
        client.authorize(account)
        assertNull(client.progress(account, "abc"))

        client.update(account, "abc", "/body/DocFragment[2]/body/p/text().3", 0.58, "HonGO! (Test)", "DEV1")
        val saved = client.progress(account, "abc")!!
        assertEquals("/body/DocFragment[2]/body/p/text().3", saved.progress)
        assertEquals(0.58, saved.percentage, 0.0)
        assertEquals("HonGO! (Test)", saved.device)
        assertEquals("DEV1", saved.deviceId)
        assertEquals(server.now, saved.timestamp)
        assertEquals("reader", server.requests.last().headers["x-auth-user"])
    }

    @Test
    fun registers_andSaysWhenTheNameIsTaken() = runTest {
        val newcomer = account.copy(username = "new")
        client.register(newcomer)
        client.authorize(newcomer)
        assertFails(KoSyncException.Reason.USER_EXISTS) { client.register(account) }
        // Registering sends no login headers.
        assertNull(server.requests.first().headers["x-auth-key"])
    }

    @Test
    fun errorsSayWhatWentWrong() = runTest {
        assertFails(KoSyncException.Reason.UNAUTHORIZED) { client.authorize(account.copy(userKey = "wrong")) }
        assertFails(KoSyncException.Reason.NETWORK) { client.authorize(account.copy(server = server.url + "/nothing-here")) }
        assertFails(KoSyncException.Reason.NETWORK) { client.authorize(account.copy(server = "ftp://example.com")) }
    }

    @Test
    fun serverAddressesAsTyped() {
        assertEquals("https://sync.koreader.rocks/users/auth", KoSyncClient.endpoint("sync.koreader.rocks", "/users/auth"))
        assertEquals("https://cwa.example/kosync/users/auth", KoSyncClient.endpoint(" https://cwa.example/kosync/ ", "/users/auth"))
        assertEquals("http://nas:8081/users/auth", KoSyncClient.endpoint("http://nas:8081", "/users/auth"))
    }

    private suspend fun assertFails(reason: KoSyncException.Reason, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $reason")
        } catch (e: KoSyncException) {
            assertEquals(e.message, reason, e.reason)
        }
    }
}
