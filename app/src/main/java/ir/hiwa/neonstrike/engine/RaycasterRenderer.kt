package ir.hiwa.neonstrike.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import ir.hiwa.neonstrike.graphics.TextureAtlas
import ir.hiwa.neonstrike.model.Enemy
import ir.hiwa.neonstrike.model.GameState
import ir.hiwa.neonstrike.model.Pickup
import ir.hiwa.neonstrike.model.WeaponType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

class RaycasterRenderer(
    private val atlas: TextureAtlas
) {
    companion object {
        private const val RENDER_W = 320
        private const val RENDER_H = 180
        private const val FOV_PLANE = 0.66f
    }

    private val frameBuffer = IntArray(RENDER_W * RENDER_H)
    private val zBuffer = FloatArray(RENDER_W)
    private val frameBitmap: Bitmap = Bitmap.createBitmap(RENDER_W, RENDER_H, Bitmap.Config.ARGB_8888)
    private val srcRect = Rect(0, 0, RENDER_W, RENDER_H)
    private val dstRect = Rect()

    private val bitmapPaint = Paint().apply {
        isFilterBitmap = true
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    // Dynamic touch hitboxes computed every frame for GameSurfaceView
    val fireButtonRect = RectF()
    val reloadButtonRect = RectF()
    val switchButtonRect = RectF()
    val pauseButtonRect = RectF()
    val muteButtonRect = RectF()
    val primaryActionRect = RectF()
    val secondaryActionRect = RectF()
    var joystickCenterX = 0f
    var joystickCenterY = 0f
    var joystickRadius = 0f

    private data class RenderSprite(
        val x: Float,
        val y: Float,
        val distSq: Float,
        val pixels: IntArray,
        val scale: Float,
        val hitFlash: Boolean,
        val attackFlash: Boolean,
        val hpRatio: Float,
        val showHpBar: Boolean,
        val verticalOffset: Float
    )

    private val spriteList = ArrayList<RenderSprite>(32)

    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        engine: GameEngine,
        joyKnobX: Float,
        joyKnobY: Float,
        joyActive: Boolean
    ) {
        updateUiLayout(width, height)

        // 1. Render 3D scene via DDA raycasting into pixel buffer
        render3DWorld(engine)
        frameBitmap.setPixels(frameBuffer, 0, RENDER_W, 0, 0, RENDER_W, RENDER_H)
        dstRect.set(0, 0, width, height)
        canvas.drawBitmap(frameBitmap, srcRect, dstRect, bitmapPaint)

        // 2. Render first-person weapon & HUD when active
        if (engine.state == GameState.PLAYING || engine.state == GameState.PAUSED) {
            drawWeaponViewModel(canvas, width, height, engine)
            drawScreenFlashes(canvas, width, height, engine)
            drawCrosshair(canvas, width, height, engine)
            drawMinimap(canvas, width, height, engine)
            drawTopStatusBar(canvas, width, height, engine)
            drawBottomHud(canvas, width, height, engine)
            drawTouchControls(canvas, width, height, engine, joyKnobX, joyKnobY, joyActive)
            drawBanner(canvas, width, height, engine)
        }

        // 3. Render state overlays (Menu, Pause, Level Clear, Victory, Game Over)
        when (engine.state) {
            GameState.MENU -> drawOverlay(
                canvas, width, height,
                title = "NEON STRIKE FPS",
                subtitle = "TACTICAL FIRST-PERSON SHOOTER",
                infoLines = listOf(
                    "LEFT STICK: MOVE & STRAFE   |   RIGHT SCREEN: AIM 360°",
                    "3 WEAPONS  •  3 SECTORS  •  ELIMINATE ALL HOSTILES"
                ),
                primaryLabel = "START MISSION",
                secondaryLabel = if (engine.soundEngine.isMuted) "SOUND: OFF" else "SOUND: ON",
                accentColor = Color.rgb(0, 229, 255)
            )

            GameState.PAUSED -> drawOverlay(
                canvas, width, height,
                title = "MISSION PAUSED",
                subtitle = "${engine.level.title} — ${engine.level.subtitle}",
                infoLines = listOf(
                    "SCORE: ${engine.score}   |   HOSTILES LEFT: ${engine.remainingEnemies}"
                ),
                primaryLabel = "RESUME",
                secondaryLabel = "MAIN MENU",
                accentColor = Color.rgb(0, 229, 255)
            )

            GameState.LEVEL_CLEAR -> drawOverlay(
                canvas, width, height,
                title = "${engine.level.title} CLEARED!",
                subtitle = "ALL HOSTILES NEUTRALIZED",
                infoLines = listOf(
                    "SCORE: ${engine.score}   |   TOTAL KILLS: ${engine.totalKills}",
                    "+25 HEALTH BONUS AWARDED FOR NEXT SECTOR"
                ),
                primaryLabel = "NEXT SECTOR",
                secondaryLabel = "MAIN MENU",
                accentColor = Color.rgb(46, 232, 138)
            )

            GameState.VICTORY -> drawOverlay(
                canvas, width, height,
                title = "MISSION COMPLETE",
                subtitle = "CITADEL SECURED — YOU WIN!",
                infoLines = listOf(
                    "FINAL SCORE: ${engine.score}   |   TOTAL KILLS: ${engine.totalKills}"
                ),
                primaryLabel = "PLAY AGAIN",
                secondaryLabel = "MAIN MENU",
                accentColor = Color.rgb(255, 205, 45)
            )

            GameState.GAME_OVER -> drawOverlay(
                canvas, width, height,
                title = "AGENT DOWN",
                subtitle = "MISSION FAILED IN ${engine.level.title}",
                infoLines = listOf(
                    "SCORE: ${engine.score}   |   TOTAL KILLS: ${engine.totalKills}"
                ),
                primaryLabel = "RETRY MISSION",
                secondaryLabel = "MAIN MENU",
                accentColor = Color.rgb(255, 56, 86)
            )

            GameState.PLAYING -> Unit
        }
    }

    private fun updateUiLayout(w: Int, h: Int) {
        val minDim = minOf(w, h).toFloat()
        joystickRadius = minDim * 0.165f
        joystickCenterX = w * 0.135f
        joystickCenterY = h * 0.75f

        val fireSize = minDim * 0.21f
        val fireCx = w * 0.875f
        val fireCy = h * 0.72f
        fireButtonRect.set(
            fireCx - fireSize * 0.5f,
            fireCy - fireSize * 0.5f,
            fireCx + fireSize * 0.5f,
            fireCy + fireSize * 0.5f
        )

        val smallSize = minDim * 0.125f
        reloadButtonRect.set(
            w * 0.73f - smallSize * 0.5f,
            h * 0.82f - smallSize * 0.5f,
            w * 0.73f + smallSize * 0.5f,
            h * 0.82f + smallSize * 0.5f
        )
        switchButtonRect.set(
            w * 0.89f - smallSize * 0.5f,
            h * 0.45f - smallSize * 0.5f,
            w * 0.89f + smallSize * 0.5f,
            h * 0.45f + smallSize * 0.5f
        )

        val topBtn = minDim * 0.09f
        pauseButtonRect.set(w - topBtn - 18f, 16f, w - 18f, 16f + topBtn)
        muteButtonRect.set(w - topBtn * 2f - 30f, 16f, w - topBtn - 30f, 16f + topBtn)

        val btnW = w * 0.28f
        val btnH = h * 0.125f
        primaryActionRect.set(
            w * 0.5f - btnW * 0.5f,
            h * 0.59f,
            w * 0.5f + btnW * 0.5f,
            h * 0.59f + btnH
        )
        secondaryActionRect.set(
            w * 0.5f - btnW * 0.44f,
            h * 0.75f,
            w * 0.5f + btnW * 0.44f,
            h * 0.75f + btnH * 0.85f
        )
    }

    private fun render3DWorld(engine: GameEngine) {
        val level = engine.level
        val halfH = RENDER_H / 2

        // Ceiling & floor vertical gradient
        for (y in 0 until halfH) {
            val t = y.toFloat() / halfH
            val cColor = lerpColor(level.ceilingTopColor, level.ceilingHorizonColor, t)
            val fColor = lerpColor(level.floorHorizonColor, level.floorBottomColor, t)
            val cRow = y * RENDER_W
            val fRow = (halfH + y) * RENDER_W
            for (x in 0 until RENDER_W) {
                frameBuffer[cRow + x] = cColor
                frameBuffer[fRow + x] = fColor
            }
        }

        val posX = engine.playerX
        val posY = engine.playerY
        val dirX = cos(engine.playerAngle)
        val dirY = sin(engine.playerAngle)
        val planeX = -dirY * FOV_PLANE
        val planeY = dirX * FOV_PLANE

        // Cast vertical rays
        for (x in 0 until RENDER_W) {
            val cameraX = 2f * x / RENDER_W - 1f
            val rayDirX = dirX + planeX * cameraX
            val rayDirY = dirY + planeY * cameraX

            var mapX = floor(posX).toInt()
            var mapY = floor(posY).toInt()

            val deltaDistX = if (abs(rayDirX) < 1e-6f) 1e6f else abs(1f / rayDirX)
            val deltaDistY = if (abs(rayDirY) < 1e-6f) 1e6f else abs(1f / rayDirY)

            val stepX: Int
            var sideDistX: Float
            if (rayDirX < 0f) {
                stepX = -1
                sideDistX = (posX - mapX) * deltaDistX
            } else {
                stepX = 1
                sideDistX = (mapX + 1f - posX) * deltaDistX
            }

            val stepY: Int
            var sideDistY: Float
            if (rayDirY < 0f) {
                stepY = -1
                sideDistY = (posY - mapY) * deltaDistY
            } else {
                stepY = 1
                sideDistY = (mapY + 1f - posY) * deltaDistY
            }

            var hit = false
            var side = 0
            var steps = 0
            while (!hit && steps < 48) {
                steps++
                if (sideDistX < sideDistY) {
                    sideDistX += deltaDistX
                    mapX += stepX
                    side = 0
                } else {
                    sideDistY += deltaDistY
                    mapY += stepY
                    side = 1
                }
                if (level.isWall(mapX, mapY)) {
                    hit = true
                }
            }

            val perpWallDist = (if (side == 0) sideDistX - deltaDistX else sideDistY - deltaDistY)
                .coerceAtLeast(0.08f)
            zBuffer[x] = perpWallDist

            val lineHeight = (RENDER_H / perpWallDist).toInt()
            val drawStart = (-lineHeight / 2 + halfH).coerceAtLeast(0)
            val drawEnd = (lineHeight / 2 + halfH).coerceAtMost(RENDER_H - 1)

            val wallId = level.wallAt(mapX, mapY)
            val tex = atlas.wallTextures[wallId] ?: atlas.wallTextures[1]!!

            var wallX = if (side == 0) {
                posY + perpWallDist * rayDirY
            } else {
                posX + perpWallDist * rayDirX
            }
            wallX -= floor(wallX)

            var texX = (wallX * TextureAtlas.TEX_SIZE).toInt()
                .coerceIn(0, TextureAtlas.TEX_SIZE - 1)
            if ((side == 0 && rayDirX > 0f) || (side == 1 && rayDirY < 0f)) {
                texX = TextureAtlas.TEX_SIZE - texX - 1
            }

            val step = TextureAtlas.TEX_SIZE.toFloat() / lineHeight.coerceAtLeast(1)
            var texPos = (drawStart - halfH + lineHeight / 2f) * step

            val fogFactor = (1f / (1f + perpWallDist * perpWallDist * 0.045f)).coerceIn(0.18f, 1f)
            val sideFactor = if (side == 1) 0.76f else 1f
            val shade = fogFactor * sideFactor

            for (y in drawStart..drawEnd) {
                val texY = texPos.toInt().coerceIn(0, TextureAtlas.TEX_SIZE - 1)
                texPos += step
                val rawColor = tex[texY * TextureAtlas.TEX_SIZE + texX]
                frameBuffer[y * RENDER_W + x] = shadeColor(rawColor, shade)
            }
        }

        // Collect & depth-sort sprites (enemies + pickups)
        spriteList.clear()
        for (enemy in engine.enemies) {
            val dx = enemy.x - posX
            val dy = enemy.y - posY
            val pixels = if (enemy.isAlive) {
                atlas.enemySprites[enemy.type]!!
            } else {
                atlas.enemyDeadSprites[enemy.type]!!
            }
            val hoverOffset = if (enemy.isAlive && enemy.type.name == "DRONE") {
                sin(enemy.animPhase) * 0.08f
            } else 0f
            spriteList.add(
                RenderSprite(
                    x = enemy.x,
                    y = enemy.y,
                    distSq = dx * dx + dy * dy,
                    pixels = pixels,
                    scale = enemy.type.spriteScale,
                    hitFlash = enemy.hitFlashTimer > 0f,
                    attackFlash = enemy.attackFlashTimer > 0f,
                    hpRatio = enemy.hp.toFloat() / enemy.type.maxHp.toFloat(),
                    showHpBar = enemy.isAlive && enemy.hp < enemy.type.maxHp,
                    verticalOffset = hoverOffset
                )
            )
        }

        for (pickup in engine.pickups) {
            if (pickup.collected) continue
            val dx = pickup.x - posX
            val dy = pickup.y - posY
            spriteList.add(
                RenderSprite(
                    x = pickup.x,
                    y = pickup.y,
                    distSq = dx * dx + dy * dy,
                    pixels = atlas.pickupSprites[pickup.type]!!,
                    scale = 0.65f,
                    hitFlash = false,
                    attackFlash = false,
                    hpRatio = 1f,
                    showHpBar = false,
                    verticalOffset = 0.16f + sin(pickup.bobPhase) * 0.04f
                )
            )
        }

        // Far-to-near painter's algorithm
        spriteList.sortByDescending { it.distSq }

        val invDet = 1f / (planeX * dirY - dirX * planeY)

        for (sp in spriteList) {
            val spriteX = sp.x - posX
            val spriteY = sp.y - posY

            val transformX = invDet * (dirY * spriteX - dirX * spriteY)
            val transformY = invDet * (-planeY * spriteX + planeX * spriteY)
            if (transformY <= 0.18f) continue

            val spriteScreenX = ((RENDER_W / 2f) * (1f + transformX / transformY)).toInt()
            val spriteSize = abs((RENDER_H / transformY) * sp.scale).toInt().coerceAtMost(RENDER_H * 3)
            val vShift = ((sp.verticalOffset * RENDER_H) / transformY).toInt()

            val drawStartY = (-spriteSize / 2 + halfH + vShift).coerceAtLeast(0)
            val drawEndY = (spriteSize / 2 + halfH + vShift).coerceAtMost(RENDER_H - 1)
            val drawStartX = (-spriteSize / 2 + spriteScreenX).coerceAtLeast(0)
            val drawEndX = (spriteSize / 2 + spriteScreenX).coerceAtMost(RENDER_W - 1)

            val fog = (1f / (1f + transformY * transformY * 0.04f)).coerceIn(0.24f, 1f)

            for (stripe in drawStartX..drawEndX) {
                if (transformY >= zBuffer[stripe]) continue
                val texX = ((stripe - (-spriteSize / 2 + spriteScreenX)) * TextureAtlas.TEX_SIZE /
                    spriteSize.coerceAtLeast(1)).coerceIn(0, TextureAtlas.TEX_SIZE - 1)

                for (y in drawStartY..drawEndY) {
                    val d = y - vShift - halfH + spriteSize / 2
                    val texY = ((d * TextureAtlas.TEX_SIZE) / spriteSize.coerceAtLeast(1))
                        .coerceIn(0, TextureAtlas.TEX_SIZE - 1)
                    val pixel = sp.pixels[texY * TextureAtlas.TEX_SIZE + texX]
                    if (pixel != 0) {
                        frameBuffer[y * RENDER_W + stripe] = when {
                            sp.hitFlash -> Color.rgb(255, 235, 235)
                            sp.attackFlash -> Color.rgb(255, 170, 50)
                            else -> shadeColor(pixel, fog)
                        }
                    }
                }
            }

            // Floating mini HP bar above damaged enemy
            if (sp.showHpBar && drawStartY > 4 && spriteScreenX in 12..(RENDER_W - 12)) {
                val barW = (spriteSize * 0.55f).toInt().coerceIn(10, 38)
                val barStartX = (spriteScreenX - barW / 2).coerceIn(1, RENDER_W - 2)
                val barEndX = (spriteScreenX + barW / 2).coerceIn(1, RENDER_W - 2)
                val barY = (drawStartY - 3).coerceIn(1, RENDER_H - 3)
                val fillEndX = barStartX + ((barEndX - barStartX) * sp.hpRatio).toInt()
                for (bx in barStartX..barEndX) {
                    if (transformY < zBuffer[bx]) {
                        val c = if (bx <= fillEndX) Color.rgb(255, 50, 80) else Color.rgb(35, 35, 45)
                        frameBuffer[barY * RENDER_W + bx] = c
                        frameBuffer[(barY + 1) * RENDER_W + bx] = c
                    }
                }
            }
        }
    }

    private fun drawWeaponViewModel(canvas: Canvas, w: Int, h: Int, engine: GameEngine) {
        val weapon = engine.currentWeapon.type
        val bobX = sin(engine.walkBobPhase) * w * 0.012f
        val bobY = abs(cos(engine.walkBobPhase)) * h * 0.016f
        val recoilY = engine.recoilAmount * h * 0.055f
        val reloadDropY = if (engine.isReloading) {
            val progress = 1f - (engine.reloadTimer / weapon.reloadTime).coerceIn(0f, 1f)
            sin(progress * Math.PI).toFloat() * h * 0.24f
        } else 0f

        val baseX = w * 0.56f + bobX
        val baseY = h * 0.99f + bobY + recoilY + reloadDropY

        // Muzzle flash
        if (engine.muzzleFlashTimer > 0f) {
            val flashX = baseX - w * 0.01f
            val flashY = baseY - h * 0.34f
            fillPaint.color = Color.argb(210, 255, 225, 95)
            canvas.drawCircle(flashX, flashY, h * 0.075f, fillPaint)
            fillPaint.color = Color.argb(240, 255, 255, 235)
            canvas.drawCircle(flashX, flashY, h * 0.038f, fillPaint)
        }

        when (weapon) {
            WeaponType.PISTOL -> {
                // Slide & barrel
                fillPaint.color = Color.rgb(42, 48, 60)
                canvas.drawRoundRect(baseX - w * 0.038f, baseY - h * 0.30f, baseX + w * 0.038f, baseY - h * 0.08f, 12f, 12f, fillPaint)
                fillPaint.color = Color.rgb(72, 82, 102)
                canvas.drawRoundRect(baseX - w * 0.026f, baseY - h * 0.31f, baseX + w * 0.026f, baseY - h * 0.16f, 8f, 8f, fillPaint)
                // Neon sight accent
                fillPaint.color = Color.rgb(0, 229, 255)
                canvas.drawRect(baseX - w * 0.018f, baseY - h * 0.27f, baseX + w * 0.018f, baseY - h * 0.255f, fillPaint)
                // Grip
                fillPaint.color = Color.rgb(26, 30, 38)
                canvas.drawRect(baseX - w * 0.032f, baseY - h * 0.12f, baseX + w * 0.036f, baseY + h * 0.05f, fillPaint)
            }

            WeaponType.SHOTGUN -> {
                // Twin heavy barrel
                fillPaint.color = Color.rgb(52, 56, 66)
                canvas.drawRoundRect(baseX - w * 0.052f, baseY - h * 0.36f, baseX + w * 0.052f, baseY - h * 0.08f, 14f, 14f, fillPaint)
                fillPaint.color = Color.rgb(28, 30, 36)
                canvas.drawCircle(baseX - w * 0.02f, baseY - h * 0.35f, w * 0.014f, fillPaint)
                canvas.drawCircle(baseX + w * 0.02f, baseY - h * 0.35f, w * 0.014f, fillPaint)
                // Tactical pump forend
                fillPaint.color = Color.rgb(190, 95, 32)
                canvas.drawRoundRect(baseX - w * 0.06f, baseY - h * 0.22f, baseX + w * 0.06f, baseY - h * 0.09f, 10f, 10f, fillPaint)
                // Receiver
                fillPaint.color = Color.rgb(34, 38, 46)
                canvas.drawRect(baseX - w * 0.048f, baseY - h * 0.10f, baseX + w * 0.052f, baseY + h * 0.05f, fillPaint)
            }

            WeaponType.RIFLE -> {
                // Sleek pulse rifle body
                fillPaint.color = Color.rgb(36, 44, 58)
                canvas.drawRoundRect(baseX - w * 0.046f, baseY - h * 0.37f, baseX + w * 0.046f, baseY + h * 0.05f, 14f, 14f, fillPaint)
                // Top rail
                fillPaint.color = Color.rgb(66, 78, 98)
                canvas.drawRect(baseX - w * 0.022f, baseY - h * 0.39f, baseX + w * 0.022f, baseY - h * 0.12f, fillPaint)
                // Glowing energy coils
                fillPaint.color = Color.rgb(0, 232, 255)
                canvas.drawRect(baseX - w * 0.036f, baseY - h * 0.30f, baseX - w * 0.024f, baseY - h * 0.14f, fillPaint)
                canvas.drawRect(baseX + w * 0.024f, baseY - h * 0.30f, baseX + w * 0.036f, baseY - h * 0.14f, fillPaint)
                // Holo optic
                strokePaint.color = Color.rgb(0, 232, 255)
                strokePaint.strokeWidth = 3f
                canvas.drawRect(baseX - w * 0.024f, baseY - h * 0.34f, baseX + w * 0.024f, baseY - h * 0.28f, strokePaint)
            }
        }
    }

    private fun drawScreenFlashes(canvas: Canvas, w: Int, h: Int, engine: GameEngine) {
        if (engine.damageFlashTimer > 0f) {
            val alpha = ((engine.damageFlashTimer / 0.34f) * 145).toInt().coerceIn(0, 145)
            fillPaint.color = Color.argb(alpha, 255, 24, 48)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)
        }
        if (engine.pickupFlashTimer > 0f) {
            val alpha = ((engine.pickupFlashTimer / 0.25f) * 85).toInt().coerceIn(0, 85)
            fillPaint.color = Color.argb(alpha, 0, 232, 180)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)
        }
    }

    private fun drawCrosshair(canvas: Canvas, w: Int, h: Int, engine: GameEngine) {
        val cx = w * 0.5f
        val cy = h * 0.5f
        val gap = 10f + engine.recoilAmount * 14f
        val len = 14f

        strokePaint.color = if (engine.isReloading) Color.rgb(255, 195, 45) else Color.argb(220, 0, 235, 255)
        strokePaint.strokeWidth = 3f
        canvas.drawLine(cx - gap - len, cy, cx - gap, cy, strokePaint)
        canvas.drawLine(cx + gap, cy, cx + gap + len, cy, strokePaint)
        canvas.drawLine(cx, cy - gap - len, cx, cy - gap, strokePaint)
        canvas.drawLine(cx, cy + gap, cx, cy + gap + len, strokePaint)

        fillPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, 2.5f, fillPaint)

        // Hit-marker X
        if (engine.hitMarkerTimer > 0f) {
            strokePaint.color = Color.rgb(255, 48, 76)
            strokePaint.strokeWidth = 4f
            val m = 13f
            canvas.drawLine(cx - m, cy - m, cx - 5f, cy - 5f, strokePaint)
            canvas.drawLine(cx + m, cy - m, cx + 5f, cy - 5f, strokePaint)
            canvas.drawLine(cx - m, cy + m, cx - 5f, cy + 5f, strokePaint)
            canvas.drawLine(cx + m, cy + m, cx + 5f, cy + 5f, strokePaint)
        }
    }

    private fun drawMinimap(canvas: Canvas, w: Int, h: Int, engine: GameEngine) {
        val mapSize = minOf(w, h) * 0.26f
        val left = 18f
        val top = 16f
        val cell = mapSize / engine.level.width

        fillPaint.color = Color.argb(170, 8, 12, 22)
        canvas.drawRoundRect(left - 4f, top - 4f, left + mapSize + 4f, top + mapSize + 4f, 10f, 10f, fillPaint)
        strokePaint.color = Color.argb(170, 0, 229, 255)
        strokePaint.strokeWidth = 2f
        canvas.drawRoundRect(left - 4f, top - 4f, left + mapSize + 4f, top + mapSize + 4f, 10f, 10f, strokePaint)

        for (y in 0 until engine.level.height) {
            for (x in 0 until engine.level.width) {
                val wall = engine.level.wallAt(x, y)
                if (wall > 0) {
                    fillPaint.color = when (wall) {
                        2 -> Color.rgb(0, 170, 210)
                        3 -> Color.rgb(210, 165, 30)
                        5 -> Color.rgb(190, 40, 68)
                        else -> Color.rgb(88, 102, 124)
                    }
                    canvas.drawRect(
                        left + x * cell,
                        top + y * cell,
                        left + (x + 1) * cell,
                        top + (y + 1) * cell,
                        fillPaint
                    )
                }
            }
        }

        // Pickups on radar
        for (p in engine.pickups) {
            if (p.collected) continue
            fillPaint.color = Color.rgb(65, 240, 140)
            canvas.drawCircle(left + p.x * cell, top + p.y * cell, cell * 0.26f, fillPaint)
        }

        // Alive enemies on radar
        for (e in engine.enemies) {
            if (!e.isAlive) continue
            fillPaint.color = Color.rgb(255, 45, 78)
            canvas.drawCircle(left + e.x * cell, top + e.y * cell, cell * 0.34f, fillPaint)
        }

        // Player & look ray
        val px = left + engine.playerX * cell
        val py = top + engine.playerY * cell
        strokePaint.color = Color.rgb(0, 235, 255)
        strokePaint.strokeWidth = 2.5f
        canvas.drawLine(
            px, py,
            px + cos(engine.playerAngle) * cell * 1.9f,
            py + sin(engine.playerAngle) * cell * 1.9f,
            strokePaint
        )
        fillPaint.color = Color.WHITE
        canvas.drawCircle(px, py, cell * 0.38f, fillPaint)
    }

    private fun drawTopStatusBar(canvas: Canvas, w: Int, h: Int, engine: GameEngine) {
        val barW = w * 0.38f
        val barH = minOf(w, h) * 0.088f
        val left = (w - barW) * 0.5f
        val top = 16f

        fillPaint.color = Color.argb(175, 10, 16, 28)
        canvas.drawRoundRect(left, top, left + barW, top + barH, 12f, 12f, fillPaint)
        strokePaint.color = Color.argb(150, 0, 229, 255)
        strokePaint.strokeWidth = 2f
        canvas.drawRoundRect(left, top, left + barW, top + barH, 12f, 12f, strokePaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = barH * 0.44f
        textPaint.color = Color.WHITE
        val summary = "${engine.level.title}   |   HOSTILES: ${engine.remainingEnemies}   |   SCORE: ${engine.score}"
        canvas.drawText(summary, w * 0.5f, top + barH * 0.66f, textPaint)
    }

    private fun drawBottomHud(canvas: Canvas, w: Int, h: Int, engine: GameEngine) {
        val minDim = minOf(w, h).toFloat()
        val panelH = minDim * 0.12f
        val panelW = w * 0.25f
        val leftX = w * 0.27f
        val topY = h - panelH - 16f

        // Health & Armor card
        fillPaint.color = Color.argb(175, 10, 16, 28)
        canvas.drawRoundRect(leftX, topY, leftX + panelW, topY + panelH, 12f, 12f, fillPaint)

        val pad = 12f
        val barW = panelW - pad * 2f
        val barH = panelH * 0.24f

        // Health bar
        fillPaint.color = Color.rgb(42, 22, 30)
        canvas.drawRoundRect(leftX + pad, topY + pad, leftX + pad + barW, topY + pad + barH, 6f, 6f, fillPaint)
        fillPaint.color = if (engine.health > 30) Color.rgb(42, 232, 132) else Color.rgb(255, 52, 76)
        canvas.drawRoundRect(leftX + pad, topY + pad, leftX + pad + barW * (engine.health / 100f), topY + pad + barH, 6f, 6f, fillPaint)

        // Armor bar
        val armorTop = topY + pad + barH + 8f
        fillPaint.color = Color.rgb(22, 36, 52)
        canvas.drawRoundRect(leftX + pad, armorTop, leftX + pad + barW, armorTop + barH, 6f, 6f, fillPaint)
        fillPaint.color = Color.rgb(0, 200, 255)
        canvas.drawRoundRect(leftX + pad, armorTop, leftX + pad + barW * (engine.armor / 100f), armorTop + barH, 6f, 6f, fillPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = panelH * 0.23f
        textPaint.color = Color.WHITE
        canvas.drawText("HP ${engine.health}   ARM ${engine.armor}", leftX + pad, topY + panelH - 8f, textPaint)

        // Weapon & Ammo card
        val rightX = w * 0.54f
        val wCardW = w * 0.145f
        fillPaint.color = Color.argb(175, 10, 16, 28)
        canvas.drawRoundRect(rightX, topY, rightX + wCardW, topY + panelH, 12f, 12f, fillPaint)

        val weapon = engine.currentWeapon
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = panelH * 0.24f
        textPaint.color = Color.rgb(0, 229, 255)
        canvas.drawText(weapon.type.displayName, rightX + wCardW * 0.5f, topY + panelH * 0.36f, textPaint)

        textPaint.textSize = panelH * 0.36f
        textPaint.color = if (engine.isReloading) Color.rgb(255, 205, 45) else Color.WHITE
        val ammoLabel = if (engine.isReloading) "RELOAD..." else "${weapon.ammoInMag} / ${weapon.reserveAmmo}"
        canvas.drawText(ammoLabel, rightX + wCardW * 0.5f, topY + panelH * 0.78f, textPaint)
    }

    private fun drawTouchControls(
        canvas: Canvas,
        w: Int,
        h: Int,
        engine: GameEngine,
        joyKnobX: Float,
        joyKnobY: Float,
        joyActive: Boolean
    ) {
        // Left virtual joystick
        fillPaint.color = Color.argb(72, 16, 26, 44)
        canvas.drawCircle(joystickCenterX, joystickCenterY, joystickRadius, fillPaint)
        strokePaint.color = Color.argb(150, 0, 229, 255)
        strokePaint.strokeWidth = 3f
        canvas.drawCircle(joystickCenterX, joystickCenterY, joystickRadius, strokePaint)

        val kx = if (joyActive) joyKnobX else joystickCenterX
        val ky = if (joyActive) joyKnobY else joystickCenterY
        fillPaint.color = Color.argb(175, 0, 229, 255)
        canvas.drawCircle(kx, ky, joystickRadius * 0.38f, fillPaint)

        // Fire button
        fillPaint.color = if (engine.isFiringHeld) Color.argb(215, 255, 46, 86) else Color.argb(145, 220, 38, 72)
        canvas.drawOval(fireButtonRect, fillPaint)
        strokePaint.color = Color.argb(230, 255, 160, 180)
        strokePaint.strokeWidth = 3.5f
        canvas.drawOval(fireButtonRect, strokePaint)
        drawCenteredButtonText(canvas, fireButtonRect, "FIRE", fireButtonRect.height() * 0.28f, Color.WHITE)

        // Reload button
        drawCircularButton(canvas, reloadButtonRect, "RLD", Color.argb(145, 24, 42, 68), Color.rgb(255, 205, 45))

        // Switch Weapon button
        drawCircularButton(canvas, switchButtonRect, "GUN", Color.argb(145, 24, 42, 68), Color.rgb(0, 229, 255))

        // Pause & Mute buttons
        drawRectButton(canvas, pauseButtonRect, "||", Color.argb(155, 16, 24, 38), Color.WHITE)
        drawRectButton(
            canvas,
            muteButtonRect,
            if (engine.soundEngine.isMuted) "MUTE" else "SFX",
            Color.argb(155, 16, 24, 38),
            if (engine.soundEngine.isMuted) Color.rgb(255, 85, 100) else Color.rgb(0, 229, 255)
        )
    }

    private fun drawBanner(canvas: Canvas, w: Int, h: Int, engine: GameEngine) {
        if (engine.bannerTimer <= 0f || engine.bannerText.isEmpty()) return
        val alpha = (engine.bannerTimer.coerceAtMost(0.5f) / 0.5f * 255).toInt().coerceIn(0, 255)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = minOf(w, h) * 0.048f
        textPaint.color = Color.argb(alpha, 0, 238, 255)
        canvas.drawText(engine.bannerText, w * 0.5f, h * 0.23f, textPaint)
    }

    private fun drawOverlay(
        canvas: Canvas,
        w: Int,
        h: Int,
        title: String,
        subtitle: String,
        infoLines: List<String>,
        primaryLabel: String,
        secondaryLabel: String,
        accentColor: Int
    ) {
        fillPaint.color = Color.argb(205, 6, 10, 20)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        val cardW = w * 0.72f
        val cardH = h * 0.78f
        val left = (w - cardW) * 0.5f
        val top = (h - cardH) * 0.5f

        fillPaint.color = Color.argb(230, 12, 18, 32)
        canvas.drawRoundRect(left, top, left + cardW, top + cardH, 24f, 24f, fillPaint)
        strokePaint.color = accentColor
        strokePaint.strokeWidth = 4f
        canvas.drawRoundRect(left, top, left + cardW, top + cardH, 24f, 24f, strokePaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = accentColor
        textPaint.textSize = minOf(w, h) * 0.082f
        canvas.drawText(title, w * 0.5f, h * 0.28f, textPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = minOf(w, h) * 0.036f
        canvas.drawText(subtitle, w * 0.5f, h * 0.36f, textPaint)

        textPaint.color = Color.rgb(180, 198, 222)
        textPaint.textSize = minOf(w, h) * 0.028f
        infoLines.forEachIndexed { idx, line ->
            canvas.drawText(line, w * 0.5f, h * (0.45f + idx * 0.055f), textPaint)
        }

        // Primary button
        fillPaint.color = accentColor
        canvas.drawRoundRect(primaryActionRect, 16f, 16f, fillPaint)
        drawCenteredButtonText(canvas, primaryActionRect, primaryLabel, primaryActionRect.height() * 0.42f, Color.rgb(10, 14, 24))

        // Secondary button
        fillPaint.color = Color.rgb(26, 36, 56)
        canvas.drawRoundRect(secondaryActionRect, 14f, 14f, fillPaint)
        strokePaint.color = Color.rgb(110, 130, 160)
        strokePaint.strokeWidth = 2.5f
        canvas.drawRoundRect(secondaryActionRect, 14f, 14f, strokePaint)
        drawCenteredButtonText(canvas, secondaryActionRect, secondaryLabel, secondaryActionRect.height() * 0.40f, Color.WHITE)
    }

    private fun drawCircularButton(canvas: Canvas, rect: RectF, label: String, bgColor: Int, borderColor: Int) {
        fillPaint.color = bgColor
        canvas.drawOval(rect, fillPaint)
        strokePaint.color = borderColor
        strokePaint.strokeWidth = 3f
        canvas.drawOval(rect, strokePaint)
        drawCenteredButtonText(canvas, rect, label, rect.height() * 0.30f, Color.WHITE)
    }

    private fun drawRectButton(canvas: Canvas, rect: RectF, label: String, bgColor: Int, textColor: Int) {
        fillPaint.color = bgColor
        canvas.drawRoundRect(rect, 10f, 10f, fillPaint)
        strokePaint.color = Color.argb(150, 140, 165, 195)
        strokePaint.strokeWidth = 2f
        canvas.drawRoundRect(rect, 10f, 10f, strokePaint)
        drawCenteredButtonText(canvas, rect, label, rect.height() * 0.36f, textColor)
    }

    private fun drawCenteredButtonText(canvas: Canvas, rect: RectF, text: String, size: Float, color: Int) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = size
        textPaint.color = color
        val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) * 0.5f
        canvas.drawText(text, rect.centerX(), baseline, textPaint)
    }

    private fun shadeColor(color: Int, factor: Float): Int {
        val r = (((color shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((color shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF
        val r = (r1 + (r2 - r1) * t).toInt().coerceIn(0, 255)
        val g = (g1 + (g2 - g1) * t).toInt().coerceIn(0, 255)
        val b = (b1 + (b2 - b1) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
