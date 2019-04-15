package org.peercast.pecaviewer.util

import android.content.Context
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.peercast.pecaviewer.R
import java.util.*
import kotlin.math.abs

object DateUtils : KoinComponent {
    private val c by inject<Context>()

    private val Calendar.timeInMillisOrZero get() =
        try {
            timeInMillis
        } catch (e: IllegalArgumentException){
            0L
        }


    fun parse(s: String): Long {
        val cl = Calendar.getInstance(Locale.JAPAN)
        RE_DATETIME_1.find(s)?.let { ma ->
            val a = ma.groupValues.drop(1).map { it.toInt() }
            cl.set(a[0], a[1] - 1, a[2], a[3], a[4], a[5])
            //Timber.d("d=$cl")
            return  cl.timeInMillisOrZero
        }

        return 0L
    }

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val YEAR = 365 * DAY

    fun formatElapsedTime(t: Long): String {
        val t = abs(t)

        return when {
            t > YEAR -> c.getString(R.string.et_years_fmt, t / YEAR)
            t > DAY -> c.getString(R.string.et_days_fmt, t / DAY)
            t > HOUR -> c.getString(R.string.et_hours_fmt, t / HOUR)
            t > MINUTE -> c.getString(R.string.et_minutes_fmt, t / MINUTE)
            else -> c.getString(R.string.et_seconds_fmt, t / 1000)
        }
    }

    private val RE_DATETIME_1 = """(20\d\d)/([01]?\d)/(\d\d).*(\d\d):(\d\d):(\d\d)""".toRegex()
}