package com.homelab.poc.core.frigate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrigateCameraConfigParserTest {

    @Test
    fun `friendly name present maps display name to the friendly name`() {
        val payload = """{"cameras":{"backyard":{"friendly_name":"Quintal dos fundos","enabled":true}}}"""

        val cameras = FrigateCameraConfigParser.parse(payload)

        assertEquals(1, cameras.size)
        val domain = FrigateCameraConfigParser.toDomain(cameras[0])
        assertEquals("backyard", domain.id)
        assertEquals("Quintal dos fundos", domain.displayName)
        assertTrue(domain.enabled)
    }

    @Test
    fun `absent friendly name falls back to the camera key`() {
        val payload = """{"cameras":{"front_door":{"enabled":true}}}"""

        val cameras = FrigateCameraConfigParser.parse(payload)

        assertEquals(1, cameras.size)
        assertEquals("front_door", FrigateCameraConfigParser.toDomain(cameras[0]).displayName)
    }

    @Test
    fun `null friendly name falls back to the camera key`() {
        val payload = """{"cameras":{"front_door":{"friendly_name":null,"enabled":true}}}"""

        val cameras = FrigateCameraConfigParser.parse(payload)

        assertEquals(1, cameras.size)
        assertEquals("front_door", FrigateCameraConfigParser.toDomain(cameras[0]).displayName)
    }

    @Test
    fun `empty friendly name falls back to the camera key`() {
        val payload = """{"cameras":{"front_door":{"friendly_name":"","enabled":true}}}"""

        val cameras = FrigateCameraConfigParser.parse(payload)

        assertEquals(1, cameras.size)
        assertEquals("front_door", FrigateCameraConfigParser.toDomain(cameras[0]).displayName)
    }

    @Test
    fun `whitespace-only friendly name falls back to the camera key`() {
        val payload = """{"cameras":{"front_door":{"friendly_name":"   ","enabled":true}}}"""

        val cameras = FrigateCameraConfigParser.parse(payload)

        assertEquals("front_door", FrigateCameraConfigParser.toDomain(cameras[0]).displayName)
    }

    @Test
    fun `multiple cameras are discovered in document order`() {
        val payload = """
            {
                "cameras":{
                    "backyard":{"friendly_name":"Quintal","enabled":true},
                    "hall":{"friendly_name":"Corredor","enabled":true},
                    "gate":{"friendly_name":"Portão","enabled":true}
                }
            }
        """.trimIndent()

        val cameras = FrigateCameraConfigParser.parse(payload)

        assertEquals(listOf("backyard", "hall", "gate"), cameras.map { it.id })
        assertEquals(listOf("Quintal", "Corredor", "Portão"), cameras.map { it.friendlyName })
    }

    @Test
    fun `toDomain carries the resolved playable flag`() {
        val payload = """{"cameras":{"backyard":{"friendly_name":"Quintal","enabled":true}}}"""

        val cameras = FrigateCameraConfigParser.parse(payload)

        val playable = FrigateCameraConfigParser.toDomain(cameras[0], playable = true)
        assertTrue("playable must be carried onto the domain model", playable.playable)
        val notPlayable = FrigateCameraConfigParser.toDomain(cameras[0], playable = false)
        assertFalse(notPlayable.playable)
    }

    @Test
    fun `enabled state is parsed and disabled is supported`() {
        val payload = """{"cameras":{"cam_on":{"enabled":true},"cam_off":{"enabled":false}}}"""

        val cameras = FrigateCameraConfigParser.parse(payload)
            .associateBy { it.id }

        assertTrue(cameras["cam_on"]!!.enabled)
        assertFalse(cameras["cam_off"]!!.enabled)
    }

    @Test
    fun `missing enabled defaults to true`() {
        val payload = """{"cameras":{"front_door":{"friendly_name":"Porta"}}}"""

        val cameras = FrigateCameraConfigParser.parse(payload)

        assertEquals(1, cameras.size)
        assertTrue(cameras[0].enabled)
    }

    @Test
    fun `config without cameras yields an empty list`() {
        assertTrue(FrigateCameraConfigParser.parse("{}").isEmpty())
        assertTrue(
            FrigateCameraConfigParser.parse("""{"cameras":{}}""").isEmpty(),
        )
    }

    @Test
    fun `cameras value that is not an object yields an empty list`() {
        assertTrue(FrigateCameraConfigParser.parse("""{"cameras":null}""").isEmpty())
        assertTrue(FrigateCameraConfigParser.parse("""{"cameras":[]}""").isEmpty())
    }

    @Test
    fun `extra fields at every level are tolerated`() {
        val payload = """
            {
                "version":"0.17.1-416a9b7",
                "mqtt":{"host":"mqtt","topic_prefix":"frigate"},
                "cameras":{
                    "backyard":{
                        "name":"backyard",
                        "friendly_name":"Quintal dos fundos",
                        "enabled":true,
                        "enabled_in_config":true,
                        "zones":{"yard":{"color":[0,255,0],"coordinates":"..."}},
                        "ffmpeg":{"inputs":[{"path":"rtsp://x","roles":["detect"]}]},
                        "detect":{"width":1280,"height":720},
                        "record":{"enabled":true},
                        "onvif":{"host":"x","port":80}
                    }
                }
            }
        """.trimIndent()

        val cameras = FrigateCameraConfigParser.parse(payload)

        assertEquals(1, cameras.size)
        assertEquals("Quintal dos fundos", cameras[0].friendlyName)
        assertTrue(cameras[0].enabled)
    }

    @Test
    fun `invalid JSON raises a controlled parse exception`() {
        var thrown: Exception? = null
        try {
            FrigateCameraConfigParser.parse("not json")
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue("non-JSON payload must raise", thrown is IllegalArgumentException)
    }

    @Test
    fun `truncated payload raises a controlled parse exception`() {
        var thrown: Exception? = null
        try {
            FrigateCameraConfigParser.parse("""{"cameras":{"backyard":{"enabled":true""")
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue("truncated payload must raise", thrown is IllegalArgumentException)
    }

    @Test
    fun `escaped characters in friendly name are decoded`() {
        val payload = """{"cameras":{"door":{"friendly_name":"Porta \"Principal\"","enabled":true}}}"""

        val cameras = FrigateCameraConfigParser.parse(payload)

        assertEquals("Porta \"Principal\"", cameras[0].friendlyName)
    }

    @Test
    fun `displayName helper never transforms the key`() {
        assertEquals("backyard", FrigateCameraConfigParser.displayName("backyard", null))
        assertEquals("backyard", FrigateCameraConfigParser.displayName("backyard", ""))
        assertEquals("front_door", FrigateCameraConfigParser.displayName("front_door", ""))
        assertEquals("My Name", FrigateCameraConfigParser.displayName("backyard", "My Name"))
    }

    @Test
    fun `blank camera keys are ignored`() {
        val payload = """{"cameras":{"   ":{"enabled":true},"backyard":{"enabled":true}}}"""

        val cameras = FrigateCameraConfigParser.parse(payload)

        assertEquals(listOf("backyard"), cameras.map { it.id })
    }
}
