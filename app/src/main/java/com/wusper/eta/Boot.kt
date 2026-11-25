package com.wusper.eta

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.SystemClock
import com.wusper.eta.data.MtrIndex
import com.wusper.eta.data.Snapshot
import com.wusper.eta.data.SnapshotStore
import com.wusper.eta.data.centralBoard
import java.io.File
import java.util.concurrent.CountDownLatch

class Boot : ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = context?.applicationContext ?: return true
        startedAt = SystemClock.elapsedRealtime()
        worker = Thread({
            val began = SystemClock.elapsedRealtime()
            try {
                val file = File(ctx.filesDir, "board.json")
                fromCache = file.isFile && file.length() in 32..100_000
                snapshot = SnapshotStore.read(ctx)
            } catch (_: Exception) {
                snapshot = centralBoard()
            } finally {
                parseMs = SystemClock.elapsedRealtime() - began
                ready.countDown()
            }
            try {
                index = MtrIndex.load(ctx)
            } catch (_: Exception) {
            } finally {
                indexReady.countDown()
            }
        }, "wusper-boot").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        @Volatile
        var snapshot: Snapshot? = null
        var parseMs: Long = 0
        var startedAt: Long = 0
        var fromCache: Boolean = false
        private val ready = CountDownLatch(1)
        private val indexReady = CountDownLatch(1)
        private var worker: Thread? = null

        @Volatile
        private var index: MtrIndex? = null

        fun await(): Snapshot {
            val early = snapshot
            if (early != null) return early
            try {
                ready.await()
            } catch (_: InterruptedException) {
            }
            return snapshot ?: centralBoard()
        }

        fun awaitIndex(): MtrIndex? {
            try {
                indexReady.await()
            } catch (_: InterruptedException) {
            }
            return index
        }
    }
}
