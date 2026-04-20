@file:Suppress("unused")

package app.revanced.patches.gms

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch

/**
 * Stubs FirebaseInitProvider via bytecode so it does nothing on startup.
 * This replaces the old ResourcePatch approach (manifest editing) which
 * caused aapt2 link failures on ReVanced Manager 1.x (Flutter).
 *
 * By stubbing onCreate/attachInfo/call/query at the bytecode level,
 * we avoid touching resources entirely.
 */

object FirebaseInitProviderOnCreateFingerprint : MethodFingerprint(
    returnType = "Z",
    customFingerprint = { method, _ ->
        method.definingClass.contains("FirebaseInitProvider") &&
                method.name == "onCreate"
    },
)

object FirebaseInitProviderAttachInfoFingerprint : MethodFingerprint(
    returnType = "V",
    customFingerprint = { method, _ ->
        method.definingClass.contains("FirebaseInitProvider") &&
                method.name == "attachInfo"
    },
)

object FirebaseInitProviderCallFingerprint : MethodFingerprint(
    customFingerprint = { method, _ ->
        method.definingClass.contains("FirebaseInitProvider") &&
                method.name == "call"
    },
)

object FirebaseInitProviderQueryFingerprint : MethodFingerprint(
    customFingerprint = { method, _ ->
        method.definingClass.contains("FirebaseInitProvider") &&
                method.name == "query"
    },
)

class RemoveFirebaseInitProvider : BytecodePatch(
    name = "Remove Firebase init provider",
    description = "Stubs FirebaseInitProvider methods via bytecode to " +
            "prevent auto-initialisation without GMS. " +
            "Does not modify resources (avoids aapt2 errors).",
    fingerprints = setOf(
        FirebaseInitProviderOnCreateFingerprint,
        FirebaseInitProviderAttachInfoFingerprint,
        FirebaseInitProviderCallFingerprint,
        FirebaseInitProviderQueryFingerprint,
    ),
) {
    override fun execute(context: BytecodeContext) {
        // onCreate() -> return false (provider did not create successfully)
        FirebaseInitProviderOnCreateFingerprint.result?.let {
            it.mutableMethod.addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    return v0
                """,
            )
        }

        // attachInfo(Context, ProviderInfo) -> no-op
        FirebaseInitProviderAttachInfoFingerprint.result?.let {
            it.mutableMethod.addInstructions(0, "return-void")
        }

        // call(...) -> return null
        FirebaseInitProviderCallFingerprint.result?.let {
            it.mutableMethod.addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    return-object v0
                """,
            )
        }

        // query(...) -> return null
        FirebaseInitProviderQueryFingerprint.result?.let {
            it.mutableMethod.addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    return-object v0
                """,
            )
        }

        // Catch-all: stub ALL methods in any FirebaseInitProvider class
        context.classes.forEach { classDef ->
            if (!classDef.type.contains("FirebaseInitProvider")) return@forEach
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
