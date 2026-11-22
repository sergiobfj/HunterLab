package dev.hunterlab.app

import dev.hunterlab.app.radar.*
import dev.hunterlab.core.geo.*
import dev.hunterlab.core.radar.*
import dev.hunterlab.core.route.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executor

class RadarSourceIntegrationTest {
    @get:Rule val temp=TemporaryFolder()
    private val point=GeoPoint(0.1,0.1)
    private val route=Polyline(listOf(point,GeoPoint(0.101,0.101)))
    private val tile=TileKey.of(point)
    private val now=100_000L
    private val direct=Executor { it.run() }
    private fun camera(id:String,limit:Int?=60)=RadarCamera(id,point,RadarKind.FIXED,limit,null,source=id)
    private fun cache(cameras:List<RadarCamera>) { File(temp.root,"${tile.id}.json").writeText(RadarTileData(tile,now,cameras).serialize()) }
    private fun data(repo:RadarRepository,time:Long=now):RadarRepository.RouteRadarData {
        var result:RadarRepository.RouteRadarData?=null
        repo.prepare(route,{time}) { result=it };return requireNotNull(result)
    }
    @Test fun `fresh Pardal fills a missing OSM limit without issuing extra HTTP requests`() {
        cache(listOf(camera("OSM",null)))
        val repo=RadarRepository(temp.root,HttpGet { _,_->error("fresh cache") },direct,
            offlineSource={OfflineRadarData(listOf(camera("Pardal/DER-PE")),now,"Pardal fixture")})
        val result=data(repo);assertEquals(1,result.tiles.single().cameras.size)
        assertEquals(60,result.tiles.single().cameras.single().maxSpeedKmh)
        assertEquals(CoverageStatus.COMPLETE,result.coverage);assertTrue(repo.dataSourceLabel.contains("Pardal fixture"))
    }
    @Test fun `current source disagreement is retained as unknown in route session`() {
        cache(listOf(camera("OSM",40)))
        val repo=RadarRepository(temp.root,HttpGet { _,_->error("fresh cache") },direct,
            offlineSource={OfflineRadarData(listOf(camera("Pardal/DER-PE",60)),now,"fixture")})
        val result=data(repo);val c=result.tiles.single().cameras.single()
        assertNull(c.maxSpeedKmh);assertEquals(RadarLimitStatus.CONFLICT,c.speedLimit.status)
        val session=RouteRadarSession();session.setRouteFromTiles(route,result.tiles,result.coverage)
        assertNull(session.matches.single().camera.maxSpeedKmh)
    }
    @Test fun `stale packaged data retains positions but never revives an expired numeric limit`() {
        cache(emptyList())
        val repo=RadarRepository(temp.root,HttpGet { _,_->error("fresh cache") },direct,
            offlineSource={OfflineRadarData(listOf(camera("Pardal/OSM")),now-15L*24*60*60*1000,"old fixture")})
        val result=data(repo);assertEquals(CoverageStatus.STALE,result.coverage)
        assertNull(result.tiles.single().cameras.single().maxSpeedKmh)
    }
    @Test fun `network failure can still return partial public offline coverage`() {
        val repo=RadarRepository(temp.root,HttpGet { _,_->HttpResult.Failed(HttpFailureKind.NETWORK,"fixture") },direct,
            offlineSource={OfflineRadarData(listOf(camera("Pardal/DER-PE")),now,"fixture")})
        val result=data(repo);assertEquals(CoverageStatus.PARTIAL,result.coverage)
        assertEquals(1,result.failed);assertEquals(60,result.tiles.single().cameras.single().maxSpeedKmh)
    }
    @Test fun `cancellation while loading offline data invalidates callback and prevents downloads`() {
        lateinit var repo:RadarRepository
        repo=RadarRepository(temp.root,HttpGet { _,_->error("cancelled") },direct,offlineSource={
            repo.cancel();OfflineRadarData(listOf(camera("Pardal/OSM")),now,"fixture")})
        repo.prepare(route,{now}) { fail("obsolete result") }
    }
    @Test fun `cross tile border duplicate is reconciled globally before tile assignment`() {
        val path=Polyline(listOf(GeoPoint(0.199,0.1),GeoPoint(0.201,0.1)))
        val osm=camera("OSM").copy(point=GeoPoint(0.200001,0.1))
        val local=camera("Pardal/OSM").copy(point=GeoPoint(0.199999,0.1))
        TileKey.coverRoute(path).tiles.forEach { tile ->
            File(temp.root,"${tile.id}.json").writeText(RadarTileData(tile,now,
                if (TileKey.of(osm.point)==tile) listOf(osm) else emptyList()).serialize())
        }
        val repo=RadarRepository(temp.root,HttpGet { _,_->error("fresh cache") },direct,
            offlineSource={OfflineRadarData(listOf(local),now,"fixture")})
        var result:RadarRepository.RouteRadarData?=null
        repo.prepare(path,{now}) { result=it }
        val cameras=requireNotNull(result).tiles.flatMap { it.cameras }
        assertEquals(1,cameras.size);assertEquals("OSM",cameras.single().id)
    }

}
