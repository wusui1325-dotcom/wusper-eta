package com.wusper.eta.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class AppModel(initial: Snapshot) {
    var started by mutableStateOf(false)
    var tab by mutableStateOf(initial.tab)
    var filter by mutableStateOf(Filter.All)
    var lat by mutableStateOf(initial.lat)
    var lng by mutableStateOf(initial.lng)
    var radius by mutableStateOf(initial.radius)
    var openKey by mutableStateOf(initial.openKey)
    var stops by mutableStateOf(initial.stops)
    var mtr by mutableStateOf(initial.mtr)
    var fav by mutableStateOf(initial.fav)
    var recent by mutableStateOf(initial.recent)
    var help by mutableStateOf<Help?>(null)
    var showMap by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var query by mutableStateOf("")
    var searchMode by mutableStateOf(SearchMode.Route)
    var foundStops by mutableStateOf<List<Stop>>(emptyList())
    var foundRoutes by mutableStateOf<List<Route>>(emptyList())
    var foundMtr by mutableStateOf<List<MtrStop>>(emptyList())
    var along by mutableStateOf<List<AlongStop>>(emptyList())
    var alongTitle by mutableStateOf("")
    var originText by mutableStateOf(initial.origin?.name.orEmpty())
    var destText by mutableStateOf(initial.dest?.name.orEmpty())
    var origin by mutableStateOf(initial.origin)
    var dest by mutableStateOf(initial.dest)
    var planMode by mutableStateOf(initial.planMode)
    var plan by mutableStateOf(initial.plan)
    var planFailed by mutableStateOf(false)
    var planPick by mutableStateOf("")
    var planHits by mutableStateOf<List<Place>>(emptyList())
    var bounds by mutableStateOf<List<BoundHit>>(emptyList())
    var shownBound by mutableStateOf<Map<String, BoundHit>>(emptyMap())
    var boundState by mutableStateOf<Map<String, EtaState>>(emptyMap())
    var boundEta by mutableStateOf<Map<String, List<EtaSlot>>>(emptyMap())
    var mtrSide by mutableStateOf<Map<String, String>>(emptyMap())
    var detail by mutableStateOf<RouteFocus?>(null)
    var detailStops by mutableStateOf<List<DetailStop>>(emptyList())
    var detailNext by mutableStateOf<Map<String, EtaSlot?>>(emptyMap())
    var detailTimes by mutableStateOf<Map<String, List<EtaSlot>>>(emptyMap())
    var detailTransfers by mutableStateOf<Map<String, List<String>>>(emptyMap())
    var detailRest by mutableStateOf<Map<String, List<EtaSlot>>>(emptyMap())
    var routeFav by mutableStateOf<Set<String>>(emptySet())
    var detailReady by mutableStateOf(false)

    var bootAt: Long = SystemClock.elapsedRealtime()
    private var savedAt: Long = initial.at
    private var index: MtrIndex? = null
    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val api by lazy { TransitApi() }
    private val writer by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "wusper-snapshot").apply { isDaemon = true }
        }
    }
    private var etaJob: Job? = null
    private var searchJob: Job? = null
    private var placeJob: Job? = null
    private var planJob: Job? = null
    private var loggedEta = false
    private var lastSearch: SearchBundle? = null
    private var fixListener: LocationListener? = null
    private val flipGen = HashMap<String, Int>()
    private var detailToken = 0
    private var detailJob: Job? = null
    private val transferPending = HashSet<String>()

    val openStop: Stop? get() = stops.firstOrNull { it.key == openKey }

    fun start(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        routeFav = UiPrefs.routeFavorites(app!!)
        scope.launch {
            val ctx = app ?: return@launch
            val indexJob = async(Dispatchers.IO) { MtrIndex.load(ctx) }
            launch { refreshOpenEtas() }
            index = indexJob.await()
            launch { refreshMtr() }
            launch { enrichCoords() }
            pullLocation()
            delay(3_000)
            try {
                androidx.profileinstaller.ProfileInstaller.writeProfile(ctx)
            } catch (_: Throwable) {
            }
        }
    }

    fun select(next: Tab) {
        tab = next
        showMap = false
        showSettings = false
        persist()
    }

    fun openSettings() {
        showSettings = true
        showMap = false
    }

    fun closeSettings() {
        showSettings = false
    }

    fun openMap() {
        showMap = true
    }

    fun closeMap() {
        showMap = false
    }

    fun flipBus(origin: Route) {
        val id = origin.key
        val generation = (flipGen[id] ?: 0) + 1
        flipGen[id] = generation
        if (shownBound.containsKey(id)) {
            shownBound = shownBound - id
            return
        }
        val local = otherBound(bounds, origin)
        if (local != null) {
            adoptBound(id, local, generation)
            return
        }
        scope.launch {
            val remote = try {
                api.otherBound(origin, lat, lng)
            } catch (_: Exception) {
                null
            } ?: return@launch
            if (flipGen[id] != generation) return@launch
            adoptBound(id, remote, generation)
        }
    }

    fun flipMtr(sta: String, line: String) {
        val id = "$sta|$line"
        val item = mtr.firstOrNull { it.sta == sta }?.lines?.firstOrNull { it.code == line }
        val current = mtrSide[id] ?: if (item == null) "up" else preferredMtrSide(item.up, item.down)
        mtrSide = mtrSide + (id to if (current == "up") "down" else "up")
    }

    private fun adoptBound(id: String, hit: BoundHit, generation: Int) {
        if (flipGen[id] != generation) return
        shownBound = shownBound + (id to hit)
        boundState = boundState + (id to EtaState.Loading)
        scope.launch(Dispatchers.IO) {
            val slots = try {
                api.live(hit.asRoute())
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                if (flipGen[id] != generation || shownBound[id] != hit) return@withContext
                if (slots == null) {
                    boundState = boundState + (id to EtaState.Failed)
                } else {
                    boundState = boundState + (id to EtaState.Ready)
                    boundEta = boundEta + (id to slots)
                }
            }
        }
    }

    fun choose(next: Filter) {
        filter = next
    }

    fun radiusIn() {
        radius = if (radius > 400) 400 else 250
        refreshNearby()
    }

    fun radiusOut() {
        radius = if (radius < 400) 400 else 800
        refreshNearby()
    }

    fun refresh() {
        failed = false
        refreshNearby()
        scope.launch { refreshOpenEtas() }
        scope.launch { refreshMtr() }
    }

    fun openDetail(focus: RouteFocus) {
        detailToken += 1
        val token = detailToken
        detail = focus
        detailStops = emptyList()
        detailNext = emptyMap()
        detailTimes = emptyMap()
        detailTransfers = emptyMap()
        detailRest = emptyMap()
        transferPending.clear()
        detailReady = false
        showSettings = false
        showMap = false
        detailJob?.cancel()
        detailJob = scope.launch {
            when (focus) {
                is RouteFocus.Bus -> loadBusDetail(focus.route, token)
                is RouteFocus.Mtr -> loadMtrDetail(focus, token)
            }
        }
    }

    fun closeDetail() {
        detailToken += 1
        detailJob?.cancel()
        detail = null
    }

    fun switchDetailDirection() {
        val focus = detail ?: return
        when (focus) {
            is RouteFocus.Bus -> {
                val local = otherBound(bounds, focus.route)
                if (local != null) {
                    openDetail(RouteFocus.Bus(local.asRoute()))
                    return
                }
                scope.launch {
                    val remote = try {
                        api.otherBound(focus.route, lat, lng)
                    } catch (_: Exception) {
                        null
                    } ?: return@launch
                    if (detail?.favId == focus.favId) openDetail(RouteFocus.Bus(remote.asRoute()))
                }
            }
            is RouteFocus.Mtr -> openDetail(focus.copy(side = if (focus.side == "down") "up" else "down"))
        }
    }

    fun ensureTransfers(stopId: String) {
        if (stopId in detailTransfers || stopId in transferPending) return
        val focus = detail ?: return
        val stop = detailStops.firstOrNull { it.id == stopId } ?: return
        val token = detailToken
        transferPending.add(stopId)
        scope.launch {
            val names = try {
                when (focus) {
                    is RouteFocus.Bus -> busTransfers(focus.route, stop)
                    is RouteFocus.Mtr -> mtrTransfers(focus, stop)
                }
            } catch (_: Exception) {
                emptyList()
            }
            if (token == detailToken) detailTransfers = detailTransfers + (stopId to names)
        }
    }

    private suspend fun busTransfers(route: Route, stop: DetailStop): List<String> {
        val full = api.routesAt(lat, lng, stop.id, route.co) ?: return emptyList()
        return full.routes.map { it.route }
            .filter { it.isNotBlank() && it != route.route }
            .distinct()
            .take(8)
    }

    private fun mtrTransfers(focus: RouteFocus.Mtr, stop: DetailStop): List<String> {
        val station = index?.stations?.firstOrNull { it.sta == stop.id } ?: return emptyList()
        return station.lines.zip(station.lineNames)
            .filter { it.first != focus.code }
            .map { it.second }
            .distinct()
    }

    fun toggleRouteFav() {
        val id = detail?.favId ?: return
        val ctx = app ?: return
        routeFav = if (id in routeFav) routeFav - id else routeFav + id
        UiPrefs.setRouteFavorites(ctx, routeFav)
    }

    private suspend fun loadBusDetail(route: Route, token: Int) {
        val stops = try {
            api.routeStops(route)
        } catch (_: Exception) {
            emptyList()
        }
        if (token != detailToken) return
        detailStops = stops.map { DetailStop(it.id, it.name, it.lat, it.lng, it.seq) }
        detailReady = true
        val gate = Semaphore(6)
        coroutineScope {
        detailStops.forEach { stop ->
            launch(Dispatchers.IO) {
                gate.withPermit {
                    if (token != detailToken) return@withPermit
                    val slots = try {
                        api.live(route.copy(stopId = stop.id))
                    } catch (_: Exception) {
                        null
                    }
                    withContext(Dispatchers.Main) { publishDetailEta(token, stop.id, slots) }
                }
            }
        }
        }
    }

    private suspend fun loadMtrDetail(focus: RouteFocus.Mtr, token: Int) {
        while (index == null && token == detailToken) delay(40)
        val idx = index ?: return
        if (token != detailToken) return
        val stations = idx.onLine(focus.code)
        detailStops = stations.mapIndexed { i, station ->
            DetailStop(station.sta, station.name, station.lat, station.lng, i + 1)
        }
        detailReady = true
        val gate = Semaphore(6)
        coroutineScope {
        detailStops.forEach { stop ->
            launch(Dispatchers.IO) {
                gate.withPermit {
                    if (token != detailToken) return@withPermit
                    val pair = try {
                        api.mtr(focus.code, stop.id, idx::name)
                    } catch (_: Exception) {
                        null
                    }
                    val trains = if (pair == null) {
                        null
                    } else if (focus.side == "down") {
                        pair.second
                    } else {
                        pair.first
                    }
                    val slots = trains?.map { EtaSlot(it.minutes, it.scheduled) }
                    withContext(Dispatchers.Main) { publishDetailEta(token, stop.id, slots) }
                }
            }
        }
        }
    }

    private fun publishDetailEta(token: Int, id: String, slots: List<EtaSlot>?) {
        if (token != detailToken) return
        val ready = slots ?: listOf(EtaSlot(null, false))
        val timed = ready.filter { it.minutes != null }.take(3)
        detailNext = detailNext + (id to (timed.firstOrNull() ?: ready.firstOrNull()))
        detailTimes = detailTimes + (id to if (timed.isEmpty()) listOf(EtaSlot(null, ready.any { it.scheduled })) else timed)
        detailRest = detailRest + (id to ready.drop(1).filter { it.minutes != null })
    }

    fun open(key: String) {
        openKey = key
        val stop = stops.firstOrNull { it.key == key } ?: return
        pushRecent(stop.toSaved())
        scope.launch { refreshOpenEtas() }
        persist()
    }

    fun openStop(stop: Stop) {
        val existing = stops.indexOfFirst { it.key == stop.key }
        stops = if (existing >= 0) {
            stops.map { if (it.key == stop.key) stop.copy(routes = stop.routes.ifEmpty { it.routes }, meters = it.meters) else it }
        } else {
            listOf(stop) + stops
        }
        tab = Tab.Bus
        open(stop.key)
        if (stop.routes.isEmpty() || stop.lat == 0.0) {
            scope.launch {
                val full = try {
                    withContext(Dispatchers.IO) { api.routesAt(lat, lng, stop.id, stop.co) }
                } catch (_: Exception) {
                    null
                }
                if (full != null) {
                    stops = stops.map { current ->
                        if (current.key != stop.key) current
                        else current.copy(
                            name = full.name.ifBlank { current.name },
                            lat = if (full.lat != 0.0) full.lat else current.lat,
                            lng = if (full.lng != 0.0) full.lng else current.lng,
                            routes = full.routes.ifEmpty { current.routes },
                        )
                    }
                    refreshOpenEtas()
                }
            }
        }
    }

    fun openMtr(station: MtrStop) {
        tab = Tab.Mtr
        if (mtr.none { it.sta == station.sta }) mtr = listOf(station) + mtr
        scope.launch { refreshMtr() }
    }

    fun toggleFav(stop: Stop) {
        fav = if (fav.any { it.key == stop.key }) {
            fav.filterNot { it.key == stop.key }
        } else {
            listOf(stop.toSaved()) + fav
        }
        persist()
    }

    fun chooseSearch(mode: SearchMode) {
        if (searchMode == mode) return
        searchMode = mode
        along = emptyList()
        val cached = lastSearch
        if (query.isNotBlank() && cached != null) {
            publishSearch(cached, query)
        } else {
            foundStops = emptyList()
            foundRoutes = emptyList()
            foundMtr = emptyList()
        }
    }

    fun onQuery(value: String) {
        query = value
        searchJob?.cancel()
        along = emptyList()
        if (value.isBlank()) {
            lastSearch = null
            foundStops = emptyList()
            foundRoutes = emptyList()
            foundMtr = emptyList()
            return
        }
        searchJob = scope.launch {
            delay(160)
            val bundle = try {
                api.search(value)
            } catch (_: Exception) {
                failed = foundStops.isEmpty() && foundRoutes.isEmpty()
                return@launch
            }
            if (query != value) return@launch
            failed = false
            val idx = index
            val placed = bundle.copy(
                mtr = bundle.mtr.map { station ->
                    val geo = idx?.stations?.firstOrNull { it.sta == station.sta }
                    if (geo == null) station else station.copy(lat = geo.lat, lng = geo.lng, nameEn = station.nameEn)
                },
            )
            lastSearch = placed
            publishSearch(placed, value)
        }
    }

    private fun publishSearch(bundle: SearchBundle, raw: String) {
        when (searchMode) {
            SearchMode.Route -> {
                foundStops = emptyList()
                foundMtr = emptyList()
                foundRoutes = bundle.routes.filter { matchesRouteQuery(raw, it.route, it.dest) }
            }
            SearchMode.StopName -> {
                foundRoutes = emptyList()
                foundStops = bundle.stops.filter { matchesStopName(raw, it.name, it.nameEn) }
                foundMtr = bundle.mtr.filter { matchesStopName(raw, it.name, it.nameEn) }
            }
        }
    }

    fun openRoute(route: Route) {
        alongTitle = "${route.route}  ${route.dest}"
        along = emptyList()
        scope.launch {
            along = try {
                api.routeStops(route)
            } catch (_: Exception) {
                failed = true
                emptyList()
            }
        }
    }

    fun editPlace(which: String, text: String) {
        planPick = which
        if (which == "o") {
            originText = text
            origin = null
        } else {
            destText = text
            dest = null
        }
        placeJob?.cancel()
        if (text.isBlank()) {
            planHits = emptyList()
            return
        }
        placeJob = scope.launch {
            delay(160)
            val hits = try {
                api.search(text)
            } catch (_: Exception) {
                return@launch
            }
            if (planPick != which) return@launch
            val fromStops = hits.stops.take(6).map { Place(it.name, it.lat, it.lng) }
            val fromMtr = hits.mtr.mapNotNull { station ->
                val geo = index?.stations?.firstOrNull { it.sta == station.sta } ?: return@mapNotNull null
                Place(station.name, geo.lat, geo.lng)
            }
            planHits = (fromStops + fromMtr).take(8)
        }
    }

    fun pickPlace(place: Place) {
        if (planPick == "d") {
            dest = place
            destText = place.name
        } else {
            origin = place
            originText = place.name
        }
        planHits = emptyList()
        persist()
    }

    fun swapEnds() {
        val o = origin
        val d = dest
        val ot = originText
        val dt = destText
        origin = d
        dest = o
        originText = dt
        destText = ot
    }

    fun useHere(which: String) {
        val place = Place("目前位置", lat, lng)
        if (which == "d") {
            dest = place
            destText = place.name
        } else {
            origin = place
            originText = place.name
        }
        pullLocation()
    }

    fun setMode(mode: String) {
        planMode = mode
    }

    fun runPlan() {
        val from = origin ?: return
        val to = dest ?: return
        planFailed = false
        planJob?.cancel()
        planJob = scope.launch {
            val result = try {
                withContext(Dispatchers.IO) { Planner.plan(from, to, planMode) }
            } catch (_: Exception) {
                planFailed = true
                null
            }
            if (result != null) {
                plan = result
                planFailed = false
                persist()
            }
        }
    }

    fun locateGranted() {
        pullLocation(force = true)
    }

    fun permitted(context: Context): Boolean {
        val fine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun close() {
        etaJob?.cancel()
        detailJob?.cancel()
        searchJob?.cancel()
        placeJob?.cancel()
        planJob?.cancel()
        writer.shutdownNow()
    }

    fun flush() {
        val ctx = app ?: return
        val snap = capture()
        try {
            writer.submit { SnapshotStore.write(ctx, snap) }.get(150, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            persist()
        }
    }

    private fun refreshNearby() {
        scope.launch {
            val page = try {
                api.nearby(lat, lng, radius)
            } catch (_: Exception) {
                if (stops.isEmpty()) failed = true
                return@launch
            }
            val next = page.stops
            bounds = page.bounds
            failed = false
            val previous = stops.flatMap { it.routes }.associateBy { it.key }
            val merged = next.map { stop ->
                val old = stops.firstOrNull { it.key == stop.key }
                stop.copy(
                    lat = old?.lat ?: stop.lat,
                    lng = old?.lng ?: stop.lng,
                    routes = stop.routes.map { route ->
                        val prior = previous[route.key]
                        if (prior != null && prior.state == EtaState.Ready) {
                            route.copy(state = prior.state, etas = prior.etas)
                        } else {
                            route
                        }
                    },
                )
            }
            stops = merged
            if (stops.none { it.key == openKey }) openKey = stops.firstOrNull()?.key.orEmpty()
            enrichCoords()
            refreshOpenEtas()
            index?.let { applyMtr(it) }
            refreshMtr()
            persist()
        }
    }

    private suspend fun refreshOpenEtas() {
        val stop = stops.firstOrNull { it.key == openKey } ?: return
        etaJob?.cancel()
        etaJob = scope.launch {
            val gate = Semaphore(6)
            stop.routes.forEach { route ->
                launch(Dispatchers.IO) {
                    gate.withPermit {
                        val slots = try {
                            api.live(route)
                        } catch (_: Exception) {
                            null
                        }
                        withContext(Dispatchers.Main) {
                            applyEta(stop.key, route.key, slots)
                        }
                    }
                }
            }
        }
    }

    private fun applyEta(stopKey: String, routeKey: String, slots: List<EtaSlot>?) {
        var ready = false
        stops = stops.map { stop ->
            if (stop.key != stopKey) stop
            else stop.copy(
                routes = stop.routes.map { route ->
                    if (route.key != routeKey) route
                    else if (slots == null) {
                        if (route.state == EtaState.Ready) route else route.copy(state = EtaState.Failed)
                    } else {
                        ready = true
                        route.copy(state = EtaState.Ready, etas = slots)
                    }
                },
            )
        }
        if (ready) {
            savedAt = System.currentTimeMillis()
            if (!loggedEta) {
                loggedEta = true
                Log.i(TAG, "eta ${SystemClock.elapsedRealtime() - bootAt}ms")
            }
            persist()
        }
    }

    private suspend fun refreshMtr() {
        val idx = index ?: return
        if (mtr.isEmpty()) applyMtr(idx)
        val current = mtr
        current.forEach { station ->
            station.lines.forEach { line ->
                scope.launch(Dispatchers.IO) {
                    val pair = try {
                        api.mtr(line.code, station.sta, idx::name)
                    } catch (_: Exception) {
                        null
                    } ?: return@launch
                    withContext(Dispatchers.Main) {
                        val sideId = "${station.sta}|${line.code}"
                        if (sideId !in mtrSide && (pair.first.isNotEmpty() || pair.second.isNotEmpty())) {
                            mtrSide = mtrSide + (sideId to preferredMtrSide(pair.first, pair.second))
                        }
                        mtr = mtr.map { row ->
                            if (row.sta != station.sta) row
                            else row.copy(
                                lines = row.lines.map { item ->
                                    if (item.code != line.code) item else item.copy(up = pair.first, down = pair.second)
                                },
                            )
                        }
                        savedAt = System.currentTimeMillis()
                        if (!loggedEta) {
                            loggedEta = true
                            Log.i(TAG, "eta ${SystemClock.elapsedRealtime() - bootAt}ms")
                        }
                        persist()
                    }
                }
            }
        }
    }

    private fun applyMtr(idx: MtrIndex) {
        val previous = mtr.associateBy { it.sta }
        mtr = idx.near(lat, lng).map { station ->
            val old = previous[station.sta] ?: return@map station
            station.copy(
                lines = station.lines.map { line ->
                    val prior = old.lines.firstOrNull { it.code == line.code }
                    if (prior == null) line else line.copy(up = prior.up, down = prior.down)
                },
            )
        }
    }

    private fun enrichCoords() {
        val ctxLat = lat
        val ctxLng = lng
        stops.filter { it.lat == 0.0 }.take(8).forEach { stop ->
            scope.launch(Dispatchers.IO) {
                val full = try {
                    api.routesAt(ctxLat, ctxLng, stop.id, stop.co)
                } catch (_: Exception) {
                    null
                } ?: return@launch
                if (full.lat == 0.0) return@launch
                withContext(Dispatchers.Main) {
                    stops = stops.map { current ->
                        if (current.key != stop.key) current
                        else current.copy(lat = full.lat, lng = full.lng, name = current.name.ifBlank { full.name })
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun pullLocation(force: Boolean = false) {
        val ctx = app ?: return
        if (!permitted(ctx)) return
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        val fix = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            try {
                lm.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
        }.maxByOrNull { it.time }
        if (fix != null) {
            val moved = haversineMeters(lat, lng, fix.latitude, fix.longitude)
            if (force || moved > 150) {
                lat = fix.latitude
                lng = fix.longitude
                refreshNearby()
            }
        }
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        fixListener?.let { previous ->
            try {
                lm.removeUpdates(previous)
            } catch (_: Exception) {
            }
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                try {
                    lm.removeUpdates(this)
                } catch (_: Exception) {
                }
                val moved = haversineMeters(lat, lng, location.latitude, location.longitude)
                if (moved > 80) {
                    lat = location.latitude
                    lng = location.longitude
                    refreshNearby()
                }
            }
        }
        fixListener = listener
        try {
            lm.requestLocationUpdates(provider, 0L, 0f, listener, android.os.Looper.getMainLooper())
        } catch (_: Exception) {
        }
    }

    private fun pushRecent(saved: SavedStop) {
        recent = listOf(saved) + recent.filterNot { it.key == saved.key }.take(7)
    }

    private fun persist() {
        val ctx = app ?: return
        val snap = capture()
        try {
            writer.execute { SnapshotStore.write(ctx, snap) }
        } catch (_: Exception) {
        }
    }

    private fun capture(): Snapshot = Snapshot(
        at = savedAt,
        tab = tab,
        lat = lat,
        lng = lng,
        radius = radius,
        openKey = openKey,
        stops = stops,
        mtr = mtr,
        fav = fav,
        recent = recent,
        origin = origin,
        dest = dest,
        planMode = planMode,
        plan = plan,
    )

    private companion object {
        const val TAG = "WusperETA"
    }
}
