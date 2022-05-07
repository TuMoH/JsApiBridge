// Copyright 2021 Yandex LLC. All rights reserved.

package com.androidtim.jsapi.bridge.util

import com.androidtim.jsapi.bridge.util.PatternType.GLOB
import com.androidtim.jsapi.bridge.util.PatternType.REGEXP
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlUtilsTest {

    @Test
    fun verifyUrl() {
        // true
        assertTrue(UrlUtils.verifyUrl("zen.yandex.ru", "zen.yandex.ru", GLOB))
        assertTrue(UrlUtils.verifyUrl("zen.yandex.ru", "*.yandex.ru", GLOB))
        assertTrue(UrlUtils.verifyUrl("zen.yandex.ru", "*.yandex.*", GLOB))

        assertTrue(UrlUtils.verifyUrl("zen.yandex.com", "*.yandex.com", GLOB))
        assertTrue(UrlUtils.verifyUrl("zen.yandex.com", "*.yandex.*", GLOB))

        assertTrue(UrlUtils.verifyUrl("dz.zen.yandex.com", "*.yandex.*", GLOB))
        assertTrue(UrlUtils.verifyUrl("dz.zen.yandex.com", "*.yandex.com", GLOB))

        assertTrue(UrlUtils.verifyUrl("yandex.ru", "yandex.ru", GLOB))
        assertTrue(UrlUtils.verifyUrl("yandex.ru", "yandex.*", GLOB))

        // false
        assertFalse(UrlUtils.verifyUrl("zen.yandex.com", "zen.yandex.ru", GLOB))
        assertFalse(UrlUtils.verifyUrl("google.com", "*.yandex.ru", GLOB))

        assertFalse(UrlUtils.verifyUrl("zen.yamdex.ru", "*.yandex.*", GLOB))
        assertFalse(UrlUtils.verifyUrl("zenyandex.ru", "*.yandex.*", GLOB))

        assertFalse(UrlUtils.verifyUrl("zen.yandex.ru", "yandex.*", GLOB))
        assertFalse(UrlUtils.verifyUrl("yandex.ru", "yandex.com", GLOB))
        assertFalse(UrlUtils.verifyUrl("yandex.ru", "*.yandex.ru", GLOB))
        assertFalse(UrlUtils.verifyUrl("yandex.ru", "*.yandex.*", GLOB))

        assertFalse(UrlUtils.verifyUrl("dz.zen.yandex.com", "zen.yandex.com", GLOB))
        assertFalse(UrlUtils.verifyUrl("dz.zen.yandex.com", "zen.yandex.*", GLOB))
        assertFalse(UrlUtils.verifyUrl("dz.zen.yandex.com", "*.yandex.ru", GLOB))

        // regexp
        assertTrue(UrlUtils.verifyUrl("zen.yandex.ru", ".*\\.yandex\\..*", REGEXP))
        assertFalse(UrlUtils.verifyUrl("zen.yandex.ru", "yandex\\..*", REGEXP))
    }

    @Test
    fun verifyUrlWithPatternList() {
        // true
        assertTrue(
            UrlUtils.verifyUrl(
                "zen.yandex.ru",
                listOf("zen.yandex.ru"), GLOB
            )
        )
        assertTrue(
            UrlUtils.verifyUrl(
                "zen.yandex.ru",
                listOf("zen.yandex.ru", "zen.yandex.com"), GLOB
            )
        )
        assertTrue(
            UrlUtils.verifyUrl(
                "zen.yandex.ru",
                listOf("zen.yandex.*", "zen.yandex.com"), GLOB
            )
        )

        assertTrue(
            UrlUtils.verifyUrl(
                "zen.yandex.com",
                listOf("*.yandex.*", "zen.yandex.ru"), GLOB
            )
        )

        assertTrue(
            UrlUtils.verifyUrl(
                "dz.zen.yandex.com",
                listOf("*.yandex.*", "*.yandex.com"), GLOB
            )
        )

        assertTrue(
            UrlUtils.verifyUrl(
                "yandex.ru",
                listOf("yandex.com", "yandex.*"), GLOB
            )
        )

        // false
        assertFalse(
            UrlUtils.verifyUrl(
                "zen.yandex.com",
                listOf("*.yandex.ru", "zen.yandex.ru"), GLOB
            )
        )
        assertFalse(
            UrlUtils.verifyUrl(
                "google.com",
                listOf("*.yandex.*"), GLOB
            )
        )
        assertFalse(
            UrlUtils.verifyUrl(
                "zen.yamdex.ru",
                listOf("*.yandex.*"), GLOB
            )
        )
        assertFalse(
            UrlUtils.verifyUrl(
                "zenyandex.ru",
                listOf("yandex.*", "*.yandex.*"), GLOB
            )
        )

        assertFalse(
            UrlUtils.verifyUrl(
                "zen.yandex.ru",
                listOf("yandex.*", "yandex.ru"), GLOB
            )
        )

        assertFalse(
            UrlUtils.verifyUrl(
                "dz.zen.yandex.com",
                listOf("yandex.*", "yandex.com", "*.yandex.ru"), GLOB
            )
        )

        // regexp
        assertTrue(UrlUtils.verifyUrl("zen.yandex.ru", listOf(".*\\.yandex\\..*"), REGEXP))
        assertFalse(UrlUtils.verifyUrl("zen.yandex.ru", listOf("yandex\\..*"), REGEXP))
    }

}
