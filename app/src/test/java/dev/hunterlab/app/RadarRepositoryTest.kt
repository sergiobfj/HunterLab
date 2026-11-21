package dev.hunterlab.app

import dev.hunterlab.app.radar.RadarRepository
import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.geo.Polyline
import dev.hunterlab.core.radar.RadarTileData
import dev.hunterlab.core.radar.TileKey
import dev.hunterlab.core.route.HttpGet
import dev.hunterlab.core.route.HttpResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executor

class RadarRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private val route = Polyline(listOf(GeoPoint(0.1, 0.1), GeoPoint(0.4, 0.4)))
    private class Queue : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun runAll() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    @Test fun `cancelamento antes de executar nao consulta rede nem entrega resultado`() {
        val queue = Queue()
        val repo = RadarRepository(temp.root, HttpGet { _, _ -> error("rede após cancelamento") }, queue)
        repo.prepare(route, { 1L }) { fail("callback após cancelamento") }
        repo.cancel()
        queue.runAll()
    }

    @Test fun `cancelar durante HTTP impede tiles seguintes e callback obsoleto`() {
        val queue = Queue()
        lateinit var repo: RadarRepository
        var calls = 0
        repo = RadarRepository(temp.root, HttpGet { _, _ ->
            calls++
            repo.cancel()
            HttpResult.Ok(200, "{\"elements\":[]}")
        }, queue)
        repo.prepare(route, { 1L }) { fail("callback obsoleto") }
        queue.runAll()
        assertEquals(1, calls)
    }

    @Test fun `cache fresco e reutilizado sem downloads e rota nova substitui tarefa pendente`() {
        val queue = Queue()
        val now = 100_000L
        val cover = TileKey.coverRoute(route)
        cover.tiles.forEach { File(temp.root, "${it.id}.json").writeText(RadarTileData(it, now, emptyList()).serialize()) }
        val repo = RadarRepository(temp.root, HttpGet { _, _ -> error("cache fresco não deve baixar") }, queue)
        repo.prepare(route, { now }) { fail("rota substituída") }
        var delivered = 0
        repo.prepare(route, { now }) { assertEquals(cover.tiles.size, it.tiles.size); delivered++ }
        queue.runAll()
        assertEquals(1, delivered)
    }
}
