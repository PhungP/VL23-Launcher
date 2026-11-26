package vn.navara.launcher

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

// ===== Lưu trữ và quản lý hồ sơ xe do người dùng tạo =====
// Hồ sơ dựng sẵn nằm trong Vehicles.kt và không bao giờ bị sửa / xoá. Hồ sơ người dùng lưu trong một tệp JSON (schema có phiên bản),
// ghi nguyên tử (ghi tệp tạm rồi đổi tên) và giữ bản trước đó làm .bak để khôi phục khi tệp chính hỏng.

interface ProfileStore {
    fun read(): String?
    fun readBackup(): String?
    @Throws(IOException::class) fun write(text: String)
}

class FileProfileStore(dir: File) : ProfileStore {
    private val main = File(dir, "vehicle_profiles.json")
    private val bak = File(dir, "vehicle_profiles.json.bak")
    private val tmp = File(dir, "vehicle_profiles.json.tmp")
    override fun read(): String? = runCatching { if (main.exists()) main.readText(Charsets.UTF_8) else null }.getOrNull()
    override fun readBackup(): String? = runCatching { if (bak.exists()) bak.readText(Charsets.UTF_8) else null }.getOrNull()
    override fun write(text: String) {
        main.parentFile?.mkdirs()
        FileOutputStream(tmp).use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
        if (main.exists()) { bak.delete(); if (!main.renameTo(bak)) throw IOException("Không tạo được bản dự phòng") }
        if (!tmp.renameTo(main)) {
            if (bak.exists()) bak.renameTo(main)   // hoàn tác: trả bản cũ về
            throw IOException("Không lưu được hồ sơ xe")
        }
    }
}

class ProfileException(message: String, val errors: List<String> = emptyList()) : Exception(message)

class ProfileRepository(
    private val store: ProfileStore,
    private val idGen: () -> String = { "u_" + UUID.randomUUID().toString().replace("-", "").take(10) },
    private val reservedIds: () -> Set<String> = { emptySet() },   // mã của hồ sơ dựng sẵn
) {
    private val _profiles = MutableStateFlow<List<ProfileData>>(emptyList())
    val profiles: StateFlow<List<ProfileData>> = _profiles
    /** Cảnh báo khi nạp (ví dụ tệp chính hỏng phải dùng bản dự phòng); null = bình thường. */
    @Volatile var loadWarning: String? = null; private set

    fun newId(): String { var i = idGen(); val used = _profiles.value.map { it.id }.toSet() + reservedIds(); while (i in used) i = idGen(); return i }

    @Synchronized fun load() {
        loadWarning = null
        val main = store.read()
        if (main == null && store.readBackup() == null) { _profiles.value = emptyList(); return }
        var list = main?.let { decode(it) }
        if (list == null) {
            list = store.readBackup()?.let { decode(it) }
            loadWarning = if (list != null) "Tệp hồ sơ xe bị lỗi, đã khôi phục từ bản dự phòng" else "Tệp hồ sơ xe bị lỗi và không có bản dự phòng hợp lệ"
        }
        _profiles.value = list ?: emptyList()
    }

    private fun decode(text: String): List<ProfileData>? {
        val env = ProfileJson.parseEnvelope(text, ::newId)
        if (env.fatal != null) return null
        return env.items.mapNotNull { it.data }.distinctBy { it.id }
    }

    private fun persist(list: List<ProfileData>) { store.write(ProfileJson.envelope("store", list)) }

    private fun check(d: ProfileData) {
        val e = ProfileValidation.validate(d)
        if (e.isNotEmpty()) throw ProfileException("Hồ sơ không hợp lệ", e)
    }

    @Synchronized fun create(d: ProfileData): ProfileData {
        val id = if (d.id.isBlank() || _profiles.value.any { it.id == d.id } || d.id in reservedIds()) newId() else d.id
        val n = d.copy(id = id, builtin = false)
        check(n)
        if (_profiles.value.size >= MAX_PROFILES) throw ProfileException("Quá nhiều hồ sơ (tối đa $MAX_PROFILES)")
        val next = _profiles.value + n
        persist(next); _profiles.value = next
        return n
    }

    @Synchronized fun update(d: ProfileData): ProfileData {
        if (_profiles.value.none { it.id == d.id }) throw ProfileException("Không tìm thấy hồ sơ để sửa (hồ sơ dựng sẵn không sửa được)")
        val n = d.copy(builtin = false)
        check(n)
        val next = _profiles.value.map { if (it.id == n.id) n else it }
        persist(next); _profiles.value = next
        return n
    }

    /** Xoá hồ sơ người dùng. Không xoá được hồ sơ đang dùng. */
    @Synchronized fun delete(id: String, activeId: String) {
        if (id == activeId) throw ProfileException("Không xoá được hồ sơ đang dùng. Hãy chọn hồ sơ khác trước.")
        if (_profiles.value.none { it.id == id }) throw ProfileException("Không tìm thấy hồ sơ (hồ sơ dựng sẵn không xoá được)")
        val next = _profiles.value.filter { it.id != id }
        persist(next); _profiles.value = next
    }

    /** Nhân bản từ bất kỳ hồ sơ nào (kể cả dựng sẵn): mã mới, bản gốc giữ nguyên, không tự thành hồ sơ đang dùng. */
    @Synchronized fun duplicate(src: ProfileData, newName: String): ProfileData =
        create(src.copy(id = newId(), builtin = false, nickname = newName.trim().ifBlank { src.displayName + " (bản sao)" }))

    /** Thay toàn bộ hồ sơ người dùng (khôi phục). Lỗi giữa chừng thì trả về đúng trạng thái trước đó. */
    @Synchronized fun replaceAll(list: List<ProfileData>) {
        val previous = _profiles.value
        val clean = list.map { it.copy(builtin = false) }
        clean.forEach { check(it) }
        if (clean.map { it.id }.toSet().size != clean.size) throw ProfileException("Mã hồ sơ bị trùng")
        try {
            persist(clean); _profiles.value = clean
        } catch (e: Exception) {
            _profiles.value = previous
            runCatching { persist(previous) }
            throw ProfileException("Không ghi được hồ sơ, đã giữ nguyên dữ liệu cũ: " + (e.message ?: ""))
        }
    }
}

// ===== Nhập / xuất / sao lưu / khôi phục =====

enum class ImportStatus { NEW, DUP_ID_USER, DUP_ID_BUILTIN, INVALID }
enum class ImportAction { IMPORT_NEW, REPLACE, SKIP }

class ImportItem(val index: Int, val data: ProfileData?, val errors: List<String>, val status: ImportStatus, val nameClash: Boolean) {
    /** Hành động cho phép với mục này. */
    val allowed: List<ImportAction> get() = when (status) {
        ImportStatus.NEW -> listOf(ImportAction.IMPORT_NEW, ImportAction.SKIP)
        ImportStatus.DUP_ID_USER -> listOf(ImportAction.SKIP, ImportAction.IMPORT_NEW, ImportAction.REPLACE)
        ImportStatus.DUP_ID_BUILTIN -> listOf(ImportAction.SKIP, ImportAction.IMPORT_NEW)
        ImportStatus.INVALID -> listOf(ImportAction.SKIP)
    }
    /** Mặc định: chỉ nhập hồ sơ mới; trùng thì bỏ qua; không bao giờ tự ghi đè. */
    val defaultAction: ImportAction get() = if (status == ImportStatus.NEW) ImportAction.IMPORT_NEW else ImportAction.SKIP
}

class ImportPreview(val kind: String, val schema: Int, val activeId: String?, val items: List<ImportItem>, val fatal: String?)

class ApplyResult(val added: Int, val replaced: Int, val skipped: Int, val errors: List<String>)

object ProfileIO {
    const val KIND_EXPORT = "vl23-vehicle-profiles"
    const val KIND_BACKUP = "vl23-profile-backup"

    fun exportJson(profiles: List<ProfileData>, appVersion: String = "", nowMs: Long = System.currentTimeMillis()): String =
        ProfileJson.envelope(KIND_EXPORT, profiles.map { it.copy(builtin = false) }, null, appVersion, nowMs)

    /** Bản sao lưu: chỉ hồ sơ người dùng và mã hồ sơ đang chọn. Không chứa khoá API, token hay cache. */
    fun backupJson(user: List<ProfileData>, activeId: String, appVersion: String = "", nowMs: Long = System.currentTimeMillis()): String =
        ProfileJson.envelope(KIND_BACKUP, user, activeId, appVersion, nowMs)

    fun preview(text: String, user: List<ProfileData>, builtin: List<ProfileData>, newId: () -> String = { "tmp" }): ImportPreview {
        val env = ProfileJson.parseEnvelope(text, newId)
        if (env.fatal != null) return ImportPreview(env.kind, env.schema, null, emptyList(), env.fatal)
        val userIds = user.map { it.id }.toSet(); val builtinIds = builtin.map { it.id }.toSet()
        val names = (user + builtin).map { it.displayName.lowercase() }.toSet()
        val seen = HashSet<String>()
        val items = env.items.mapIndexed { i, p ->
            val d = p.data
            when {
                d == null -> ImportItem(i, null, p.errors, ImportStatus.INVALID, false)
                !seen.add(d.id) -> ImportItem(i, d, listOf("Mã hồ sơ bị trùng ngay trong tệp"), ImportStatus.INVALID, false)
                d.id in builtinIds -> ImportItem(i, d, emptyList(), ImportStatus.DUP_ID_BUILTIN, d.displayName.lowercase() in names)
                d.id in userIds -> ImportItem(i, d, emptyList(), ImportStatus.DUP_ID_USER, d.displayName.lowercase() in names)
                else -> ImportItem(i, d, emptyList(), ImportStatus.NEW, d.displayName.lowercase() in names)
            }
        }
        return ImportPreview(env.kind, env.schema, env.activeId, items, null)
    }

    fun apply(items: List<ImportItem>, actions: List<ImportAction>, repo: ProfileRepository): ApplyResult {
        var added = 0; var replaced = 0; var skipped = 0; val errs = ArrayList<String>()
        items.forEachIndexed { i, item ->
            val a = actions.getOrElse(i) { item.defaultAction }
            val d = item.data
            if (d == null || a == ImportAction.SKIP || a !in item.allowed) { skipped++; return@forEachIndexed }
            try {
                if (a == ImportAction.REPLACE) { repo.update(d); replaced++ }
                else { repo.create(if (item.status == ImportStatus.NEW) d else d.copy(id = repo.newId())); added++ }
            } catch (e: ProfileException) { errs.add(d.displayName + ": " + (e.errors.firstOrNull() ?: e.message)); skipped++ }
        }
        return ApplyResult(added, replaced, skipped, errs)
    }

    /**
     * Khôi phục toàn bộ: thay mọi hồ sơ người dùng bằng nội dung bản sao lưu (mục không hợp lệ bị bỏ, mã trùng hồ sơ dựng sẵn được cấp mã mới).
     * Trả về mã hồ sơ đang chọn trong bản sao lưu nếu còn tồn tại, ngược lại null. Lỗi ghi thì dữ liệu cũ được giữ nguyên (repo.replaceAll hoàn tác).
     */
    fun restoreAll(p: ImportPreview, repo: ProfileRepository, builtinIds: Set<String>): String? {
        if (p.fatal != null) throw ProfileException(p.fatal)
        val valid = p.items.mapNotNull { it.data }
        if (valid.isEmpty() && p.items.isNotEmpty()) throw ProfileException("Bản sao lưu không có hồ sơ hợp lệ nên không khôi phục (dữ liệu hiện tại được giữ nguyên)")
        val used = HashSet(builtinIds)
        val list = valid.map { d ->
            val id = if (d.id in used) repo.newId() else d.id
            used.add(id); d.copy(id = id)
        }
        repo.replaceAll(list)
        return p.activeId?.takeIf { id -> id in builtinIds || list.any { it.id == id } }
    }
}
