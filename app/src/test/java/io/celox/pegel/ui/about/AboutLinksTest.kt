package io.celox.pegel.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** What the About section claims — author, licence, where the money goes — pinned in one place. */
class AboutLinksTest {

    @Test
    fun `author, site, repository and licence`() {
        assertEquals("Martin Pfeffer", AboutLinks.AUTHOR)
        assertEquals("https://celox.io", AboutLinks.WEBSITE_URL)
        assertEquals("https://pegel.celox.io", AboutLinks.PRODUCT_URL)
        assertEquals("pegel.celox.io", AboutLinks.PRODUCT_LABEL)
        assertEquals("https://pegel.celox.io", AboutLinks.PRODUCT_URL)
        assertEquals("https://github.com/pepperonas/pegel", AboutLinks.REPO_URL)
        assertEquals("MIT License", AboutLinks.LICENSE_NAME)
        assertEquals("https://github.com/pepperonas/pegel/blob/main/LICENSE", AboutLinks.LICENSE_URL)
        assertEquals("https://github.com/pepperonas/pegel/blob/main/CHANGELOG.md", AboutLinks.CHANGELOG_URL)
    }

    @Test
    fun `the donation link names this app and asks for euros`() {
        val url = AboutLinks.donateUrl()
        assertTrue(url.startsWith("https://www.paypal.com/donate/?business=martin.pfeffer@celox.io&"))
        assertTrue(url.contains("currency_code=EUR"))
        assertTrue(url.endsWith("item_name=Pegel"))
    }

    @Test
    fun `the licence the app names is the licence in the repository`() {
        val license = (File("../LICENSE").takeIf { it.exists() } ?: File("LICENSE")).readText()
        assertTrue(license.startsWith("MIT License"))
        assertTrue(license.contains("Copyright (c) 2026 ${AboutLinks.AUTHOR}"))
    }
}
