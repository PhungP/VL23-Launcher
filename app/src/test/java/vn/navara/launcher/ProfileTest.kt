package vn.navara.launcher

import org.junit.Assert.*
import org.junit.Test

class MemoryStore(var main: String? = null, var bak: String? = null, var failNext: Boolean = false) : ProfileStore {
    override fun read() = main
    override fun readBackup() = bak
    override fun write(text: String) {
        if (failNext) { failNext = false; throw java.io.IOException("disk full") }
        if (main != null) bak = main
        main = text
    }
}

private fun sample(id: String = "x1", name: String = "Xe thử") = ProfileData(
    id = id, nickname = name, brand = "Honda", model = "Accord", year = 2015, engine = "2.4", displacementL = 2.4, fuel = "PETROL",
    gearbox = "5AT", gearRatios = listOf(2.706, 1.552, 1.0, 0.756, 0.6), finalDrive = 4.357, tireM = 2.09,
    redline = 6700, rpmMax = 8000, priorityPids = listOf("11"), skipPids = listOf("78"),
)

class ProfileValidationTest {
    @Test fun sampleIsValid() = assertTrue(ProfileValidation.validate(sample()).isEmpty())
    @Test fun needsNameInfo() = assertTrue(ProfileValidation.validate(sample().copy(brand = "", model = "", nickname = "")).isNotEmpty())
    @Test fun badTire() = assertTrue(ProfileValidation.validate(sample().copy(tireM = 9.0)).isNotEmpty())
    @Test fun redlineAboveMax() = assertTrue(ProfileValidation.validate(sample().copy(redline = 9000, rpmMax = 8000)).isNotEmpty())
    @Test fun ratiosMustDescend() = assertTrue(ProfileValidation.validate(sample().copy(gearRatios = listOf(1.0, 2.0))).isNotEmpty())
    @Test fun ratiosNeedFinalDrive() = assertTrue(ProfileValidation.validate(sample().copy(finalDrive = 0.0)).isNotEmpty())
    @Test fun noRatiosIsFine() = assertTrue(ProfileValidation.validate(sample().copy(gearRatios = emptyList(), finalDrive = 0.0)).isEmpty())
    @Test fun badPid() = assertTrue(ProfileValidation.validate(sample().copy(skipPids = listOf("ZZ"))).isNotEmpty())
    @Test fun pidCannotBeBoth() = assertTrue(ProfileValidation.validate(sample().copy(priorityPids = listOf("78"))).isNotEmpty())
    @Test fun badProtocol() = assertTrue(ProfileValidation.validate(sample().copy(obdProtocol = 12)).isNotEmpty())
    @Test fun badSkin() = assertTrue(ProfileValidation.validate(sample().copy(skin = 3)).isNotEmpty())
    @Test fun badTilesKeyRejected() = assertTrue(ProfileValidation.validate(sample().copy(homeTiles = "rpm,<script>")).isNotEmpty())
    @Test fun badId() = assertTrue(ProfileValidation.validate(sample().copy(id = "a/b")).isNotEmpty())
    @Test fun alertOrder() = assertTrue(ProfileValidation.validate(sample().copy(alerts = AlertOverride(coolantWarn = 110f, coolantCrit = 100f))).isNotEmpty())
    @Test fun alertRange() = assertTrue(ProfileValidation.validate(sample().copy(alerts = AlertOverride(voltLow = 3f))).isNotEmpty())
    @Test fun overridesApplyOnlyWhereSet() {
        val c = AlertConfig(coolantWarn = 105f, voltLow = 11.8f).withOverrides(AlertOverride(coolantWarn = 98f))
        assertEquals(98f, c.coolantWarn); assertEquals(11.8f, c.voltLow)
    }
}

class ProfileRepositoryTest {
    private fun repo(store: ProfileStore = MemoryStore(), reserved: Set<String> = setOf("builtin1")): ProfileRepository {
        var n = 0
        return ProfileRepository(store, { "u_${n++}" }, { reserved }).also { it.load() }
    }

    @Test fun createAssignsUniqueIdsAndPersists() {
        val st = MemoryStore(); val r = repo(st)
        val a = r.create(sample(id = "")); val b = r.create(sample(id = a.id))
        assertNotEquals(a.id, b.id); assertEquals(2, r.profiles.value.size)
        val r2 = ProfileRepository(st).also { it.load() }
        assertEquals(2, r2.profiles.value.size)
    }

    @Test fun createCannotUseBuiltinId() = assertNotEquals("builtin1", repo().create(sample(id = "builtin1")).id)

    @Test fun invalidProfileRejectedAndNothingSaved() {
        val st = MemoryStore(); val r = repo(st)
        try { r.create(sample().copy(tireM = 99.0)); fail() } catch (e: ProfileException) { assertTrue(e.errors.isNotEmpty()) }
        assertNull(st.main); assertTrue(r.profiles.value.isEmpty())
    }

    @Test fun updateChangesOnlyTarget() {
        val r = repo(); val a = r.create(sample("a")); val b = r.create(sample("b", "B"))
        r.update(a.copy(nickname = "A2"))
        assertEquals("A2", r.profiles.value.first { it.id == a.id }.nickname)
        assertEquals("B", r.profiles.value.first { it.id == b.id }.nickname)
    }

    @Test fun updateUnknownFails() { try { repo().update(sample("zzz")); fail() } catch (e: ProfileException) {} }

    @Test fun cannotDeleteActive() {
        val r = repo(); val a = r.create(sample("a"))
        try { r.delete(a.id, a.id); fail() } catch (e: ProfileException) {}
        assertEquals(1, r.profiles.value.size)
    }

    @Test fun deleteInactiveAndBuiltinGuard() {
        val r = repo(); val a = r.create(sample("a"))
        r.delete(a.id, "builtin1"); assertTrue(r.profiles.value.isEmpty())
        try { r.delete("builtin1", "x"); fail() } catch (e: ProfileException) {}
    }

    @Test fun duplicateGivesNewIdKeepsSourceAndSpecs() {
        val r = repo(); val a = r.create(sample("a", "Gốc"))
        val d = r.duplicate(a, "Bản 2")
        assertNotEquals(a.id, d.id); assertEquals("Bản 2", d.nickname); assertEquals(a.gearRatios, d.gearRatios)
        assertEquals("Gốc", r.profiles.value.first { it.id == "a" }.nickname)
        r.update(d.copy(tireM = 2.2)); assertEquals(2.09, r.profiles.value.first { it.id == "a" }.tireM, 1e-9)
    }

    @Test fun duplicateOfBuiltinBecomesUserProfile() {
        val r = repo(); val d = r.duplicate(sample("builtin1").copy(builtin = true), "")
        assertFalse(d.builtin); assertTrue(d.nickname.contains("bản sao"))
    }

    @Test fun corruptMainFallsBackToBackup() {
        val st = MemoryStore(); val r = repo(st); r.create(sample("a")); r.create(sample("b"))
        st.main = "{ hỏng"
        val r2 = ProfileRepository(st).also { it.load() }
        assertEquals(1, r2.profiles.value.size); assertNotNull(r2.loadWarning)
    }

    @Test fun failedWriteKeepsMemoryState() {
        val st = MemoryStore(); val r = repo(st); r.create(sample("a"))
        st.failNext = true
        try { r.create(sample("b")); fail() } catch (e: java.io.IOException) {}
        assertEquals(1, r.profiles.value.size)
    }

    @Test fun replaceAllRollsBackOnFailure() {
        val st = MemoryStore(); val r = repo(st); r.create(sample("a"))
        st.failNext = true
        try { r.replaceAll(listOf(sample("n1"), sample("n2"))); fail() } catch (e: ProfileException) {}
        assertEquals(listOf("a"), r.profiles.value.map { it.id })
        assertEquals(listOf("a"), ProfileRepository(st).also { it.load() }.profiles.value.map { it.id })
    }

    @Test fun replaceAllRejectsInvalidAndDuplicateIds() {
        val r = repo(); r.create(sample("a"))
        try { r.replaceAll(listOf(sample("n"), sample("n"))); fail() } catch (e: ProfileException) {}
        try { r.replaceAll(listOf(sample("n").copy(tireM = 50.0))); fail() } catch (e: ProfileException) {}
        assertEquals(listOf("a"), r.profiles.value.map { it.id })
    }
}

class ProfileImportExportTest {
    private val builtin = listOf(sample("builtin1", "Có sẵn").copy(builtin = true))
    private fun repo() = ProfileRepository(MemoryStore(), idGen = run { var n = 0; { "u_${n++}" } }, reservedIds = { setOf("builtin1") }).also { it.load() }

    @Test fun roundTrip() {
        val a = sample("a", "Xe A").copy(alerts = AlertOverride(coolantWarn = 100f, tpmsLowBar = 2.0f))
        val b = sample("b", "Xe B").copy(gearRatios = emptyList(), finalDrive = 0.0, skin = 6)
        val json = ProfileIO.exportJson(listOf(a, b))
        val p = ProfileIO.preview(json, emptyList(), builtin)
        assertNull(p.fatal); assertEquals(2, p.items.size)
        assertEquals(a, p.items[0].data); assertEquals(b, p.items[1].data)
    }

    @Test fun exportIsUtf8ReadableWithSchemaAndMetadata() {
        val json = ProfileIO.exportJson(listOf(sample("a", "Xe Việt Ðồ Sơn")), "0.29", 123L)
        assertTrue(json.contains("\"schema\": 1")); assertTrue(json.contains("exportedAt")); assertTrue(json.contains("Đồ Sơn".replace("Đ", "Ð")) || json.contains("Việt"))
    }

    @Test fun malformedJsonIsFatalNotCrash() {
        for (t in listOf("", "xyz", "{", "[1,2", "{\"schema\":\"a\"}", "{\"schema\":1}"))
            assertNotNull(t, ProfileIO.preview(t, emptyList(), builtin).fatal)
    }

    @Test fun futureSchemaRejected() {
        val p = ProfileIO.preview("{\"schema\":99,\"profiles\":[]}", emptyList(), builtin)
        assertTrue(p.fatal!!.contains("mới hơn"))
    }

    @Test fun legacySchemaZeroArrayAndSingleObjectAccepted() {
        val one = ProfileJson.toJson(sample("a")).toString()
        assertEquals(1, ProfileIO.preview("[$one]", emptyList(), builtin).items.size)
        assertEquals(1, ProfileIO.preview(one, emptyList(), builtin).items.size)
        assertEquals(1, ProfileIO.preview("{\"profiles\":[$one]}", emptyList(), builtin).items.count { it.data != null })
    }

    @Test fun wrongTypesAndOutOfRangeAreInvalid() {
        val bad1 = "{\"schema\":1,\"profiles\":[{\"brand\":\"A\",\"model\":\"B\",\"year\":\"abc\"}]}"
        val bad2 = "{\"schema\":1,\"profiles\":[{\"brand\":\"A\",\"model\":\"B\",\"tireM\":50}]}"
        val bad3 = "{\"schema\":1,\"profiles\":[5, {\"brand\":\"A\",\"model\":\"B\"}]}"
        assertEquals(ImportStatus.INVALID, ProfileIO.preview(bad1, emptyList(), builtin).items[0].status)
        assertEquals(ImportStatus.INVALID, ProfileIO.preview(bad2, emptyList(), builtin).items[0].status)
        val p3 = ProfileIO.preview(bad3, emptyList(), builtin)
        assertEquals(ImportStatus.INVALID, p3.items[0].status); assertEquals(ImportStatus.NEW, p3.items[1].status)
    }

    @Test fun tooLargeFileRejected() {
        val big = "[" + "0,".repeat(MAX_IMPORT_BYTES) + "0]"
        assertTrue(ProfileIO.preview(big, emptyList(), builtin).fatal!!.contains("quá lớn"))
    }

    @Test fun unknownKeysIgnoredNotExecuted() {
        val j = "{\"schema\":1,\"profiles\":[{\"brand\":\"A\",\"model\":\"B\",\"exec\":\"rm -rf /\",\"script\":{\"x\":1}}]}"
        assertEquals(ImportStatus.NEW, ProfileIO.preview(j, emptyList(), builtin).items[0].status)
    }

    @Test fun duplicateIdsAndNamesDetected() {
        val r = repo(); val u = r.create(sample("a", "Xe A"))
        val json = ProfileIO.exportJson(listOf(u, sample("builtin1", "Có sẵn"), sample("n", "Xe A"), sample("a", "Trùng trong tệp")))
        val p = ProfileIO.preview(json, r.profiles.value, builtin)
        assertEquals(ImportStatus.DUP_ID_USER, p.items[0].status)
        assertEquals(ImportStatus.DUP_ID_BUILTIN, p.items[1].status)
        assertEquals(ImportStatus.NEW, p.items[2].status); assertTrue(p.items[2].nameClash)
        assertEquals(ImportStatus.INVALID, p.items[3].status)
    }

    @Test fun defaultActionNeverOverwrites() {
        val r = repo(); val u = r.create(sample("a", "Xe A"))
        val p = ProfileIO.preview(ProfileIO.exportJson(listOf(u, sample("z", "Mới"))), r.profiles.value, builtin)
        val res = ProfileIO.apply(p.items, p.items.map { it.defaultAction }, r)
        assertEquals(1, res.added); assertEquals(0, res.replaced); assertEquals(1, res.skipped)
        assertEquals(2, r.profiles.value.size)
    }

    @Test fun replaceOnlyWhenChosenAndOnlyUserProfiles() {
        val r = repo(); val u = r.create(sample("a", "Xe A"))
        val p = ProfileIO.preview(ProfileIO.exportJson(listOf(u.copy(tireM = 2.2), sample("builtin1", "Có sẵn"))), r.profiles.value, builtin)
        val res = ProfileIO.apply(p.items, listOf(ImportAction.REPLACE, ImportAction.REPLACE), r)   // REPLACE cho hồ sơ dựng sẵn không được phép
        assertEquals(1, res.replaced); assertEquals(1, res.skipped)
        assertEquals(2.2, r.profiles.value.first { it.id == "a" }.tireM, 1e-9)
        assertEquals(1, r.profiles.value.size)
    }

    @Test fun importAsNewGivesFreshIdOnClash() {
        val r = repo(); val u = r.create(sample("a", "Xe A"))
        val p = ProfileIO.preview(ProfileIO.exportJson(listOf(u)), r.profiles.value, builtin)
        ProfileIO.apply(p.items, listOf(ImportAction.IMPORT_NEW), r)
        assertEquals(2, r.profiles.value.map { it.id }.toSet().size)
    }

    @Test fun invalidItemsNeverImported() {
        val r = repo()
        val p = ProfileIO.preview("{\"profiles\":[{\"brand\":\"A\",\"model\":\"B\",\"tireM\":50}]}", emptyList(), builtin)
        val res = ProfileIO.apply(p.items, listOf(ImportAction.IMPORT_NEW), r)
        assertEquals(0, res.added); assertTrue(r.profiles.value.isEmpty())
    }

    @Test fun backupRestoreRoundTripKeepsActive() {
        val src = repo(); val a = src.create(sample("a", "Xe A")); src.create(sample("b", "Xe B"))
        val backup = ProfileIO.backupJson(src.profiles.value, a.id)
        val dst = repo(); dst.create(sample("old", "Cũ"))
        val active = ProfileIO.restoreAll(ProfileIO.preview(backup, dst.profiles.value, builtin), dst, setOf("builtin1"))
        assertEquals(a.id, active); assertEquals(setOf("a", "b"), dst.profiles.value.map { it.id }.toSet())
    }

    @Test fun restoreActiveBuiltinIdKept() {
        val src = repo(); src.create(sample("a"))
        val active = ProfileIO.restoreAll(ProfileIO.preview(ProfileIO.backupJson(src.profiles.value, "builtin1"), emptyList(), builtin), repo(), setOf("builtin1"))
        assertEquals("builtin1", active)
    }

    @Test fun badBackupLeavesDataUntouched() {
        val r = repo(); r.create(sample("a"))
        try { ProfileIO.restoreAll(ProfileIO.preview("{ hỏng", r.profiles.value, builtin), r, setOf("builtin1")); fail() } catch (e: ProfileException) {}
        try { ProfileIO.restoreAll(ProfileIO.preview("{\"profiles\":[{\"brand\":\"A\",\"model\":\"B\",\"tireM\":50}]}", r.profiles.value, builtin), r, setOf("builtin1")); fail() } catch (e: ProfileException) {}
        assertEquals(listOf("a"), r.profiles.value.map { it.id })
    }

    @Test fun backupHasNoSecrets() {
        val json = ProfileIO.backupJson(listOf(sample("a")), "a")
        for (w in listOf("apiKey", "token", "password", "geminiKey", "goong")) assertFalse(w, json.contains(w, ignoreCase = true))
    }
}
