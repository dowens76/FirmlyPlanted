package com.firmlyplanted.app.data.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class SmilParserTest {

    // Start of Text/00-RUT-001.xhtml.smil from eBible.org's "Tanakh fluent 08-Ruth" ePub.
    private val ruth1 = """
        <smil xmlns="http://www.w3.org/ns/SMIL" xmlns:epub="http://www.idpf.org/2007/ops" version="3.0">
         <body>
          <seq epub:textref="00-RUT-001.xhtml" epub:type="bodymatter chapter">
            <par><text src="00-RUT-001.xhtml#1a"/><audio clipBegin="0" clipEnd="4.88" src="../Audio/00-RUT-001.mp3"/></par>
            <par><text src="00-RUT-001.xhtml#1b"/><audio clipBegin="4.88" clipEnd="13.4" src="../Audio/00-RUT-001.mp3"/></par>
            <par><text src="00-RUT-001.xhtml#2a"/><audio clipBegin="13.4" clipEnd="24.98" src="../Audio/00-RUT-001.mp3"/></par>
          </seq>
         </body>
        </smil>
    """.trimIndent()

    @Test
    fun parsesClipsInOrder() {
        assertEquals(
            listOf(SmilClip("1a", 0.0, 4.88), SmilClip("1b", 4.88, 13.4), SmilClip("2a", 13.4, 24.98)),
            SmilParser.parse(ruth1),
        )
    }

    @Test
    fun parsesClockFormats() {
        assertEquals(4.88, SmilParser.parseClock("4.88"))
        assertEquals(4.88, SmilParser.parseClock("4.88s"))
        assertEquals(0.88, SmilParser.parseClock("880ms"))
        assertEquals(62.5, SmilParser.parseClock("1:02.5"))
        assertEquals(3662.5, SmilParser.parseClock("1:01:02.5"))
        assertEquals(null, SmilParser.parseClock("soon"))
    }
}
