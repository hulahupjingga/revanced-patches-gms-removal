@file:Suppress("unused")

package app.revanced.patches.gms

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.Patch.CompatiblePackage
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import kotlin.reflect.KClass

// ─── Fingerprints ───────────────────────────────────────────

object PlayServicesAvailabilityFingerprint : MethodFingerprint(
    strings = listOf("Check that Google Play"),
)

object IsGooglePlayServicesAvailableFingerprint : MethodFingerprint(
    returnType = "I",
    strings = listOf("com.google.android.gms"),
    customFingerprint = { method, _ ->
        method.definingClass.contains("GoogleApiAvailability")
                && method.name == "isGooglePlayServicesAvailable"
    },
)

object BillingClientIsReadyFingerprint : MethodFingerprint(
    returnType = "Z",
    customFingerprint = { method, _ ->
        method.definingClass.contains("BillingClient")
                && method.name == "isReady"
    },
)

object BillingClientGetConnectionStateFingerprint : MethodFingerprint(
    returnType = "I",
    customFingerprint = { method, _ ->
        method.definingClass.contains("BillingClient")
                && method.name == "getConnectionState"
    },
)

object BillingClientStartConnectionFingerprint : MethodFingerprint(
    returnType = "V",
    customFingerprint = { method, _ ->
        method.definingClass.contains("BillingClient")
                && method.name == "startConnection"
    },
)

object BillingQueryPurchasesFingerprint : MethodFingerprint(
    returnType = "V",
    customFingerprint = { method, _ ->
        method.definingClass.contains("BillingClient")
                && method.name == "queryPurchasesAsync"
    },
)

object BillingQueryProductDetailsFingerprint : MethodFingerprint(
    returnType = "V",
    customFingerprint = { method, _ ->
        method.definingClass.contains("BillingClient")
                && method.name == "queryProductDetailsAsync"
    },
)

object FirebaseAnalyticsGetInstanceFingerprint : MethodFingerprint(
    returnType = "Lcom/google/firebase/analytics/FirebaseAnalytics;",
    customFingerprint = { method, _ ->
        method.definingClass == "Lcom/google/firebase/analytics/FirebaseAnalytics;"
                && method.name == "getInstance"
    },
)

object FirebaseAnalyticsLogEventFingerprint : MethodFingerprint(
    returnType = "V",
    customFingerprint = { method, _ ->
        method.definingClass == "Lcom/google/firebase/analytics/FirebaseAnalytics;"
                && method.name == "logEvent"
    },
)

object FirebaseAnalyticsSetUserPropertyFingerprint : MethodFingerprint(
    returnType = "V",
    customFingerprint = { method, _ ->
        method.definingClass == "Lcom/google/firebase/analytics/FirebaseAnalytics;"
                && method.name == "setUserProperty"
    },
)

object FirebaseAnalyticsSetCollectionFingerprint : MethodFingerprint(
    returnType = "V",
    customFingerprint = { method, _ ->
        method.definingClass == "Lcom/google/firebase/analytics/FirebaseAnalytics;"
                && method.name == "setAnalyticsCollectionEnabled"
    },
)

object FirebaseAppInitializeFingerprint : MethodFingerprint(
    customFingerprint = { method, _ ->
        method.definingClass == "Lcom/google/firebase/FirebaseApp;"
                && method.name == "initializeApp"
                && AccessFlags.STATIC.isSet(method.accessFlags)
    },
)

// ─── Patch 1: Play Services Availability ────────────────────

class RemovePlayServicesAvailabilityCheck : BytecodePatch(
    name = "Remove Play Services availability check",
    description = "Stubs GoogleApiAvailability checks so the app never " +
            "complains about missing Google Play Services. " +
            "Removes the 'Check that Google Play is enabled' error.",
    fingerprints = setOf(
        PlayServicesAvailabilityFingerprint,
        IsGooglePlayServicesAvailableFingerprint,
    ),
    requiresIntegrations = true,
) {
    override fun execute(context: BytecodeContext) {
        // Stub isGooglePlayServicesAvailable -> return 0 (SUCCESS)
        IsGooglePlayServicesAvailableFingerprint.result?.let { result ->
            result.mutableMethod.addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    return v0
                """,
            )
        }

        // Also scan all classes for any remaining calls
        val targetMethods = listOf(
            "isGooglePlayServicesAvailable",
            "makeGooglePlayServicesAvailable",
        )

        context.classes.forEach { classDef ->
            classDef.methods.forEach { method ->
                val impl = method.implementation ?: return@forEach
                impl.instructions.forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.INVOKE_VIRTUAL &&
                        instruction.opcode != Opcode.INVOKE_STATIC
                    ) return@forEachIndexed

                    val ref = (instruction as? ReferenceInstruction)
                        ?.reference as? MethodReference ?: return@forEachIndexed

                    if (ref.definingClass.contains("GoogleApiAvailability") &&
                        targetMethods.contains(ref.name)
                    ) {
                        val mutableMethod = context.proxy(classDef)
                            .mutableClass.methods.first {
                                it.name == method.name &&
                                        it.parameterTypes == method.parameterTypes
                            }
                        mutableMethod.replaceInstruction(
                            index,
                            "invoke-static {p0}, " +
                                    "Lapp/revanced/extension/gms/GmsStubs;" +
                                    "->isPlayServicesAvailable(Landroid/content/Context;)I",
                        )
                    }
                }
            }
        }

        // Neutralise the error-string method
        PlayServicesAvailabilityFingerprint.result?.let { result ->
            result.mutableMethod.addInstructions(0, "return-void")
        }
    }
}

// ─── Patch 2: Play Billing ──────────────────────────────────

class RemovePlayBillingDependency : BytecodePatch(
    name = "Remove Play Billing dependency",
    description = "Stubs BillingClient so the app works without Google Play Billing.",
    fingerprints = setOf(
        BillingClientIsReadyFingerprint,
        BillingClientGetConnectionStateFingerprint,
        BillingClientStartConnectionFingerprint,
        BillingQueryPurchasesFingerprint,
        BillingQueryProductDetailsFingerprint,
    ),
) {
    override fun execute(context: BytecodeContext) {
        // isReady() -> true
        BillingClientIsReadyFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        }

        // getConnectionState() -> 2 (CONNECTED)
        BillingClientGetConnectionStateFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "const/4 v0, 0x2\nreturn v0")
        }

        // startConnection() -> no-op
        BillingClientStartConnectionFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }

        // queryPurchasesAsync() -> no-op
        BillingQueryPurchasesFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }

        // queryProductDetailsAsync() -> no-op
        BillingQueryProductDetailsFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }
    }
}

// ─── Patch 3: Firebase Analytics ────────────────────────────

class RemoveFirebaseAnalyticsDependency : BytecodePatch(
    name = "Remove Firebase Analytics dependency",
    description = "Stubs Firebase Analytics so no data is collected.",
    fingerprints = setOf(
        FirebaseAnalyticsGetInstanceFingerprint,
        FirebaseAnalyticsLogEventFingerprint,
        FirebaseAnalyticsSetUserPropertyFingerprint,
        FirebaseAnalyticsSetCollectionFingerprint,
        FirebaseAppInitializeFingerprint,
    ),
) {
    override fun execute(context: BytecodeContext) {
        // getInstance() -> return null
        FirebaseAnalyticsGetInstanceFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "const/4 v0, 0x0\nreturn-object v0")
        }

        // logEvent() -> no-op
        FirebaseAnalyticsLogEventFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }

        // setUserProperty() -> no-op
        FirebaseAnalyticsSetUserPropertyFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }

        // setAnalyticsCollectionEnabled() -> no-op
        FirebaseAnalyticsSetCollectionFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }

        // FirebaseApp.initializeApp() -> return null
        FirebaseAppInitializeFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "const/4 v0, 0x0\nreturn-object v0")
        }

        // Catch-all: stub ALL methods in FirebaseAnalytics class
        context.classes.forEach { classDef ->
            if (classDef.type != "Lcom/google/firebase/analytics/FirebaseAnalytics;") return@forEach
            classDef.methods.forEach { method ->
                val mutable = context.proxy(classDef).mutableClass.methods.firstOrNull {
                    it.name == method.name && it.parameterTypes == method.parameterTypes
                } ?: return@forEach

                when (mutable.returnType) {
                    "V" -> mutable.addInstructions(0, "return-void")
                    "Z" -> mutable.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
                    "I", "S", "B", "C" -> mutable.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
                    "J" -> mutable.addInstructions(0, "const-wide/16 v0, 0x0\nreturn-wide v0")
                    "F" -> mutable.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
                    "D" -> mutable.addInstructions(0, "const-wide/16 v0, 0x0\nreturn-wide v0")
                    else -> {
                        if (mutable.returnType.startsWith("L") || mutable.returnType.startsWith("[")) {
                            mutable.addInstructions(0, "const/4 v0, 0x0\nreturn-object v0")
                        }
                    }
                }
            }
        }
    }
}

// ─── Patch 4: Master (all GMS removal) ─────────────────────

class RemoveAllGmsDependencies : BytecodePatch(
    name = "Remove all GMS dependencies",
    description = "Comprehensive patch that removes Google Play Services, " +
            "Play Billing, and Firebase Analytics dependencies.",
    dependencies = setOf(
        RemovePlayServicesAvailabilityCheck::class,
        RemovePlayBillingDependency::class,
        RemoveFirebaseAnalyticsDependency::class,
        RemoveFirebaseInitProvider::class,
    ),
    requiresIntegrations = true,
) {
    override fun execute(context: BytecodeContext) {
        // Final sweep: find methods containing GMS error strings and neutralise
        val errorStrings = listOf(
            "Check that Google Play is enabled",
            "Google Play services is not available",
            "Google Play Store is not installed",
            "Google Play services are not available",
            "GooglePlayServicesUtil",
        )

        context.classes.forEach { classDef ->
            classDef.methods.forEach { method ->
                val impl = method.implementation ?: return@forEach
                val hasGmsString = impl.instructions.any { inst ->
                    if (inst.opcode != Opcode.CONST_STRING) return@any false
                    val ref = (inst as? ReferenceInstruction)?.reference
                            as? StringReference ?: return@any false
                    errorStrings.any { ref.string.contains(it, ignoreCase = true) }
                }
                if (!hasGmsString) return@forEach

                val mutable = context.proxy(classDef).mutableClass.methods.firstOrNull {
                    it.name == method.name && it.parameterTypes == method.parameterTypes
                } ?: return@forEach

                when (mutable.returnType) {
                    "V" -> mutable.addInstructions(0, "return-void")
                    "Z" -> mutable.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
                    "I" -> mutable.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
                    else -> {
                        if (mutable.returnType.startsWith("L") || mutable.returnType.startsWith("[")) {
                            mutable.addInstructions(0, "const/4 v0, 0x0\nreturn-object v0")
                        }
                    }
                }
            }
        }
    }
}
