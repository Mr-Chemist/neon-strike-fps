package ir.hiwa.neonstrike.view

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import ir.hiwa.neonstrike.audio.SoundEngine
import ir.hiwa.neonstrike.engine.GameEngine
import ir.hiwa.neonstrike.engine.RaycasterRenderer
import ir.hiwa.neonstrike.graphics.TextureAtlas
import ir.hiwa.neonstrike.model.GameState
import kotlin.math.hypot

class GameSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private val soundEngine = SoundEngine()
    private val atlas = TextureAtlas()
    private val renderer = RaycasterRenderer(atlas)
    private val engine = GameEngine(soundEngine) { durationMs -> triggerHaptic(durationMs) }

    @Volatile
    private var running = false
    private var gameThread: Thread? = null

    // Multi-touch pointer tracking
    private var joyPointerId = -1
    private var joyKnobX = 0f
    private var joyKnobY = 0f

    private var lookPointerId = -1
    private var lastLookX = 0f

    private var firePointerId = -1

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        resumeGameLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pauseGameLoop()
    }

    fun resumeGameLoop() {
        if (running) return
        running = true
        gameThread = Thread(this, "NeonStrikeGameLoop").apply { start() }
    }

    fun pauseGameLoop() {
        running = false
        try {
            gameThread?.join(500)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        gameThread = null
    }

    fun releaseResources() {
        pauseGameLoop()
        soundEngine.release()
    }

    override fun run() {
        var lastTimeNs = System.nanoTime()
        while (running) {
            val nowNs = System.nanoTime()
            val dt = ((nowNs - lastTimeNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastTimeNs = nowNs

            engine.update(dt)

            val canvas = holder.lockCanvas()
            if (canvas != null) {
                try {
                    renderer.render(
                        canvas = canvas,
                        width = canvas.width,
                        height = canvas.height,
                        engine = engine,
                        joyKnobX = joyKnobX,
                        joyKnobY = joyKnobY,
                        joyActive = joyPointerId != -1
                    )
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }

            // Cap around ~60 FPS to keep thermals low
            val frameMs = (System.nanoTime() - nowNs) / 1_000_000L
            val sleepMs = (16L - frameMs).coerceAtLeast(1L)
            try {
                Thread.sleep(sleepMs)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val index = event.actionIndex
        val pointerId = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)

        // Overlay UI handling when not actively playing
        if (engine.state != GameState.PLAYING) {
            if (action == MotionEvent.ACTION_DOWN) {
                handleOverlayTouch(x, y)
            }
            return true
        }

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                when {
                    renderer.pauseButtonRect.contains(x, y) -> {
                        clearTouchInputs()
                        engine.togglePause()
                    }
                    renderer.muteButtonRect.contains(x, y) -> {
                        engine.toggleMute()
                    }
                    renderer.fireButtonRect.contains(x, y) -> {
                        firePointerId = pointerId
                        engine.isFiringHeld = true
                    }
                    renderer.reloadButtonRect.contains(x, y) -> {
                        engine.triggerReload()
                    }
                    renderer.switchButtonRect.contains(x, y) -> {
                        engine.switchWeapon()
                    }
                    x < width * 0.45f && joyPointerId == -1 -> {
                        joyPointerId = pointerId
                        updateJoystick(x, y)
                    }
                    x >= width * 0.45f && lookPointerId == -1 -> {
                        lookPointerId = pointerId
                        lastLookX = x
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val px = event.getX(i)
                    val py = event.getY(i)
                    if (pid == joyPointerId) {
                        updateJoystick(px, py)
                    } else if (pid == lookPointerId) {
                        val deltaX = px - lastLookX
                        lastLookX = px
                        val sensitivity = 0.0052f
                        engine.rotatePlayer(deltaX * sensitivity)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (pidMatches(action, pointerId, joyPointerId)) {
                    joyPointerId = -1
                    engine.moveForwardInput = 0f
                    engine.moveStrafeInput = 0f
                }
                if (pidMatches(action, pointerId, lookPointerId)) {
                    lookPointerId = -1
                }
                if (pidMatches(action, pointerId, firePointerId)) {
                    firePointerId = -1
                    engine.isFiringHeld = false
                }
            }
        }
        return true
    }

    private fun pidMatches(action: Int, eventPointerId: Int, trackedId: Int): Boolean {
        return action == MotionEvent.ACTION_CANCEL || eventPointerId == trackedId
    }

    private fun updateJoystick(touchX: Float, touchY: Float) {
        val dx = touchX - renderer.joystickCenterX
        val dy = touchY - renderer.joystickCenterY
        val radius = renderer.joystickRadius.coerceAtLeast(1f)
        val dist = hypot(dx, dy)
        val clampedDist = dist.coerceAtMost(radius)
        val nx = if (dist > 1f) dx / dist else 0f
        val ny = if (dist > 1f) dy / dist else 0f

        joyKnobX = renderer.joystickCenterX + nx * clampedDist
        joyKnobY = renderer.joystickCenterY + ny * clampedDist

        val strength = clampedDist / radius
        engine.moveStrafeInput = nx * strength
        engine.moveForwardInput = -ny * strength
    }

    private fun handleOverlayTouch(x: Float, y: Float) {
        if (renderer.primaryActionRect.contains(x, y)) {
            when (engine.state) {
                GameState.MENU, GameState.VICTORY, GameState.GAME_OVER -> engine.startNewGame()
                GameState.PAUSED -> engine.togglePause()
                GameState.LEVEL_CLEAR -> engine.startNextLevel()
                GameState.PLAYING -> Unit
            }
            return
        }

        if (renderer.secondaryActionRect.contains(x, y)) {
            when (engine.state) {
                GameState.MENU -> engine.toggleMute()
                GameState.PAUSED, GameState.LEVEL_CLEAR, GameState.VICTORY, GameState.GAME_OVER -> engine.returnToMenu()
                GameState.PLAYING -> Unit
            }
        }
    }

    private fun clearTouchInputs() {
        joyPointerId = -1
        lookPointerId = -1
        firePointerId = -1
        engine.moveForwardInput = 0f
        engine.moveStrafeInput = 0f
        engine.isFiringHeld = false
    }

    private fun triggerHaptic(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (_: Throwable) {
            // Ignore devices without vibrator hardware
        }
    }
}
