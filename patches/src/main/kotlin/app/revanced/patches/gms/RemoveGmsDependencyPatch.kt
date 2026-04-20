@file:Suppress("unused")

package app.revanced.patches.gms

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

// ═══════════════════════════════════════════════════════════════
// Fingerprints based on the WORKING smali patch analysis.
// The app crashes due to:
//   1. PairIP license check (com.pairip.licensecheck.LicenseClient)
//   2. Billing service binding (com.android.vending.billing.InAppBillingService.BIND)
// NOT due to GoogleApiAvailability or FirebaseAnalytics.
// ═══════════════════════════════════════════════════════════════

// ─── PairIP License Check Fingerprints ────────────────────────

object LicenseClientCheckLicenseFingerprint : MethodFingerprint(
    returnType = "V",
    customFingerprint = { method, _ ->
        method.definingClass == "Lcom/pairip/licensecheck/LicenseClient;"
                && method.name == "checkLicense"
    },
)

object LicenseClientHandleErrorFingerprint : MethodFingerprint(
    returnType = "V",
    customFingerprint = { method, _ ->
        method.definingClass == "Lcom/pairip/licensecheck/LicenseClient;"
                && method.name == "handleError"
    },
)

object LicenseClientInitializeFingerprint : MethodFingerprint(
    returnType = "V",
    customFingerprint = { method, _ ->
        method.definingClass == "Lcom/pairip/licensecheck/LicenseClient;"
                && method.name == "initializeLicenseCheck"
    },
)

object LicenseClientStartErrorDialogFingerprint : MethodFingerprint(
    returnType = "V",
    customFingerprint = { method, _ ->
        method.definingClass == "Lcom/pairip/licensecheck/LicenseClient;"
                && method.name == "startErrorDialogActivity"
    },
)

object LicenseClientConnectFingerprint : MethodFingerprint(
    returnType = "V",
    customFingerprint = { method, _ ->
        method.definingClass == "Lcom/pairip/licensecheck/LicenseClient;"
                && method.name == "connectToLicensingService"
    },
)

// ─── Billing Service Binding Fingerprint ─────────────────────
// Matches the obfuscated method that contains the string
// "com.android.vending.billing.InAppBillingService.BIND"

object BillingServiceBindFingerprint : MethodFingerprint(
    returnType = "V",
    strings = listOf("com.android.vending.billing.InAppBillingService.BIND"),
)

// ─── Additional Billing Fingerprints ─────────────────────────

object BillingServiceConnectionBlockedFingerprint : MethodFingerprint(
    strings = listOf("Connection to Billing service is blocked."),
)

object BillingServiceUnavailableFingerprint : MethodFingerprint(
    strings = listOf("Billing service unavailable on device."),
)

// ═══════════════════════════════════════════════════════════════
// Patch 1: Remove PairIP License Check
// ═══════════════════════════════════════════════════════════════

class RemovePairIPLicenseCheck : BytecodePatch(
    name = "Remove PairIP license check",
    description = "Stubs PairIP LicenseClient to bypass Google Play " +
            "license verification. Prevents crash on devices without Play Store.",
    fingerprints = setOf(
        LicenseClientCheckLicenseFingerprint,
        LicenseClientHandleErrorFingerprint,
        LicenseClientInitializeFingerprint,
        LicenseClientStartErrorDialogFingerprint,
        LicenseClientConnectFingerprint,
    ),
) {
    override fun execute(context: BytecodeContext) {
        // checkLicense() -> return-void (skip entire license flow)
        LicenseClientCheckLicenseFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }

        // handleError() -> return-void (no error dialog)
        LicenseClientHandleErrorFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }

        // initializeLicenseCheck() -> return-void
        LicenseClientInitializeFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }

        // startErrorDialogActivity() -> return-void
        LicenseClientStartErrorDialogFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }

        // connectToLicensingService() -> return-void
        LicenseClientConnectFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }

        // Catch-all: stub ALL methods in LicenseClient class
        context.classes.forEach { classDef ->
            if (!classDef.type.contains("pairip/licensecheck/LicenseClient")) return@forEach
            classDef.methods.forEach { method ->
                if (method.name == "<init>" || method.name == "<clinit>") return@forEach
                val mutable = context.proxy(classDef).mutableClass.methods.firstOrNull {
                    it.name == method.name && it.parameterTypes == method.parameterTypes
                } ?: return@forEach

                when (mutable.returnType) {
                    "V" -> mutable.addInstructions(0, "return-void")
                    "Z" -> mutable.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
                    "I", "S", "B", "C" -> mutable.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
                    "J" -> mutable.addInstructions(0, "const-wide/16 v0, 0x0\nreturn-wide v0")
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

// ═══════════════════════════════════════════════════════════════
// Patch 2: Remove Billing Service Binding
// ═══════════════════════════════════════════════════════════════

class RemoveBillingServiceBinding : BytecodePatch(
    name = "Remove billing service binding",
    description = "Stubs the billing service initialization that tries to bind " +
            "to com.android.vending. Prevents crash without Play Store.",
    fingerprints = setOf(
        BillingServiceBindFingerprint,
        BillingServiceConnectionBlockedFingerprint,
        BillingServiceUnavailableFingerprint,
    ),
) {
    override fun execute(context: BytecodeContext) {
        // Stub the method that binds to InAppBillingService
        BillingServiceBindFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }

        // Also stub any method containing billing error strings
        BillingServiceConnectionBlockedFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }

        BillingServiceUnavailableFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }

        // Sweep: find and stub any method that references the vending billing service
        context.classes.forEach { classDef ->
            classDef.methods.forEach { method ->
                val impl = method.implementation ?: return@forEach
                val hasVendingString = impl.instructions.any { inst ->
                    if (inst.opcode != Opcode.CONST_STRING) return@any false
                    val ref = (inst as? ReferenceInstruction)?.reference
                            as? StringReference ?: return@any false
                    ref.string.contains("com.android.vending.billing") ||
                    ref.string.contains("com.android.vending") && ref.string.contains("billing")
                }
                if (!hasVendingString) return@forEach

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

// ═══════════════════════════════════════════════════════════════
// Patch 3: Remove Play Services Availability (keep for completeness)
// ═══════════════════════════════════════════════════════════════

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

class RemovePlayServicesAvailabilityCheck : BytecodePatch(
    name = "Remove Play Services availability check",
    description = "Stubs GoogleApiAvailability checks so the app never " +
            "complains about missing Google Play Services.",
    fingerprints = setOf(
        PlayServicesAvailabilityFingerprint,
        IsGooglePlayServicesAvailableFingerprint,
    ),
) {
    override fun execute(context: BytecodeContext) {
        IsGooglePlayServicesAvailableFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
        }
        PlayServicesAvailabilityFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Patch 4: Remove Firebase Analytics (keep for completeness)
// ═══════════════════════════════════════════════════════════════

object FirebaseAppInitializeFingerprint : MethodFingerprint(
    customFingerprint = { method, _ ->
        method.definingClass == "Lcom/google/firebase/FirebaseApp;"
                && method.name == "initializeApp"
    },
)

class RemoveFirebaseAnalyticsDependency : BytecodePatch(
    name = "Remove Firebase Analytics dependency",
    description = "Stubs Firebase Analytics so no data is collected.",
    fingerprints = setOf(
        FirebaseAppInitializeFingerprint,
    ),
) {
    override fun execute(context: BytecodeContext) {
        FirebaseAppInitializeFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "const/4 v0, 0x0\nreturn-object v0")
        }

        // Stub all methods in FirebaseAnalytics class
        context.classes.forEach { classDef ->
            if (classDef.type != "Lcom/google/firebase/analytics/FirebaseAnalytics;") return@forEach
            classDef.methods.forEach { method ->
                if (method.name == "<init>" || method.name == "<clinit>") return@forEach
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

// ═══════════════════════════════════════════════════════════════
// Master Patch: Remove ALL GMS Dependencies
// ═══════════════════════════════════════════════════════════════

class RemoveAllGmsDependencies : BytecodePatch(
    name = "Remove all GMS dependencies",
    description = "Comprehensive patch: removes PairIP license check, " +
            "billing service binding, Play Services checks, Firebase Analytics, " +
            "and Firebase init provider.",
    dependencies = setOf(
        RemovePairIPLicenseCheck::class,
        RemoveBillingServiceBinding::class,
        RemovePlayServicesAvailabilityCheck::class,
        RemoveFirebaseAnalyticsDependency::class,
        RemoveFirebaseInitProvider::class,
    ),
) {
    override fun execute(context: BytecodeContext) {
        // Final sweep: neutralise methods with GMS/licensing error strings
        val errorStrings = listOf(
            "Check that Google Play is enabled",
            "Google Play services is not available",
            "Google Play Store is not installed",
            "Google Play services are not available",
            "GooglePlayServicesUtil",
            "Error while checking license",
            "LicenseClient",
            "Billing service unavailable",
            "doesn't have valid Play Store",
        )

        context.classes.forEach { classDef ->
            classDef.methods.forEach { method ->
                val impl = method.implementation ?: return@forEach
                val hasErrorString = impl.instructions.any { inst ->
                    if (inst.opcode != Opcode.CONST_STRING) return@any false
                    val ref = (inst as? ReferenceInstruction)?.reference
                            as? StringReference ?: return@any false
                    errorStrings.any { ref.string.contains(it, ignoreCase = true) }
                }
                if (!hasErrorString) return@forEach

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
