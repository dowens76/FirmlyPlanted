package com.firmlyplanted.app.data.remote

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FetchBibleManifestTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun licenseMayBeAStringOrAFlagObject() {
        val manifest = json.decodeFromString<FetchBibleManifest>(
            """
            {"bibles": {
              "eng_web": {"copyright": {"attribution": "Public domain", "licenses": [{"license": "public", "url": ""}]}},
              "amh_amh": {"copyright": {"attribution": "UBS", "licenses": [{"license": {
                "forbid_attributionless": true, "forbid_derivatives": true, "forbid_commercial": true,
                "forbid_limitless": false, "forbid_other": false}, "url": ""}]}}
            }}
            """,
        )

        assertEquals("Public domain (public)", manifest.bibles.getValue("eng_web").copyright?.buildNotice())
        assertEquals(
            "UBS (Custom license: non-commercial, no derivatives, attribution required)",
            manifest.bibles.getValue("amh_amh").copyright?.buildNotice(),
        )
    }
}
