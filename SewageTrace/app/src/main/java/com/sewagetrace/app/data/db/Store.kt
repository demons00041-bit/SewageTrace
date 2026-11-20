package com.sewagetrace.app.data.db

import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.sewagetrace.app.R
import com.sewagetrace.app.model.Booking
import com.sewagetrace.app.model.Report
import com.sewagetrace.app.model.Ticket
import com.sewagetrace.app.model.TicketLog
import com.sewagetrace.app.model.User

/**
 * 内存列表 ↔ Room 的同步器（write-through + 合并写）。
 *
 *   启动：数据库 → 内存列表（同步，一次）
 *   运行：列表任何写操作 → markDirty(表) → 250 ms 内的多次修改合并
 *         → 主线程拍快照 → 后台线程整表写入（事务）
 *   退到后台：flushNow() 立即写，防止被系统杀掉时丢最后几次修改
 *
 * 快照必须在主线程拍：列表只在主线程修改，后台线程直接遍历会撞上并发修改。
 */
object Store {

    enum class Table { USERS, REPORTS, TICKETS, LOGS, BOOKINGS }

    private const val DEBOUNCE_MS = 250L

    private var db: AppDatabase? = null
    private lateinit var res: ResKeys

    private val main = Handler(Looper.getMainLooper())
    private val io: Handler by lazy { Handler(HandlerThread("st-db").apply { start() }.looper) }

    private val dirty = mutableSetOf<Table>()
    private var sources: Sources? = null

    /** 由 DemoRepo 提供的五个列表 */
    class Sources(
        val users: List<User>,
        val reports: List<Report>,
        val tickets: List<Ticket>,
        val logs: List<TicketLog>,
        val bookings: List<Booking>
    )

    fun init(ctx: Context, sources: Sources) {
        if (db == null) db = AppDatabase.open(ctx)
        res = AndroidResKeys(ctx.resources, ctx.packageName)
        this.sources = sources
    }

    fun isEmpty(): Boolean = (db?.dao()?.userCount() ?: 0) == 0

    class Loaded(
        val users: List<User>,
        val reports: List<Report>,
        val tickets: List<Ticket>,
        val logs: List<TicketLog>,
        val bookings: List<Booking>
    )

    fun load(): Loaded {
        val dao = requireNotNull(db).dao()
        return Loaded(
            users = dao.users().map { it.toModel() },
            reports = dao.reports().map { it.toModel(res) },
            tickets = dao.tickets().map { it.toModel() },
            logs = dao.ticketLogs().map { it.toModel(res, R.string.role_platform, R.string.log_generic) },
            bookings = dao.bookings().map { it.toModel() }
        )
    }

    fun markDirty(table: Table) {
        if (db == null) return   // 初始化前（种子阶段之前）不写
        synchronized(dirty) { dirty += table }
        main.removeCallbacks(flushRunnable)
        main.postDelayed(flushRunnable, DEBOUNCE_MS)
    }

    /** 立即落库（Activity 退到后台时调用） */
    fun flushNow() {
        main.removeCallbacks(flushRunnable)
        flushRunnable.run()
    }

    /** 标记全部表为脏并立即写（重置演示数据后用） */
    fun markAllDirtyAndFlush() {
        synchronized(dirty) { dirty += Table.entries }
        flushNow()
    }

    private val flushRunnable = Runnable {
        val src = sources ?: return@Runnable
        val tables = synchronized(dirty) { dirty.toSet().also { dirty.clear() } }
        if (tables.isEmpty()) return@Runnable
        // 主线程拍快照并转成行
        val u = if (Table.USERS in tables) src.users.map { it.toEntity() } else null
        val r = if (Table.REPORTS in tables) src.reports.map { it.toEntity(res) } else null
        val t = if (Table.TICKETS in tables) src.tickets.map { it.toEntity() } else null
        val l = if (Table.LOGS in tables) src.logs.map { it.toEntity(res) } else null
        val b = if (Table.BOOKINGS in tables) src.bookings.map { it.toEntity() } else null
        val dao = db?.dao() ?: return@Runnable
        io.post { runCatching { dao.replaceAll(u, r, t, l, b) } }
    }

    private class AndroidResKeys(private val r: Resources, private val pkg: String) : ResKeys {
        override fun keyOf(id: Int): String? =
            if (id == 0) null else runCatching { r.getResourceEntryName(id) }.getOrNull()

        @android.annotation.SuppressLint("DiscouragedApi")
        override fun idOf(key: String?, fallback: Int): Int {
            if (key.isNullOrBlank()) return fallback
            val id = r.getIdentifier(key, "string", pkg)
            return if (id != 0) id else fallback
        }
    }
}
