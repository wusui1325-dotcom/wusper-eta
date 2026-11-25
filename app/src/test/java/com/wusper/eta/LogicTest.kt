package com.wusper.eta

import com.wusper.eta.data.DetailStop
import com.wusper.eta.data.BoundHit
import com.wusper.eta.data.EtaState
import com.wusper.eta.data.MtrTrain
import com.wusper.eta.data.Route
import com.wusper.eta.data.citybusOppositeDest
import com.wusper.eta.data.decodePolyline
import com.wusper.eta.data.haversineMeters
import com.wusper.eta.data.isScheduledRemark
import com.wusper.eta.data.dedupeRouteHits
import com.wusper.eta.data.matchesRouteQuery
import com.wusper.eta.data.routeSearchRank
import com.wusper.eta.data.matchesStopName
import com.wusper.eta.data.minutesUntil
import com.wusper.eta.data.atDestinationTerminus
import com.wusper.eta.data.mtrLineOrder
import com.wusper.eta.data.Stop
import com.wusper.eta.data.exitLabel
import com.wusper.eta.data.minuteSpan
import com.wusper.eta.data.stationHits
import com.wusper.eta.data.vehicleKind
import com.wusper.eta.data.fallbackPlanTitle
import com.wusper.eta.data.inStopOrder
import com.wusper.eta.data.nearestStop
import com.wusper.eta.data.parseStopSeq
import com.wusper.eta.data.planCopy
import com.wusper.eta.data.usefulMtrSide
import com.wusper.eta.data.oppositeDir
import com.wusper.eta.data.otherBound
import com.wusper.eta.data.preferredMtrSide
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private fun hit(dir: String, dest: String, meters: Int) = BoundHit(
        co = "KMB",
        route = "101",
        dir = dir,
        serviceType = "1",
        dest = dest,
        stopId = "s$meters",
        stopName = "站",
        routeId = "",
        region = "",
        meters = meters,
        lat = 0.0,
        lng = 0.0,
    )

    @Test
    fun minutesRoundToNearest() {
        val now = Instant.parse("2026-11-25T02:00:00Z")
        assertEquals(7, minutesUntil("2026-11-25T02:07:20Z", now))
        assertEquals(0, minutesUntil("2026-11-25T01:59:40Z", now))
        assertNull(minutesUntil("2026-11-25T01:50:00Z", now))
        assertNull(minutesUntil(null, now))
    }

    @Test
    fun scheduledRemarks() {
        assertTrue(isScheduledRemark("預定班次"))
        assertTrue(isScheduledRemark("未開出"))
        assertTrue(isScheduledRemark("Scheduled"))
        assertFalse(isScheduledRemark(""))
        assertFalse(isScheduledRemark("客滿"))
    }

    @Test
    fun haversineAbout111mPerThousandthDegree() {
        val d = haversineMeters(22.28, 114.15, 22.281, 114.15)
        assertTrue(d in 100.0..120.0)
    }

    @Test
    fun stopSearchUsesNamesOnly() {
        assertTrue(matchesStopName("中環", "中環街市", "CENTRAL MARKET"))
        assertTrue(matchesStopName("admiralty", "金鐘站", "Admiralty Station"))
        assertFalse(matchesStopName("001050", "歷山大廈", "Alexandra House"))
        assertFalse(matchesStopName("A5BF", "置地廣場", "LANDMARK"))
        assertFalse(matchesStopName("  ", "中環", "Central"))
    }

    @Test
    fun routeSearchMatchesCodeOrDest() {
        assertTrue(matchesRouteQuery("12A", "12A", "麥當勞道"))
        assertTrue(matchesRouteQuery("北角", "10", "北角碼頭"))
        assertFalse(matchesRouteQuery("001024", "12A", "麥當勞道"))
        assertEquals(0, routeSearchRank("24", "24", "旺角(循環線)"))
        assertEquals(1, routeSearchRank("24", "24X", "大埔"))
        assertTrue(routeSearchRank("24", "24", "旺角")!! < routeSearchRank("24", "240X", "葵興")!!)
        val routes = listOf(
            Route("KMB", "24", "O", "1", "旺角(循環線)", "", "", "", EtaState.Loading, emptyList()),
            Route("KMB", "24", "O", "2", "旺角(循環線)", "", "", "", EtaState.Loading, emptyList()),
            Route("KMB", "24", "I", "1", "啟業", "", "", "", EtaState.Loading, emptyList()),
        )
        val unique = dedupeRouteHits(routes)
        assertEquals(2, unique.size)
        assertEquals("啟業", unique[1].dest)
    }

    @Test
    fun oppositeBoundIsNearestOtherDirection() {
        val route = Route("KMB", "101", "O", "1", "堅尼地城", "A", "", "", EtaState.Loading, emptyList())
        val far = hit("I", "觀塘(裕民坊)", 220)
        val near = hit("I", "觀塘(裕民坊)", 80)
        val same = hit("O", "堅尼地城", 12)
        val picked = otherBound(listOf(far, same, near), route)
        assertEquals("觀塘(裕民坊)", picked?.dest)
        assertEquals(80, picked?.meters)
        assertEquals("O", oppositeDir("I"))
        assertEquals("2", oppositeDir("1"))
        assertEquals("堅尼地城", citybusOppositeDest("O", "跑馬地 (上)", "堅尼地城", "跑馬地 (上)"))
        assertEquals("down", preferredMtrSide(listOf(MtrTrain(6, "柴灣", false, "1")), listOf(MtrTrain(1, "堅尼地城", false, "2"))))
    }

    @Test
    fun destinationTerminusMatchesTheBoundName() {
        assertTrue(atDestinationTerminus("堅尼地城巴士總站", "堅尼地城"))
        assertTrue(atDestinationTerminus("中環(港澳碼頭)", "中環(港澳碼頭)"))
        assertFalse(atDestinationTerminus("置地廣場", "堅尼地城"))
        assertEquals("down", usefulMtrSide("ISL", "CHW", "up"))
        assertEquals("up", usefulMtrSide("ISL", "KET", "down"))
        assertEquals("up", usefulMtrSide("ISL", "CEN", "up"))
    }

    @Test
    fun eastRailRunsAdmiraltyToLoWu() {
        val order = mtrLineOrder("EAL")
        assertEquals("ADM", order.first())
        assertTrue(order.indexOf("ADM") < order.indexOf("TAW"))
        assertTrue(order.indexOf("TAW") < order.indexOf("LOW"))
        assertTrue(order.contains("LMC"))
    }

    @Test
    fun detailMapOpensOnTheNearestStop() {
        val far = DetailStop("far", "遠", 22.30, 114.20, 1)
        val near = DetailStop("near", "近", 22.2820, 114.1582, 4)
        val blank = DetailStop("none", "無", 0.0, 0.0, 2)
        val hit = nearestStop(listOf(far, blank, near), 22.2819, 114.1580)
        assertEquals("near", hit?.id)
        assertTrue(haversineMeters(22.2819, 114.1580, hit!!.lat, hit.lng) < haversineMeters(22.2819, 114.1580, far.lat, far.lng))
        val first = nearestStop(listOf(near, far), 0.0, 0.0)
        assertEquals("far", first?.id)
    }

    @Test
    fun stopListFollowsNumericSequence() {
        val apiOrder = listOf("1", "10", "11", "12", "13", "14", "2", "3", "4", "5", "6", "7", "8", "9")
        val ordered = apiOrder
            .mapIndexed { index, raw -> parseStopSeq(raw, index + 1) to raw }
            .inStopOrder { it.first }
            .map { it.first }
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14), ordered)
    }

    @Test
    fun stationSearchUsesNamesNotStopIds() {
        val stop = Stop(
            key = "CTB|001050",
            co = "CTB",
            id = "001050",
            name = "歷山大廈, 德輔道中",
            meters = 0,
            lat = 22.28,
            lng = 114.15,
            routes = emptyList(),
            nameEn = "Alexandra House",
        )
        assertTrue(stationHits("001050", listOf(stop), emptyList()) { null }.isEmpty())
        assertEquals("歷山大廈, 德輔道中", stationHits("Alexandra", listOf(stop), emptyList()) { null }.first().name)
        assertEquals("rail", vehicleKind("東鐵綫", "地下鐵 開往金鐘", "", ""))
        assertEquals("gmb", vehicleKind("20C", "", "", "GMB"))
        assertEquals("bus", vehicleKind("71K", "", "", "KMB"))
        assertEquals(45 to 60, minuteSpan(2700.0, 3600.0))
    }

    @Test
    fun planKeepsGoogleWordingAndExitLetters() {
        val spoken = planCopy(
            listOf("乘坐港鐵東鐵綫往羅湖方向", "在大埔墟站下車", "從出口 F 離開"),
            transit = true,
            route = "東鐵綫",
            towards = "F",
        )
        assertEquals("乘坐港鐵東鐵綫往羅湖方向", spoken.first)
        assertEquals("在大埔墟站下車\n從出口 F 離開", spoken.second)
        assertFalse(spoken.second.contains("往 F"))
        assertTrue(exitLabel("出口 F"))
        assertTrue(exitLabel("F"))
        assertFalse(exitLabel("羅湖"))
        assertEquals("東鐵綫 往 羅湖", fallbackPlanTitle(true, "東鐵綫", "羅湖"))
        assertEquals("東鐵綫", fallbackPlanTitle(true, "東鐵綫", "出口 F"))
    }

    @Test
    fun polylineDecodesGoogleSample() {
        val pts = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0].first, 0.01)
        assertEquals(-120.2, pts[0].second, 0.01)
        assertEquals(43.252, pts[2].first, 0.01)
    }
}
