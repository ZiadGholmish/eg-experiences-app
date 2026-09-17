package com.egyptexperiences.core.common.locale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppLanguageTest {
    @Test
    fun `arabic is the default - matching the backend locale fallback`() {
        // The backend resolves an unspecified Accept-Language to Arabic. If the
        // client disagreed, an unconfigured user would see an Arabic payload in
        // an English layout.
        assertEquals(AppLanguage.ARABIC, AppLanguage.default)
        assertTrue(AppLanguage.ARABIC.isRtl)
    }

    @Test
    fun `a full BCP-47 tag resolves to its language`() {
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromTag("ar-EG"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-US"))
    }

    @Test
    fun `an unknown or missing tag falls back to the default`() {
        assertEquals(AppLanguage.default, AppLanguage.fromTag("fr"))
        assertEquals(AppLanguage.default, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.default, AppLanguage.fromTag(""))
    }
}
