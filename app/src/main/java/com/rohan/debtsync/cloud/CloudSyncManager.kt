package com.rohan.debtsync.cloud

import android.app.Activity
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.rohan.debtsync.model.CurrencyMode
import com.rohan.debtsync.model.LoanAccount
import com.rohan.debtsync.model.MonthlyEmi
import com.rohan.debtsync.model.PaymentStatus
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CloudSyncManager {
    private var listenerRegistration: ListenerRegistration? = null

    fun isFirebaseReady(context: Context): Boolean {
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) true
            else FirebaseApp.initializeApp(context) != null
        } catch (_: Exception) {
            false
        }
    }

    fun currentPhone(context: Context): String? {
        if (!isFirebaseReady(context)) return null
        return try {
            FirebaseAuth.getInstance().currentUser?.phoneNumber
        } catch (_: Exception) {
            null
        }
    }

    fun currentUid(context: Context): String? {
        if (!isFirebaseReady(context)) return null
        return try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }
    }

    fun normalizePhone(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[\\s\\-()]"), "")
        return when {
            cleaned.startsWith("+") -> cleaned
            cleaned.length == 10 && cleaned.all { it.isDigit() } -> "+91$cleaned"
            else -> "+$cleaned"
        }
    }

    fun sendOtp(
        activity: Activity,
        rawPhone: String,
        onCodeSent: (verificationId: String) -> Unit,
        onAutoVerified: (phone: String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isFirebaseReady(activity)) {
            onError("Cloud sync is not configured yet (missing google-services.json).")
            return
        }
        val phone = normalizePhone(rawPhone)
        val auth = FirebaseAuth.getInstance()
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                auth.signInWithCredential(credential)
                    .addOnSuccessListener { result ->
                        onAutoVerified(result.user?.phoneNumber ?: phone)
                    }
                    .addOnFailureListener { e ->
                        onError(e.localizedMessage ?: "Verification failed")
                    }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                onError(e.localizedMessage ?: "Could not send OTP")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                onCodeSent(verificationId)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(
        context: Context,
        verificationId: String,
        otp: String,
        onSuccess: (phone: String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isFirebaseReady(context)) {
            onError("Cloud sync is not configured yet.")
            return
        }
        val auth = FirebaseAuth.getInstance()
        val credential = PhoneAuthProvider.getCredential(verificationId, otp.trim())
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                onSuccess(result.user?.phoneNumber ?: "Verified")
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Invalid OTP")
            }
    }

    fun signOut(context: Context) {
        stopRealtimeSync()
        if (isFirebaseReady(context)) {
            try {
                FirebaseAuth.getInstance().signOut()
            } catch (_: Exception) {
            }
        }
    }

    fun startRealtimeSync(
        context: Context,
        onRemoteState: (loans: List<LoanAccount>, currency: CurrencyMode?, updatedAtMs: Long) -> Unit,
        onError: (String) -> Unit
    ) {
        stopRealtimeSync()
        val uid = currentUid(context) ?: return
        try {
            val docRef = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("vault")
                .document("state")

            listenerRegistration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: "Cloud sync error")
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val rawJson = snapshot.getString("loansJson") ?: return@addSnapshotListener
                val updatedAtMs = snapshot.getLong("updatedAtMs") ?: 0L
                val currencyName = snapshot.getString("currency")
                val currency = when (currencyName) {
                    CurrencyMode.USD.name -> CurrencyMode.USD
                    CurrencyMode.INR.name -> CurrencyMode.INR
                    else -> null
                }
                val parsed = decodeLoansJson(rawJson) ?: return@addSnapshotListener
                onRemoteState(parsed, currency, updatedAtMs)
            }
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Could not start cloud sync")
        }
    }

    fun stopRealtimeSync() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    fun pushState(
        context: Context,
        loans: List<LoanAccount>,
        currency: CurrencyMode,
        updatedAtMs: Long
    ) {
        val uid = currentUid(context) ?: return
        try {
            val payload = mapOf(
                "loansJson" to encodeLoansJson(loans),
                "currency" to currency.name,
                "updatedAtMs" to updatedAtMs,
                "phone" to (currentPhone(context) ?: "")
            )
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("vault")
                .document("state")
                .set(payload)
        } catch (_: Exception) {
        }
    }

    private fun encodeLoansJson(loans: List<LoanAccount>): String {
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
        return arr.toString()
    }

    private fun decodeLoansJson(raw: String): List<LoanAccount>? {
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
            null
        }
    }
}
