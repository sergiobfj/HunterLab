package dev.hunterlab.core.radar

import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.geo.Polyline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RadarTilesTest {

    private val day = 24L * 60 * 60 * 1000
    private val hour = 60L * 60 * 1000
    private val now = 1_800_000_000_000L
    private val policy = RadarUpdatePolicy()
    private val tile = TileKey.of(RadarTestGeo.ORIGIN)

    private fun data(fetchedAt: Long, t: TileKey = tile, cameras: List<RadarCamera> = emptyList()) =
        RadarTileData(t, fetchedAt, cameras)

    @Test
    fun `tile de 0,2 grau contem o ponto e tem id reversivel`() {
        val p = GeoPoint(-10.5, -48.3)
        val k = TileKey.of(p)
        assertEquals(TileKey(-53, -242), k)
        assertEquals(-10.6, k.south, 1e-12)
        assertEquals(-10.4, k.north, 1e-12)
        assertEquals(-48.4, k.west, 1e-12)
        assertEquals(-48.2, k.east, 1e-12)
        assertTrue(k.contains(p))
        assertEquals("-53_-242", k.id)
        assertEquals(k, TileKey.parse(k.id))
        assertNull(TileKey.parse("x"))
        assertNull(TileKey.parse("999_0"))
        assertEquals(TileKey(0, 0), TileKey.of(GeoPoint(0.0, 0.0)))
        assertEquals(TileKey(449, 899), TileKey.of(GeoPoint(90.0, 180.0)))
        // Retângulo exatamente igual ao tile: um tile só.
        assertEquals(listOf(k), TileKey.coverBox(k.south, k.west, k.north, k.east))
    }

    @Test
    fun `cobertura da rota inclui a margem de 1 km e cruza a borda do tile`() {
        // Rota de 2 km para leste a ~550 m ao sul da borda -10,4.
        val start = GeoPoint(-10.405, -48.35)
        val route = Polyline(listOf(start, GeoPoint(-10.405, -48.3318)))
        val cov = TileKey.coverRoute(route)
        assertEquals(setOf(TileKey(-53, -242), TileKey(-52, -242)), cov.tiles.toSet())
        assertFalse(cov.partial)
        assertEquals(route.lengthM, cov.coveredUntilM)
        // Sem margem fica num tile só.
        assertEquals(listOf(TileKey(-53, -242)), TileKey.coverRoute(route, marginM = 0.0).tiles)
    }

    @Test
    fun `rota acima do limite de tiles e parcial e cobre o inicio`() {
        // ~300 km para leste: bem mais que 12 tiles.
        val route = Polyline(listOf(GeoPoint(-10.5, -48.3), GeoPoint(-10.5, -45.55)))
        val cov = TileKey.coverRoute(route)
        assertEquals(12, cov.tiles.size)
        assertTrue(cov.requiredTileCount > 12)
        assertTrue(cov.partial)
        assertEquals(TileKey.of(route.points.first()), cov.tiles.first())
        assertTrue(cov.coveredUntilM > 0 && cov.coveredUntilM < route.lengthM, "${cov.coveredUntilM}")
        // Tudo até coveredUntilM está nos tiles escolhidos.
        assertTrue(cov.tiles.contains(TileKey.of(route.pointAt(cov.coveredUntilM))))
        assertFalse(TileKey.coverRoute(route, maxTiles = 100).partial)
        // Corredor usa menos tiles que o retângulo envolvente numa diagonal.
        val diagonal = Polyline(listOf(GeoPoint(-10.5, -48.3), GeoPoint(-11.5, -47.3)))
        assertTrue(
            TileKey.coverRoute(diagonal, maxTiles = 1_000).tiles.size < TileKey.coverRouteBoundingBox(diagonal).size,
        )
    }

    @Test
    fun `politica de validade de 30 dias e intervalo minimo de 1 h`() {
        val fresh = RadarTileState(tile, data(now - 29 * day), lastAttemptMs = now - 29 * day)
        val old = RadarTileState(tile, data(now - 31 * day), lastAttemptMs = now - 31 * day)
        val missing = RadarTileState(tile)
        assertEquals(TileFreshness.FRESH, policy.freshness(fresh, now))
        assertEquals(TileFreshness.STALE, policy.freshness(old, now))
        assertEquals(TileFreshness.MISSING, policy.freshness(missing, now))
        assertEquals(TileFreshness.STALE, policy.freshness(RadarTileState(tile, data(now + 3 * day)), now)) // relógio

        assertFalse(policy.shouldFetch(fresh, now))
        assertTrue(policy.shouldFetch(old, now))
        assertTrue(policy.shouldFetch(missing, now))
        val triedRecently = missing.copy(lastAttemptMs = now - 30 * 60 * 1000, lastAttemptFailed = true)
        assertFalse(policy.shouldFetch(triedRecently, now))
        assertTrue(policy.shouldFetch(triedRecently, now + 31 * 60 * 1000))
        // Forçar ignora a validade, mas nunca o intervalo mínimo.
        assertTrue(policy.shouldFetch(fresh, now, force = true))
        assertFalse(policy.shouldFetch(fresh.copy(lastAttemptMs = now - hour / 2), now, force = true))
    }

    @Test
    fun `falha mantem o dado antigo como desatualizado e sucesso renova`() {
        val cam = RadarTestGeo.camera(1, 0.0, 0.0)
        val oldData = data(now - 40 * day, cameras = listOf(cam))
        val failed = policy.onFetchFailure(RadarTileState(tile, oldData, now - 40 * day), now)
        assertEquals(oldData, failed.data)
        assertEquals(TileFreshness.STALE, policy.freshness(failed, now))
        assertFalse(policy.shouldFetch(failed, now + hour - 1))
        assertTrue(policy.shouldFetch(failed, now + hour))

        // Mesmo um dado ainda no prazo passa a "desatualizado" depois de uma atualização que falhou.
        val recentFailed = policy.onFetchFailure(RadarTileState(tile, data(now - day)), now)
        assertEquals(TileFreshness.STALE, policy.freshness(recentFailed, now))

        val renewed = policy.onFetchSuccess(failed, data(now + hour, cameras = listOf(cam)), now + hour)
        assertEquals(TileFreshness.FRESH, policy.freshness(renewed, now + hour))
        assertFalse(renewed.lastAttemptFailed)
        assertThrows<IllegalArgumentException> { RadarTileState(tile, data(now, t = TileKey(0, 0))) }
    }

    @Test
    fun `status de cobertura da rota`() {
        val other = TileKey(tile.latIndex, tile.lonIndex + 1)
        val cov = RouteTileCoverage(listOf(tile, other), 2, 1_000.0, 1_000.0)
        fun st(k: TileKey, fetchedAt: Long?) = k to RadarTileState(k, fetchedAt?.let { data(it, k) })
        assertEquals(CoverageStatus.COMPLETE, policy.coverageOf(cov, mapOf(st(tile, now), st(other, now)), now))
        assertEquals(CoverageStatus.STALE, policy.coverageOf(cov, mapOf(st(tile, now), st(other, now - 31 * day)), now))
        assertEquals(CoverageStatus.PARTIAL, policy.coverageOf(cov, mapOf(st(tile, now - 31 * day)), now))
        assertEquals(CoverageStatus.NONE, policy.coverageOf(cov, emptyMap(), now))
        val cut = cov.copy(requiredTileCount = 5)
        assertEquals(CoverageStatus.PARTIAL, policy.coverageOf(cut, mapOf(st(tile, now), st(other, now)), now))
        assertEquals(listOf(other), policy.tilesToFetch(cov, mapOf(st(tile, now)), now))

        assertEquals("Sem dados de radar", CoverageStatus.NONE.label)
        assertEquals("Radares atualizados", CoverageStatus.COMPLETE.label)
        assertEquals("Radares desatualizados", CoverageStatus.STALE.label)
        assertEquals("Cobertura parcial de radares", CoverageStatus.PARTIAL.label)
    }

    @Test
    fun `serializacao ida e volta preserva tudo`() {
        val cams = listOf(
            RadarCamera("osm:node/1", GeoPoint(-10.5000001, -48.3000002), RadarKind.FIXED, 60, 90.0),
            RadarCamera("osm:node/2", GeoPoint(-10.51, -48.31), RadarKind.AVERAGE_SPEED, null, null),
            RadarCamera("osm:node/3", GeoPoint(0.00001, -0.00002), RadarKind.TRAFFIC_LIGHT, 40, 247.5),
        )
        val original = RadarTileData(tile, now, cams)
        val json = original.serialize()
        assertTrue("\"schema\":2" in json, json)
        assertTrue("© colaboradores do OpenStreetMap (ODbL)" in json, json)
        assertEquals(original, RadarTileData.deserialize(json))
        val empty = RadarTileData(tile, now, emptyList(), attribution = "Fonte \"teste\"\n")
        assertEquals(empty, RadarTileData.deserialize(empty.serialize()))
    }

    @Test
    fun `versao desconhecida ou cache corrompido e rejeitado`() {
        val json = RadarTileData(tile, now, listOf(RadarTestGeo.camera(1, 0.0, 0.0))).serialize()
        val e = assertThrows<IllegalArgumentException> { RadarTileData.deserialize(json.replace("\"schema\":2", "\"schema\":999")) }
        assertTrue("desconhecida" in e.message!!, e.message)
        assertThrows<IllegalArgumentException> { RadarTileData.deserialize(json.replace("\"schema\":2,", "")) }
        assertThrows<IllegalArgumentException> { RadarTileData.deserialize(json.replace("\"FIXED\"", "\"LASER\"")) }
        assertThrows<IllegalArgumentException> { RadarTileData.deserialize(json.dropLast(5)) }
        assertThrows<IllegalArgumentException> { RadarTileData.deserialize("[]") }
    }

    @Test
    fun `cameras de varios tiles sao combinadas sem repeticao`() {
        val a = RadarTestGeo.camera(1, 0.0, 0.0)
        val b = RadarTestGeo.camera(2, 10.0, 0.0)
        val other = TileKey(tile.latIndex, tile.lonIndex + 1)
        val merged = RadarTileData.mergeCameras(listOf(data(1L, cameras = listOf(a, b)), data(2L, other, listOf(b))))
        assertEquals(listOf("osm:node/1", "osm:node/2"), merged.map { it.id })
    }
}
