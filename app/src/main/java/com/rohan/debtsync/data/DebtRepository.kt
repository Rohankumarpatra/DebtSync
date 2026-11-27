package com.rohan.debtsync.data

import android.content.Context
import com.rohan.debtsync.model.CurrencyMode
import com.rohan.debtsync.model.DebtCatalog
import com.rohan.debtsync.model.LoanAccount
import com.rohan.debtsync.model.MonthlyEmi
import com.rohan.debtsync.model.PaymentStatus
import com.rohan.debtsync.security.KeystoreCrypto
import com.rohan.debtsync.security.TotpManager
import org.json.JSONArray
import org.json.JSONObject

class DebtRepository(context: Context) {
    private val prefs = context.getSharedPreferences("debt_sync_clean_prefs", Context.MODE_PRIVATE)

    fun loadLoans(): List<LoanAccount> {
        val encryptedRaw = prefs.getString("loans_json_enc", null)
        val raw = if (encryptedRaw != null) {
            KeystoreCrypto.decrypt(encryptedRaw) ?: return DebtCatalog.initialLoans()
        } else {
            // Migrate legacy plaintext loans_json if present from v2.1-v2.3
            val legacy = prefs.getString("loans_json", null) ?: return DebtCatalog.initialLoans()
            prefs.edit()
                .putString("loans_json_enc", KeystoreCrypto.encrypt(legacy))
                .remove("loans_json")
                .apply()
            legacy
        }

        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<LoanAccount>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val paymentsObj = obj.optJSONObject("payments") ?: JSONObject()
                val payments = mutableMapOf<String, MonthlyEmi>()
                val keys = paymentsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val pObj = paymentsObj.getJSONObject(key)
                    val amt = pObj.optDouble("amount", 0.0)
                    val st = if (pObj.optString("status") == "PAID") PaymentStatus.PAID else PaymentStatus.UPCOMING
                    if (amt > 0) payments[key] = MonthlyEmi(amt, st)
                }
                list.add(
                    LoanAccount(
                        id = obj.getString("id"),
                        platform = obj.getString("platform"),
                        dueDay = if (obj.has("dueDay") && !obj.isNull("dueDay")) obj.getInt("dueDay") else null,
                        payments = payments
                    )
                )
            }
            list
        } catch (_: Exception) {
            DebtCatalog.initialLoans()
        }
    }

    fun saveLoans(loans: List<LoanAccount>) {
        val arr = JSONArray()
        loans.forEach { loan ->
            val obj = JSONObject().apply {
                put("id", loan.id)
                put("platform", loan.platform)
                if (loan.dueDay != null) put("dueDay", loan.dueDay)
                val pObj = JSONObject()
                loan.payments.forEach { (monthKey, emi) ->
                    pObj.put(
                        monthKey,
                        JSONObject().apply {
                            put("amount", emi.amount)
                            put("status", emi.status.name)
                        }
                    )
                }
                put("payments", pObj)
            }
            arr.put(obj)
        }
        prefs.edit()
            .putString("loans_json_enc", KeystoreCrypto.encrypt(arr.toString()))
            .remove("loans_json")
            .apply()
    }

    fun loadCurrency(): CurrencyMode {
        val code = prefs.getString("currency_mode", CurrencyMode.INR.name)
        return if (code == CurrencyMode.USD.name) CurrencyMode.USD else CurrencyMode.INR
    }

    fun saveCurrency(mode: CurrencyMode) {
        prefs.edit().putString("currency_mode", mode.name).apply()
    }

    fun loadDarkTheme(): Boolean? {
        if (!prefs.contains("dark_theme")) return null
        return prefs.getBoolean("dark_theme", false)
    }

    fun saveDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean("dark_theme", isDark).apply()
    }

    fun isAuthConfigured(): Boolean = !prefs.getString("totp_secret_enc", null).isNullOrBlank()

    fun saveAuthConfig(secretBase32: String, recoveryCode: String) {
        val encrypted = TotpManager.encryptSecret(secretBase32)
        val recoveryHash = TotpManager.sha256(recoveryCode.replace("-", "").trim().uppercase())
        prefs.edit()
            .putString("totp_secret_enc", encrypted)
            .putString("totp_recovery_hash", recoveryHash)
            .putInt("auth_failed_attempts", 0)
            .putLong("auth_lockout_until_ms", 0L)
            .apply()
    }

    fun loadTotpSecret(): String? {
        val enc = prefs.getString("totp_secret_enc", null) ?: return null
        return TotpManager.decryptSecret(enc)
    }

    fun verifyRecoveryCode(input: String): Boolean {
        val storedHash = prefs.getString("totp_recovery_hash", null) ?: return false
        val normalized = input.replace("-", "").replace(" ", "").trim().uppercase()
        if (normalized.isEmpty()) return false
        return TotpManager.constantTimeEquals(storedHash, TotpManager.sha256(normalized))
    }

    // Brute-force / rate-limit tracking persisted across restarts
    fun getAuthLockoutUntilMs(): Long = prefs.getLong("auth_lockout_until_ms", 0L)

    fun getFailedAuthAttempts(): Int = prefs.getInt("auth_failed_attempts", 0)

    fun recordFailedAuthAttempt(): Long {
        val attempts = getFailedAuthAttempts() + 1
        val cooldownMs = when {
            attempts >= 10 -> 120_000L // 2 minutes after 10+ failed tries
            attempts >= 5 -> 30_000L   // 30 seconds after 5+ failed tries
            else -> 0L
        }
        val until = if (cooldownMs > 0L) System.currentTimeMillis() + cooldownMs else 0L
        prefs.edit()
            .putInt("auth_failed_attempts", attempts)
            .putLong("auth_lockout_until_ms", until)
            .apply()
        return until
    }

    fun resetAuthFailures() {
        prefs.edit()
            .putInt("auth_failed_attempts", 0)
            .putLong("auth_lockout_until_ms", 0L)
            .apply()
    }

    fun isReminderEnabled(): Boolean = prefs.getBoolean("emi_reminder_enabled", true)

    fun setReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("emi_reminder_enabled", enabled).apply()
    }

    fun getReminderLeadDays(): Int = prefs.getInt("emi_reminder_lead_days", 3).coerceIn(1, 14)

    fun setReminderLeadDays(days: Int) {
        prefs.edit().putInt("emi_reminder_lead_days", days.coerceIn(1, 14)).apply()
    }
}
