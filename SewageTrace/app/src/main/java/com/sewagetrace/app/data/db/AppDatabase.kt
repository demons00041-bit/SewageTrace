package com.sewagetrace.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

/*
 * 本地数据库（Room / SQLite）。
 *
 * 表结构（报告「Database design」一节可以直接引用）：
 *
 *   users         1 ── n  reports        （reports.userId）
 *   reports       n ── 1  tickets        （reports.ticketId，可空：草稿/标记没有工单）
 *   tickets       1 ── n  ticket_logs    （ticket_logs.ticketId）
 *   users         1 ── n  bookings       （bookings.userId）
 *
 * 机构（agencies）是静态配置，每次启动从代码种子加载，不落库。
 *
 * 设计取舍：
 * - 枚举存名称（TEXT），不存 ordinal：调整枚举顺序不会让历史数据错位。
 * - 文案资源存「资源名」而不是 R.string 的整数 id：整数 id 每次编译都可能变，
 *   存 id 会导致升级后历史日志显示成别的句子甚至崩溃。
 * - 嵌套列表（证据、插值参数、检测项目）存 JSON 文本：数据量小、只整体读写，
 *   不需要单独建表。
 * - 不加外键约束：演示数据允许「工单先于报告写入」这类顺序，靠应用层保证一致。
 */

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val nickname: String,
    val phone: String,
    val city: String,
    val realName: Boolean,
    val anonymous: Boolean,
    val createdAt: Long,
    val isDemoUser: Boolean,
    val studentEmail: String
)

@Entity(
    tableName = "reports",
    indices = [Index("userId"), Index("ticketId")]
)
data class ReportEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val waterbodyName: String,
    val lat: Double,
    val lng: Double,
    val precision: Int,
    val capturedAt: Long,
    val createdAt: Long,
    /** SeverityLevel 名称，如 "R3"（AI 判定） */
    val level: String,
    val index: Int,
    val confidence: String,
    val suspectedType: String,
    /** List<Evidence> 的 JSON */
    val evidenceJson: String,
    /** 场景描述（CAP-01） */
    val note: String,
    val photoPath: String,
    val status: String,
    val ticketId: String?,
    val userAdjusted: Boolean,
    val aiIndex: Int,
    val aiLevel: String,
    /** 人工等级（CAP-02）；null = 未分级 */
    val manualLevel: String?,
    val levelSource: String,
    val dedupCounted: Boolean,
    val engine: String
)

@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey val id: String,
    /** List<String> 的 JSON */
    val reportIdsJson: String,
    val waterbodyName: String,
    val region: String,
    val agencyId: String?,
    val agencyName: String,
    val stage: String,
    val progress: Int,
    val createdAt: Long,
    val slaDeadline: Long,
    val reporterUserId: String,
    val reportCount: Int,
    val escalated: Boolean,
    val urged: Boolean,
    val disputed: Boolean,
    val thanked: Boolean,
    val referredAgencyName: String?
)

@Entity(tableName = "ticket_logs", indices = [Index("ticketId")])
data class TicketLogEntity(
    @PrimaryKey val id: String,
    val ticketId: String,
    val at: Long,
    /** 角色文案的资源名，如 "role_platform" */
    val actorRoleKey: String,
    val actorName: String?,
    val stage: String,
    val progressAfter: Int,
    /** 日志文案的资源名，如 "log_dispatched" */
    val textKey: String,
    /** List<String> 的 JSON */
    val formatArgsJson: String,
    val photoPath: String?,
    val isDispute: Boolean
)

@Entity(tableName = "bookings", indices = [Index("userId")])
data class BookingEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val agencyId: String,
    val agencyName: String,
    val slotDate: String,
    val slotTime: String,
    val testsJson: String,
    val materialsJson: String,
    val status: String,
    val createdAt: Long,
    val code: String,
    /** List<Pair<String,String>>? 的 JSON；null = 尚无检测报告 */
    val reportJson: String?
)

@Dao
interface AppDao {
    @Query("SELECT * FROM users") fun users(): List<UserEntity>
    @Query("SELECT * FROM reports") fun reports(): List<ReportEntity>
    @Query("SELECT * FROM tickets") fun tickets(): List<TicketEntity>
    @Query("SELECT * FROM ticket_logs ORDER BY at") fun ticketLogs(): List<TicketLogEntity>
    @Query("SELECT * FROM bookings") fun bookings(): List<BookingEntity>

    @Query("SELECT COUNT(*) FROM users") fun userCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertUsers(rows: List<UserEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertReports(rows: List<ReportEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertTickets(rows: List<TicketEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertTicketLogs(rows: List<TicketLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertBookings(rows: List<BookingEntity>)

    @Query("DELETE FROM users") fun clearUsers()
    @Query("DELETE FROM reports") fun clearReports()
    @Query("DELETE FROM tickets") fun clearTickets()
    @Query("DELETE FROM ticket_logs") fun clearTicketLogs()
    @Query("DELETE FROM bookings") fun clearBookings()

    /**
     * 整表快照写入：先清空再插入，在一个事务里完成。
     * 演示数据只有几十行，整表重写比逐行比对差异更简单，也不会出现半写状态。
     */
    @Transaction
    fun replaceAll(
        users: List<UserEntity>?,
        reports: List<ReportEntity>?,
        tickets: List<TicketEntity>?,
        logs: List<TicketLogEntity>?,
        bookings: List<BookingEntity>?
    ) {
        users?.let { clearUsers(); insertUsers(it) }
        reports?.let { clearReports(); insertReports(it) }
        tickets?.let { clearTickets(); insertTickets(it) }
        logs?.let { clearTicketLogs(); insertTicketLogs(it) }
        bookings?.let { clearBookings(); insertBookings(it) }
    }
}

@Database(
    entities = [UserEntity::class, ReportEntity::class, TicketEntity::class, TicketLogEntity::class, BookingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        const val NAME = "sewagetrace.db"

        fun open(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, NAME)
                // 启动时同步读一次（几十行，<10 ms），之后的写入都在后台线程
                .allowMainThreadQueries()
                // 演示版：表结构变了就重建（正式版要写 Migration）
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
