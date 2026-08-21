package com.example.diamond

import com.example.pvzh.data.pool.CredentialPoolParser
import com.example.pvzh.data.pool.PoolParseException
import com.example.pvzh.data.api.ApiCallResult
import com.example.pvzh.data.api.PvzhApiClient
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun contentVersion_accepts32HexCharacters() {
        assertEquals(
            "38b9447f96a43d37877273e6d457f8e2",
            CredentialPoolParser.parseVersion("\"38b9447f96a43d37877273e6d457f8e2\"")
        )
    }

    @Test(expected = PoolParseException::class)
    fun contentVersion_rejectsProxyHtml() {
        CredentialPoolParser.parseVersion("<html>proxy error</html>")
    }

    @Test
    fun emptyRewards_isClassifiedAsConcurrentMiss() {
        val result = PvzhApiClient().validateRewardResponse(200, "{\"rewards\":[]}", 1)
        assertTrue(result is ApiCallResult.ConcurrentMiss)
    }

    @Test
    fun matchingAward_isConfirmedSuccess() {
        val body = "{\"rewards\":[{\"gemsAwarded\":1}]}"
        val result = PvzhApiClient().validateRewardResponse(200, body, 1)
        assertTrue(result is ApiCallResult.Success)
        assertEquals(1, (result as ApiCallResult.Success).gemsAwarded)
    }
}
