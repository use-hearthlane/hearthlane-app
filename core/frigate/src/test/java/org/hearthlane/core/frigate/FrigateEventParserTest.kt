package org.hearthlane.core.frigate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrigateEventParserTest {

    @Test
    fun `parses a normal event with all fields`() {
        val json = """[{
            "id":"1787072293.499881-q04v5h",
            "camera":"backyard",
            "label":"person",
            "zones":["yard"],
            "start_time":1787072293.499881,
            "end_time":1787072302.224223,
            "has_clip":true,
            "has_snapshot":true,
            "data":{"box":[0.1,0.2],"score":0.9}
        }]"""

        val events = FrigateEventParser.parseList(json)

        assertEquals(1, events.size)
        val event = events[0]
        assertEquals("1787072293.499881-q04v5h", event.id)
        assertEquals("backyard", event.cameraId)
        assertEquals("person", event.label)
        assertEquals(1787072293.499881, event.startTime, 0.0)
        assertEquals(1787072302.224223, event.endTime!!, 0.0)
        assertTrue(event.hasClip)
        assertTrue(event.hasSnapshot)
        assertEquals(listOf("yard"), event.zones)
    }

    @Test
    fun `end_time null is preserved for an in-progress event`() {
        val json = """[{
            "id":"1-abc","camera":"hall","label":"person",
            "start_time":1787072293.5,"end_time":null,
            "has_clip":true,"has_snapshot":true,"zones":[]
        }]"""

        val event = FrigateEventParser.parseList(json).single()

        assertNull("an ongoing event has a null end_time", event.endTime)
    }

    @Test
    fun `missing end_time is parsed as null`() {
        val json = """[{
            "id":"1-abc","camera":"hall","label":"person",
            "start_time":1787072293.5,
            "has_clip":true,"has_snapshot":true,"zones":[]
        }]"""

        assertNull(FrigateEventParser.parseList(json).single().endTime)
    }

    @Test
    fun `parses zones when present`() {
        val json = """[{
            "id":"1-abc","camera":"gate","label":"car",
            "start_time":1.0,"end_time":2.0,
            "has_clip":true,"has_snapshot":true,
            "zones":["driveway","front"]
        }]"""

        assertEquals(
            listOf("driveway", "front"),
            FrigateEventParser.parseList(json).single().zones,
        )
    }

    @Test
    fun `empty zones list is preserved`() {
        val json = """[{
            "id":"1-abc","camera":"gate","label":"car",
            "start_time":1.0,"end_time":2.0,
            "has_clip":true,"has_snapshot":true,"zones":[]
        }]"""

        assertTrue(FrigateEventParser.parseList(json).single().zones.isEmpty())
    }

    @Test
    fun `missing zones defaults to an empty list`() {
        val json = """[{
            "id":"1-abc","camera":"gate","label":"car",
            "start_time":1.0,"end_time":2.0,
            "has_clip":true,"has_snapshot":true
        }]"""

        assertTrue(FrigateEventParser.parseList(json).single().zones.isEmpty())
    }

    @Test
    fun `has_clip true is parsed`() {
        val json = """[{"id":"1-a","camera":"x","start_time":1.0,"has_clip":true,"has_snapshot":false,"zones":[]}]"""
        assertTrue(FrigateEventParser.parseList(json).single().hasClip)
    }

    @Test
    fun `has_clip false is parsed`() {
        val json = """[{"id":"1-a","camera":"x","start_time":1.0,"has_clip":false,"has_snapshot":false,"zones":[]}]"""
        assertFalse(FrigateEventParser.parseList(json).single().hasClip)
    }

    @Test
    fun `missing has_clip defaults to false`() {
        val json = """[{"id":"1-a","camera":"x","start_time":1.0,"has_snapshot":false,"zones":[]}]"""
        assertFalse(FrigateEventParser.parseList(json).single().hasClip)
    }

    @Test
    fun `has_snapshot true is parsed`() {
        val json = """[{"id":"1-a","camera":"x","start_time":1.0,"has_clip":false,"has_snapshot":true,"zones":[]}]"""
        assertTrue(FrigateEventParser.parseList(json).single().hasSnapshot)
    }

    @Test
    fun `has_snapshot false is parsed`() {
        val json = """[{"id":"1-a","camera":"x","start_time":1.0,"has_clip":false,"has_snapshot":false,"zones":[]}]"""
        assertFalse(FrigateEventParser.parseList(json).single().hasSnapshot)
    }

    @Test
    fun `missing has_snapshot defaults to false`() {
        val json = """[{"id":"1-a","camera":"x","start_time":1.0,"has_clip":false,"zones":[]}]"""
        assertFalse(FrigateEventParser.parseList(json).single().hasSnapshot)
    }

    @Test
    fun `label null is parsed as null`() {
        val json = """[{"id":"1-a","camera":"x","label":null,"start_time":1.0,"end_time":2.0,"zones":[]}]"""
        assertNull(FrigateEventParser.parseList(json).single().label)
    }

    @Test
    fun `multiple events parse in document order`() {
        val json = """
            [
                {"id":"2-b","camera":"hall","start_time":2.0,"end_time":3.0,"zones":[]},
                {"id":"1-a","camera":"hall","start_time":1.0,"end_time":2.0,"zones":[]}
            ]
        """.trimIndent()

        assertEquals(
            listOf("2-b", "1-a"),
            FrigateEventParser.parseList(json).map { it.id },
        )
    }

    @Test
    fun `extra payload fields are tolerated`() {
        val json = """[{
            "id":"1-a","camera":"backyard","label":"person",
            "start_time":1.0,"end_time":2.0,"has_clip":true,"has_snapshot":true,"zones":[],
            "plus_id":null,"model_hash":"abc","detector_type":"openvino",
            "data":{"box":[0,0,0,0],"region":[],"score":0.9,"path_data":[[[0,0],1.0]]}
        }]"""

        val event = FrigateEventParser.parseList(json).single()

        assertEquals("1-a", event.id)
        assertEquals("backyard", event.cameraId)
        assertTrue(event.hasClip)
    }

    @Test
    fun `a malformed entry is skipped without discarding the page`() {
        val json = """
            [
                {"id":"1-a","camera":"x","start_time":1.0,"end_time":2.0,"zones":[]},
                "not an object",
                {"id":"2-b","camera":"x","start_time":2.0,"end_time":3.0,"zones":[]}
            ]
        """.trimIndent()

        val events = FrigateEventParser.parseList(json)

        assertEquals(listOf("1-a", "2-b"), events.map { it.id })
    }

    @Test
    fun `an entry without id or start_time is skipped`() {
        val json = """
            [
                {"camera":"x","start_time":1.0,"end_time":2.0,"zones":[]},
                {"id":"2-b","camera":"x","end_time":3.0,"zones":[]}
            ]
        """.trimIndent()

        assertTrue(FrigateEventParser.parseList(json).isEmpty())
    }

    @Test
    fun `parseSingle returns null for a non-object payload`() {
        assertNull(FrigateEventParser.parseSingle("[]"))
        assertNull(FrigateEventParser.parseSingle("""[{}]"""))
    }

    @Test
    fun `parseSingle returns the parsed event for a valid object`() {
        val json = """{"id":"1-a","camera":"x","start_time":1.0,"end_time":2.0,"zones":[]}"""
        assertEquals("1-a", FrigateEventParser.parseSingle(json)?.id)
    }

    @Test
    fun `invalid JSON raises a controlled parse exception`() {
        var thrown: Exception? = null
        try {
            FrigateEventParser.parseList("not json")
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue("non-JSON payload must raise", thrown is IllegalArgumentException)
    }

    @Test
    fun `empty array yields an empty list`() {
        assertTrue(FrigateEventParser.parseList("[]").isEmpty())
    }
}
