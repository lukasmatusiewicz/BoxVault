package com.boxvault.app.util.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationCompletenessTest {

    data class StringResourceEntry(
        val name: String,
        val text: String,
        val formatSpecifiers: List<String>
    )

    private val targetLocales = listOf(
        "values-pl", // Polish
        "values-de", // German
        "values-es", // Spanish
        "values-fr", // French
        "values-it", // Italian
        "values-uk"  // Ukrainian
    )

    private fun resolveResDir(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && File(it, "values/strings.xml").exists() }
            ?: error("Unable to locate res/ directory among: ${candidates.map { it.absolutePath }}")
    }

    private fun extractFormatSpecifiers(content: String): List<String> {
        val regex = "%(\\d+\\$)?[a-zA-Z]".toRegex()
        return regex.findAll(content).map { it.value }.toList()
    }

    private fun parseStringsXml(file: File): Map<String, StringResourceEntry> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        doc.documentElement.normalize()

        val stringElements = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, StringResourceEntry>()

        for (i in 0 until stringElements.length) {
            val node = stringElements.item(i)
            val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
            val text = node.textContent ?: ""
            val specifiers = extractFormatSpecifiers(text)
            result[name] = StringResourceEntry(name, text, specifiers)
        }
        return result
    }

    @Test
    fun defaultStringsXml_containsExpectedKeys() {
        val resDir = resolveResDir()
        val defaultFile = File(resDir, "values/strings.xml")
        assertTrue("Default strings.xml must exist", defaultFile.exists())

        val defaultStrings = parseStringsXml(defaultFile)
        assertTrue("Default strings.xml must contain at least 100 string keys", defaultStrings.size >= 100)
    }

    @Test
    fun allTargetLocales_haveCompleteKeyParityAndMatchingFormatSpecifiers() {
        val resDir = resolveResDir()
        val defaultFile = File(resDir, "values/strings.xml")
        val defaultStrings = parseStringsXml(defaultFile)
        val defaultKeys = defaultStrings.keys

        for (localeDirName in targetLocales) {
            val localeFile = File(resDir, "$localeDirName/strings.xml")
            assertTrue("Locale strings file must exist: ${localeFile.path}", localeFile.exists())

            val localeStrings = parseStringsXml(localeFile)
            val localeKeys = localeStrings.keys

            val missingKeys = defaultKeys - localeKeys
            val unexpectedKeys = localeKeys - defaultKeys

            assertTrue(
                "Missing keys in $localeDirName: $missingKeys",
                missingKeys.isEmpty()
            )
            assertTrue(
                "Unexpected extra keys in $localeDirName: $unexpectedKeys",
                unexpectedKeys.isEmpty()
            )
            assertEquals(
                "Exact key count parity required for $localeDirName",
                defaultKeys.size,
                localeKeys.size
            )

            // Verify format specifiers for each key
            for ((key, defaultEntry) in defaultStrings) {
                val localeEntry = localeStrings[key]
                    ?: error("Key $key missing in $localeDirName")

                val defaultSorted = defaultEntry.formatSpecifiers.sorted()
                val localeSorted = localeEntry.formatSpecifiers.sorted()

                assertEquals(
                    "Format specifier mismatch in $localeDirName for key '$key' (expected: $defaultSorted, found: $localeSorted)",
                    defaultSorted,
                    localeSorted
                )
            }
        }
    }
}
