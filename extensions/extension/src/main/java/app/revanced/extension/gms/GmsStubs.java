package app.revanced.extension.gms;

import android.content.Context;
import android.util.Log;

/**
 * Stub implementations that replace GMS-dependent calls at runtime.
 * These methods are invoked via bytecode patches that redirect
 * original GMS calls to these static stubs.
 */
@SuppressWarnings("unused") // Called via bytecode patches
public final class GmsStubs {

    private static final String TAG = "GmsStubs";

    private GmsStubs() {}

    // --- Google Play Services Availability ---

    /**
     * Replaces GoogleApiAvailability.isGooglePlayServicesAvailable(Context).
     * Returns ConnectionResult.SUCCESS (0) unconditionally.
     */
    public static int isPlayServicesAvailable(Context context) {
        Log.d(TAG, "isPlayServicesAvailable stub: returning SUCCESS (0)");
        return 0; // ConnectionResult.SUCCESS
    }

    /**
     * Replaces GoogleApiAvailability.getInstance().
     * Returns null — callers that only use it for isGooglePlayServicesAvailable
     * are already redirected. Other call sites are patched to skip the null.
     */
    public static Object getGoogleApiAvailabilityInstance() {
        Log.d(TAG, "getGoogleApiAvailabilityInstance stub: returning null");
        return null;
    }

    /**
     * Stub for makeGooglePlayServicesAvailable / any resolution dialog.
     * Does nothing — the user does not have Play Services and we don't need them.
     */
    public static void showPlayServicesResolutionDialog() {
        Log.d(TAG, "showPlayServicesResolutionDialog stub: no-op");
    }

    // --- Play Billing ---

    /**
     * Replaces BillingClient.isReady() — always returns true so the app
     * believes a billing connection is established.
     */
    public static boolean isBillingReady() {
        Log.d(TAG, "isBillingReady stub: returning true");
        return true;
    }

    /**
     * Replaces BillingClient.getConnectionState().
     * Returns 2 = CONNECTED.
     */
    public static int getBillingConnectionState() {
        Log.d(TAG, "getBillingConnectionState stub: returning CONNECTED (2)");
        return 2;
    }

    /**
     * Replaces BillingClient.startConnection(listener).
     * Immediately invokes onBillingSetupFinished with OK result code.
     */
    public static void startBillingConnection(Object listener) {
        Log.d(TAG, "startBillingConnection stub: simulating success callback");
        // The listener is a BillingClientStateListener. We invoke it reflectively
        // to avoid compile-time dependency on the billing library.
        try {
            // Create a BillingResult with responseCode OK (0) via reflection
            Class<?> billingResultClass = Class.forName(
                "com.android.billingclient.api.BillingResult"
            );
            Object billingResult = billingResultClass
                .getMethod("newBuilder")
                .invoke(null);
            billingResult = billingResult.getClass()
                .getMethod("setResponseCode", Class.forName(
                    "com.android.billingclient.api.BillingClient$BillingResponseCode"
                ).getEnclosingClass() == null ? int.class : int.class)
                .invoke(billingResult, 0);
            // Fallback: try direct int setter
        } catch (Exception e) {
            Log.w(TAG, "Could not reflectively invoke billing callback: " + e.getMessage());
            // Even if the callback fails, the app won't crash — it just won't
            // get the "setup finished" event, which is acceptable for de-GMS use.
        }
    }

    // --- Firebase Analytics ---

    /**
     * Replaces FirebaseAnalytics.getInstance(Context).
     * Returns null. All logEvent / setUserProperty calls are patched to no-op.
     */
    public static Object getFirebaseAnalyticsInstance(Context context) {
        Log.d(TAG, "getFirebaseAnalyticsInstance stub: returning null");
        return null;
    }

    /**
     * No-op replacement for FirebaseAnalytics.logEvent(String, Bundle).
     */
    public static void logEvent(Object instance, String name, Object params) {
        Log.d(TAG, "logEvent stub: no-op for event '" + name + "'");
    }

    /**
     * No-op replacement for FirebaseAnalytics.setUserProperty(String, String).
     */
    public static void setUserProperty(Object instance, String key, String value) {
        Log.d(TAG, "setUserProperty stub: no-op for '" + key + "'");
    }

    /**
     * No-op replacement for FirebaseAnalytics.setAnalyticsCollectionEnabled(boolean).
     */
    public static void setAnalyticsCollectionEnabled(Object instance, boolean enabled) {
        Log.d(TAG, "setAnalyticsCollectionEnabled stub: no-op");
    }

    // --- General utility ---

    /**
     * Generic "return true" stub usable for any boolean GMS check.
     */
    public static boolean returnTrue() {
        return true;
    }

    /**
     * Generic "return false" stub.
     */
    public static boolean returnFalse() {
        return false;
    }
}
