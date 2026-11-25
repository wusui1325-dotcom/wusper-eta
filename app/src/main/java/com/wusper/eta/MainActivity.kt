package com.wusper.eta

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.wusper.eta.data.AppModel
import com.wusper.eta.data.SnapshotStore
import com.wusper.eta.data.UiPrefs
import com.wusper.eta.ui.Root

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiPrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiPrefs.loadIntoInk(this)
        val model = held ?: AppModel(SnapshotStore.read(this)).also {
            it.bootAt = android.os.Process.getStartElapsedRealtime()
            held = it
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent { Root(model) }
        val observer = window.decorView.viewTreeObserver
        observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (window.decorView.viewTreeObserver.isAlive) {
                    window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                }
                Log.i(TAG, "first frame ${SystemClock.elapsedRealtime() - model.bootAt}ms")
                window.decorView.post { model.start(applicationContext) }
                return true
            }
        })
    }

    override fun onStop() {
        held?.flush()
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            held?.flush()
            held?.close()
            held = null
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WusperETA"
        private var held: AppModel? = null
    }
}
