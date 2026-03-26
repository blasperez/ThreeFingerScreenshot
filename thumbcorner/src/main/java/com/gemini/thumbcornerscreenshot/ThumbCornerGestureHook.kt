package com.gemini.thumbcornerscreenshot

import android.content.res.Resources
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.hypot

class ThumbCornerGestureHook : IXposedHookLoadPackage {

    private var trackingGesture = false
    private var startX = 0f
    private var startY = 0f
    private var gestureStartTime = 0L
    private var lastMoveEventTime = -1L
    private var lastTriggeredEventTime = -1L
    private var lastScreenshotTime = 0L
    private val gestureLock = Any()
    private val screenshotLock = Any()

    @Volatile
    private var screenshotInProgress = false

    // Gesture tuning for one-handed thumb use.
    private val cornerZoneRatio = 0.16f
    private val holdBeforeSwipeMs = 320L
    private val minAxisDeltaPx = 110f
    private val minDiagonalDistancePx = 180f
    private val diagonalTolerancePx = 220f
    private val maxGestureTime = 1600L
    private val cancelOppositeDirectionPx = 90f
    private val screenshotCooldown = 1800L

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "android") {
            log("Installing hooks in SYSTEM_SERVER (android)")
            hookSystemServerClasses(lpparam)
            hookInputEventReceiver(lpparam, "system_server")
        } else if (lpparam.packageName == "com.android.systemui") {
            log("Installing hooks in SystemUI")
            hookInputEventReceiver(lpparam, "systemui")
        }
    }

    private fun hookSystemServerClasses(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classNames = listOf(
            "com.android.server.policy.PhoneWindowManager",
            "com.android.server.policy.OemPhoneWindowManager",
            "com.android.server.policy.PhoneWindowManagerExt",
            "com.samsung.android.server.policy.PhoneWindowManager",
            "com.samsung.android.server.policy.PhoneWindowManagerExt",
            "com.android.server.input.InputManagerService"
        )

        var totalHooks = 0
        for (className in classNames) {
            val clazz = findClassOrNull(className, lpparam.classLoader) ?: continue
            totalHooks += hookGestureMethods(clazz)
        }
        log("System-server hook summary: $totalHooks method hooks installed")
    }

    private fun hookInputEventReceiver(lpparam: XC_LoadPackage.LoadPackageParam, origin: String) {
        try {
            val clazz = XposedHelpers.findClass("android.view.InputEventReceiver", lpparam.classLoader)
            val hooked = hookMethodIfExists(clazz, "dispatchInputEvent", "InputEventReceiver/$origin")
            if (!hooked) {
                log("InputEventReceiver.dispatchInputEvent not available in $origin")
            }
        } catch (e: Throwable) {
            log("Error hooking InputEventReceiver in $origin: ${e.message}")
        }
    }

    private fun hookGestureMethods(clazz: Class<*>): Int {
        val methodsToTry = linkedSetOf(
            "filterInputEvent",
            "interceptInputEvent",
            "interceptMotionBeforeQueueingNonInteractive",
            "interceptMotionBeforeQueueing",
            "dispatchInputEvent",
            "processMotionEvent",
            "onMotionEvent",
            "onInputEvent",
            "interceptPointerEvent",
            "handleMotionEvent"
        )

        for (method in clazz.declaredMethods) {
            if (containsInputArgs(method) && looksLikeInputMethod(method.name)) {
                methodsToTry.add(method.name)
            }
        }

        var hookedCount = 0
        for (methodName in methodsToTry) {
            if (hookMethodIfExists(clazz, methodName, clazz.simpleName)) {
                hookedCount++
            }
        }
        return hookedCount
    }

    private fun containsInputArgs(method: Method): Boolean {
        return method.parameterTypes.any { paramType ->
            MotionEvent::class.java.isAssignableFrom(paramType) ||
                InputEvent::class.java.isAssignableFrom(paramType)
        }
    }

    private fun looksLikeInputMethod(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized.contains("touch") ||
            normalized.contains("motion") ||
            normalized.contains("input") ||
            normalized.contains("pointer") ||
            normalized.contains("intercept") ||
            normalized.contains("dispatch") ||
            normalized.contains("filter")
    }

    private fun hookMethodIfExists(clazz: Class<*>, methodName: String, origin: String): Boolean {
        val exists = clazz.declaredMethods.any { it.name == methodName }
        if (!exists) return false

        val signature = "${clazz.name}#$methodName"
        synchronized(hookedMethods) {
            if (hookedMethods.contains(signature)) return false
            hookedMethods.add(signature)
        }

        return try {
            XposedBridge.hookAllMethods(clazz, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val event = extractMotionEvent(param.args) ?: return
                        if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) return
                        handleTouchEvent(event)
                    } catch (_: Throwable) {
                    }
                }
            })
            log("Hooked $signature from $origin")
            true
        } catch (e: Throwable) {
            synchronized(hookedMethods) {
                hookedMethods.remove(signature)
            }
            log("Failed to hook $signature: ${e.message}")
            false
        }
    }

    private fun extractMotionEvent(args: Array<Any?>): MotionEvent? {
        for (arg in args) {
            when (arg) {
                is MotionEvent -> return arg
                is InputEvent -> if (arg is MotionEvent) return arg
            }
        }
        return null
    }

    private fun findClassOrNull(className: String, classLoader: ClassLoader?): Class<*>? {
        return try {
            XposedHelpers.findClass(className, classLoader)
        } catch (_: Throwable) {
            null
        }
    }

    private fun handleTouchEvent(event: MotionEvent) {
        synchronized(gestureLock) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (event.pointerCount == 1) {
                        val x = safeRawX(event)
                        val y = safeRawY(event)
                        trackingGesture = isBottomRightCorner(x, y)
                        if (trackingGesture) {
                            startX = x
                            startY = y
                            gestureStartTime = SystemClock.elapsedRealtime()
                            lastMoveEventTime = -1L
                        }
                    } else {
                        trackingGesture = false
                    }
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    trackingGesture = false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!trackingGesture || event.pointerCount != 1) return
                    if (event.eventTime == lastMoveEventTime) return
                    lastMoveEventTime = event.eventTime

                    val now = SystemClock.elapsedRealtime()
                    val elapsed = now - gestureStartTime
                    if (elapsed > maxGestureTime) {
                        trackingGesture = false
                        return
                    }

                    val currentX = safeRawX(event)
                    val currentY = safeRawY(event)

                    // Cancel if motion is mostly down or to the right.
                    if (currentY > startY + cancelOppositeDirectionPx ||
                        currentX > startX + cancelOppositeDirectionPx
                    ) {
                        trackingGesture = false
                        return
                    }

                    if (elapsed < holdBeforeSwipeMs) return

                    val dx = startX - currentX // positive means left
                    val dy = startY - currentY // positive means up
                    val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    val diagonalEnough = abs(dx - dy) <= diagonalTolerancePx

                    if (dx >= minAxisDeltaPx &&
                        dy >= minAxisDeltaPx &&
                        distance >= minDiagonalDistancePx &&
                        diagonalEnough &&
                        (now - lastScreenshotTime) > screenshotCooldown &&
                        event.eventTime != lastTriggeredEventTime
                    ) {
                        log("Thumb corner gesture detected. Taking screenshot.")
                        lastTriggeredEventTime = event.eventTime
                        lastScreenshotTime = now
                        trackingGesture = false
                        takeScreenshot()
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                    trackingGesture = false
                }
            }
        }
    }

    private fun safeRawX(event: MotionEvent): Float {
        return try {
            event.rawX
        } catch (_: Throwable) {
            event.getX(0)
        }
    }

    private fun safeRawY(event: MotionEvent): Float {
        return try {
            event.rawY
        } catch (_: Throwable) {
            event.getY(0)
        }
    }

    private fun isBottomRightCorner(x: Float, y: Float): Boolean {
        val metrics = Resources.getSystem().displayMetrics
        val width = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val height = metrics.heightPixels.toFloat().coerceAtLeast(1f)
        return x >= width * (1f - cornerZoneRatio) &&
            y >= height * (1f - cornerZoneRatio)
    }

    private fun takeScreenshot() {
        synchronized(screenshotLock) {
            if (screenshotInProgress) {
                log("Screenshot request skipped (already in progress).")
                return
            }
            screenshotInProgress = true
        }

        Thread {
            try {
                val commands = listOf(
                    arrayOf("/system/bin/cmd", "statusbar", "screenshot"),
                    arrayOf("/system/bin/input", "keyevent", "KEYCODE_SYSRQ"),
                    arrayOf("/system/bin/input", "keyevent", "120"),
                    arrayOf("cmd", "statusbar", "screenshot"),
                    arrayOf("input", "keyevent", "KEYCODE_SYSRQ"),
                    arrayOf("input", "keyevent", "120"),
                    arrayOf("su", "-c", "cmd statusbar screenshot"),
                    arrayOf("su", "-c", "input keyevent KEYCODE_SYSRQ"),
                    arrayOf("su", "-c", "input keyevent 120")
                )

                var success = false
                for (command in commands) {
                    if (runCommand(command)) {
                        log("Screenshot command success: ${command.joinToString(" ")}")
                        success = true
                        break
                    }
                }

                if (!success) {
                    success = injectSysRq()
                    if (success) {
                        log("Screenshot success via injected KEYCODE_SYSRQ.")
                    }
                }

                if (!success) {
                    log("All screenshot strategies failed.")
                }
            } finally {
                screenshotInProgress = false
            }
        }.start()
    }

    private fun runCommand(command: Array<String>): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log("Command interrupted: ${command.joinToString(" ")}")
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun injectSysRq(): Boolean {
        return try {
            val inputManagerClass = XposedHelpers.findClass("android.hardware.input.InputManager", null)
            val inputManager = XposedHelpers.callStaticMethod(inputManagerClass, "getInstance")
            val injectMethod = inputManagerClass.methods.firstOrNull {
                it.name == "injectInputEvent" && it.parameterTypes.size >= 2
            } ?: return false

            val now = SystemClock.uptimeMillis()
            val down = KeyEvent(
                now,
                now,
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_SYSRQ,
                0,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_KEYBOARD
            )
            val up = KeyEvent(
                now,
                now + 10,
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_SYSRQ,
                0,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_KEYBOARD
            )

            val modeArg = when (injectMethod.parameterTypes[1]) {
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> 0
                else -> return false
            }

            val downOk = injectMethod.invoke(inputManager, down, modeArg) as? Boolean ?: false
            val upOk = injectMethod.invoke(inputManager, up, modeArg) as? Boolean ?: false
            downOk && upOk
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        private const val TAG = "ThumbCornerScreenshot"
        private val hookedMethods = HashSet<String>()

        fun log(message: String) {
            XposedBridge.log("$TAG: $message")
        }
    }
}
