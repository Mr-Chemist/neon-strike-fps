package ir.hiwa.neonstrike.graphics

import android.graphics.Color
import ir.hiwa.neonstrike.model.EnemyType
import ir.hiwa.neonstrike.model.PickupType
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

class TextureAtlas {

    companion object {
        const val TEX_SIZE = 64
    }

    val wallTextures: Map<Int, IntArray>
    val enemySprites: Map<EnemyType, IntArray>
    val enemyDeadSprites: Map<EnemyType, IntArray>
    val pickupSprites: Map<PickupType, IntArray>

    init {
        wallTextures = mapOf(
            1 to buildConcreteWall(),
            2 to buildNeonTechWall(),
            3 to buildHazardSteelWall(),
            4 to buildArmoredBulkheadWall(),
            5 to buildCrimsonReactorWall()
        )

        enemySprites = mapOf(
            EnemyType.DRONE to buildDroneSprite(dead = false),
            EnemyType.TROOPER to buildTrooperSprite(dead = false),
            EnemyType.MECH to buildMechSprite(dead = false)
        )

        enemyDeadSprites = mapOf(
            EnemyType.DRONE to buildDroneSprite(dead = true),
            EnemyType.TROOPER to buildTrooperSprite(dead = true),
            EnemyType.MECH to buildMechSprite(dead = true)
        )

        pickupSprites = mapOf(
            PickupType.HEALTH to buildMedkitSprite(),
            PickupType.AMMO to buildAmmoSprite(),
            PickupType.ARMOR to buildArmorSprite()
        )
    }

    private fun buildConcreteWall(): IntArray {
        val tex = IntArray(TEX_SIZE * TEX_SIZE)
        val rng = Random(101)
        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                val noise = rng.nextInt(-10, 11)
                val isHorizontalSeam = (y % 16 == 0)
                val offset = if ((y / 16) % 2 == 0) 0 else 16
                val isVerticalSeam = ((x + offset) % 32 == 0)
                val isBorder = (x == 0 || y == 0 || x == TEX_SIZE - 1 || y == TEX_SIZE - 1)
                val base = when {
                    isBorder -> 46
                    isHorizontalSeam || isVerticalSeam -> 54
                    else -> 88 + noise
                }
                val r = (base + 4).coerceIn(0, 255)
                val g = (base + 8).coerceIn(0, 255)
                val b = (base + 16).coerceIn(0, 255)
                tex[y * TEX_SIZE + x] = Color.rgb(r, g, b)
            }
        }
        return tex
    }

    private fun buildNeonTechWall(): IntArray {
        val tex = IntArray(TEX_SIZE * TEX_SIZE)
        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                val panelEdge = (x < 3 || x > 60 || y < 3 || y > 60)
                val neonStrip = (y in 10..13 && x in 8..55) || (y in 50..53 && x in 8..55)
                val circuitLine = (x == 31 || x == 32) && y in 14..49
                val coreGlow = hypot((x - 32).toDouble(), (y - 32).toDouble()) < 8.0
                tex[y * TEX_SIZE + x] = when {
                    neonStrip || coreGlow -> Color.rgb(0, 232, 255)
                    circuitLine -> Color.rgb(0, 145, 190)
                    panelEdge -> Color.rgb(24, 34, 52)
                    (x + y) % 8 == 0 -> Color.rgb(36, 48, 72)
                    else -> Color.rgb(30, 40, 62)
                }
            }
        }
        return tex
    }

    private fun buildHazardSteelWall(): IntArray {
        val tex = IntArray(TEX_SIZE * TEX_SIZE)
        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                val hazardBand = y in 44..56
                val stripe = ((x + y) / 8) % 2 == 0
                val rivet = ((x == 6 || x == 57) && (y == 6 || y == 36))
                tex[y * TEX_SIZE + x] = when {
                    hazardBand && stripe -> Color.rgb(235, 185, 24)
                    hazardBand -> Color.rgb(28, 28, 32)
                    rivet -> Color.rgb(165, 175, 188)
                    y == 43 || y == 57 || x == 0 || x == 63 -> Color.rgb(25, 28, 34)
                    else -> {
                        val shade = 74 + ((y % 4) * 3)
                        Color.rgb(shade, shade + 4, shade + 10)
                    }
                }
            }
        }
        return tex
    }

    private fun buildArmoredBulkheadWall(): IntArray {
        val tex = IntArray(TEX_SIZE * TEX_SIZE)
        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                val frame = x < 5 || x > 58 || y < 5 || y > 58
                val centerSplit = abs(x - 32) <= 1
                val light = y in 26..37 && (x in 14..18 || x in 45..49)
                tex[y * TEX_SIZE + x] = when {
                    light -> Color.rgb(255, 140, 40)
                    centerSplit -> Color.rgb(20, 24, 30)
                    frame -> Color.rgb(48, 56, 68)
                    y % 12 == 0 -> Color.rgb(60, 68, 82)
                    else -> Color.rgb(74, 84, 98)
                }
            }
        }
        return tex
    }

    private fun buildCrimsonReactorWall(): IntArray {
        val tex = IntArray(TEX_SIZE * TEX_SIZE)
        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                val dist = hypot((x - 32).toDouble(), (y - 32).toDouble())
                val grid = (x % 16 == 0 || y % 16 == 0)
                tex[y * TEX_SIZE + x] = when {
                    dist < 10.0 -> Color.rgb(255, 70, 95)
                    dist < 16.0 -> Color.rgb(190, 26, 56)
                    x in 29..34 || y in 29..34 -> Color.rgb(130, 24, 48)
                    grid -> Color.rgb(34, 18, 28)
                    else -> Color.rgb(52, 26, 40)
                }
            }
        }
        return tex
    }

    private fun buildDroneSprite(dead: Boolean): IntArray {
        val sprite = IntArray(TEX_SIZE * TEX_SIZE)
        if (dead) {
            for (y in 46..60) {
                for (x in 14..50) {
                    val d = hypot((x - 32).toDouble(), ((y - 53) * 2.2).toDouble())
                    if (d < 16.0) {
                        sprite[y * TEX_SIZE + x] = if (d < 6.0) Color.rgb(120, 30, 40) else Color.rgb(55, 62, 74)
                    }
                }
            }
            return sprite
        }

        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                val dx = x - 32
                val dy = y - 28
                val bodyDist = hypot(dx.toDouble(), dy.toDouble())
                val wing = abs(dy) <= 3 && abs(dx) in 14..26
                val antenna = abs(dx) <= 1 && y in 8..16
                sprite[y * TEX_SIZE + x] = when {
                    bodyDist < 5.0 -> Color.rgb(255, 42, 85)
                    bodyDist < 9.0 -> Color.rgb(255, 190, 60)
                    bodyDist < 15.0 -> Color.rgb(54, 72, 96)
                    bodyDist < 17.5 -> Color.rgb(0, 220, 255)
                    wing -> Color.rgb(80, 96, 120)
                    antenna -> Color.rgb(0, 220, 255)
                    else -> 0
                }
            }
        }
        return sprite
    }

    private fun buildTrooperSprite(dead: Boolean): IntArray {
        val sprite = IntArray(TEX_SIZE * TEX_SIZE)
        if (dead) {
            for (y in 50..62) {
                for (x in 10..54) {
                    if (abs(x - 32) < 20 && y in 52..60) {
                        sprite[y * TEX_SIZE + x] = if (x in 26..38 && y in 53..56) {
                            Color.rgb(160, 32, 52)
                        } else {
                            Color.rgb(58, 66, 80)
                        }
                    }
                }
            }
            return sprite
        }

        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                val head = hypot((x - 32).toDouble(), (y - 14).toDouble()) < 8.5
                val visor = y in 12..15 && x in 27..37
                val torso = y in 22..42 && x in 21..43
                val chestCore = y in 27..33 && x in 29..35
                val leftArm = y in 24..40 && x in 14..20
                val rightArm = y in 24..40 && x in 44..50
                val legs = y in 43..60 && (x in 22..29 || x in 35..42)
                sprite[y * TEX_SIZE + x] = when {
                    visor -> Color.rgb(255, 50, 80)
                    head -> Color.rgb(88, 102, 122)
                    chestCore -> Color.rgb(0, 230, 255)
                    torso -> Color.rgb(58, 70, 90)
                    leftArm || rightArm -> Color.rgb(74, 86, 106)
                    legs -> Color.rgb(44, 52, 68)
                    else -> 0
                }
            }
        }
        return sprite
    }

    private fun buildMechSprite(dead: Boolean): IntArray {
        val sprite = IntArray(TEX_SIZE * TEX_SIZE)
        if (dead) {
            for (y in 46..63) {
                for (x in 6..58) {
                    if (y > 49 && abs(x - 32) < 25) {
                        sprite[y * TEX_SIZE + x] = if ((x + y) % 7 == 0) {
                            Color.rgb(220, 70, 30)
                        } else {
                            Color.rgb(56, 48, 56)
                        }
                    }
                }
            }
            return sprite
        }

        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                val shoulders = y in 14..28 && (x in 8..20 || x in 44..56)
                val chassis = y in 12..42 && x in 18..46
                val headVisor = y in 16..20 && x in 24..40
                val reactorCore = hypot((x - 32).toDouble(), (y - 31).toDouble()) < 6.5
                val cannons = y in 28..38 && (x in 6..15 || x in 49..58)
                val legs = y in 43..62 && (x in 18..28 || x in 36..46)
                sprite[y * TEX_SIZE + x] = when {
                    headVisor -> Color.rgb(255, 40, 70)
                    reactorCore -> Color.rgb(255, 165, 30)
                    cannons -> Color.rgb(40, 44, 52)
                    shoulders -> Color.rgb(155, 42, 58)
                    chassis -> Color.rgb(92, 40, 54)
                    legs -> Color.rgb(62, 58, 70)
                    else -> 0
                }
            }
        }
        return sprite
    }

    private fun buildMedkitSprite(): IntArray {
        val sprite = IntArray(TEX_SIZE * TEX_SIZE)
        for (y in 34..58) {
            for (x in 16..48) {
                val border = x == 16 || x == 48 || y == 34 || y == 58
                val cross = (x in 29..35 && y in 39..53) || (y in 43..49 && x in 24..40)
                sprite[y * TEX_SIZE + x] = when {
                    border -> Color.rgb(175, 185, 195)
                    cross -> Color.rgb(235, 36, 62)
                    else -> Color.rgb(245, 250, 255)
                }
            }
        }
        return sprite
    }

    private fun buildAmmoSprite(): IntArray {
        val sprite = IntArray(TEX_SIZE * TEX_SIZE)
        for (y in 36..58) {
            for (x in 16..48) {
                val border = x == 16 || x == 48 || y == 36 || y == 58
                val stripe = y in 44..48 && x in 20..44
                sprite[y * TEX_SIZE + x] = when {
                    border -> Color.rgb(42, 54, 34)
                    stripe -> Color.rgb(250, 198, 42)
                    else -> Color.rgb(78, 108, 62)
                }
            }
        }
        return sprite
    }

    private fun buildArmorSprite(): IntArray {
        val sprite = IntArray(TEX_SIZE * TEX_SIZE)
        for (y in 32..58) {
            for (x in 18..46) {
                val dx = abs(x - 32)
                val maxWidth = if (y < 46) 13 else (13 - (y - 46)).coerceAtLeast(2)
                if (dx <= maxWidth) {
                    val edge = dx >= maxWidth - 2 || y <= 34
                    sprite[y * TEX_SIZE + x] = when {
                        edge -> Color.rgb(0, 232, 255)
                        dx < 4 && y in 39..49 -> Color.rgb(210, 250, 255)
                        else -> Color.rgb(24, 118, 182)
                    }
                }
            }
        }
        return sprite
    }
}
