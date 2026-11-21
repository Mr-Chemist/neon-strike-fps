package ir.hiwa.neonstrike.engine

import ir.hiwa.neonstrike.audio.SoundEngine
import ir.hiwa.neonstrike.level.LevelManager
import ir.hiwa.neonstrike.model.Enemy
import ir.hiwa.neonstrike.model.EnemyState
import ir.hiwa.neonstrike.model.EnemyType
import ir.hiwa.neonstrike.model.GameState
import ir.hiwa.neonstrike.model.LevelConfig
import ir.hiwa.neonstrike.model.Pickup
import ir.hiwa.neonstrike.model.PickupType
import ir.hiwa.neonstrike.model.WeaponState
import ir.hiwa.neonstrike.model.WeaponType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class GameEngine(
    val soundEngine: SoundEngine,
    private val onHaptic: (durationMs: Long) -> Unit
) {
    var state: GameState = GameState.MENU
        private set

    var currentLevelIndex: Int = 0
        private set
    var level: LevelConfig = LevelManager.getLevel(0)
        private set

    // Player transform & stats
    var playerX: Float = 2.5f
        private set
    var playerY: Float = 2.5f
        private set
    var playerAngle: Float = 0f
        private set

    var health: Int = 100
        private set
    var armor: Int = 40
        private set
    var score: Int = 0
        private set
    var totalKills: Int = 0
        private set

    // Weapons
    val weapons: List<WeaponState> = WeaponType.entries.map { WeaponState(it) }
    var currentWeaponIndex: Int = 0
        private set
    val currentWeapon: WeaponState
        get() = weapons[currentWeaponIndex]

    var fireCooldownTimer: Float = 0f
        private set
    var reloadTimer: Float = 0f
        private set
    var isReloading: Boolean = false
        private set

    // Visual feedback timers
    var muzzleFlashTimer: Float = 0f
        private set
    var recoilAmount: Float = 0f
        private set
    var hitMarkerTimer: Float = 0f
        private set
    var damageFlashTimer: Float = 0f
        private set
    var pickupFlashTimer: Float = 0f
        private set
    var walkBobPhase: Float = 0f
        private set
    var bannerText: String = ""
        private set
    var bannerTimer: Float = 0f
        private set

    // Entities
    val enemies = mutableListOf<Enemy>()
    val pickups = mutableListOf<Pickup>()

    // Live inputs
    @Volatile
    var moveForwardInput: Float = 0f
    @Volatile
    var moveStrafeInput: Float = 0f
    @Volatile
    var isFiringHeld: Boolean = false

    private var triggerWasPressed: Boolean = false
    private val rng = Random(System.currentTimeMillis())

    val remainingEnemies: Int
        get() = enemies.count { it.isAlive }

    init {
        loadLevel(0, resetStats = true)
        state = GameState.MENU
    }

    fun startNewGame() {
        score = 0
        totalKills = 0
        health = 100
        armor = 40
        weapons.forEach {
            it.ammoInMag = it.type.magSize
            it.reserveAmmo = it.type.maxReserve / 2
        }
        currentWeaponIndex = 0
        loadLevel(0, resetStats = false)
        state = GameState.PLAYING
    }

    fun startNextLevel() {
        val nextIndex = currentLevelIndex + 1
        if (nextIndex >= LevelManager.levels.size) {
            state = GameState.VICTORY
            return
        }
        health = (health + 25).coerceAtMost(100)
        loadLevel(nextIndex, resetStats = false)
        state = GameState.PLAYING
    }

    fun togglePause() {
        state = when (state) {
            GameState.PLAYING -> GameState.PAUSED
            GameState.PAUSED -> GameState.PLAYING
            else -> state
        }
    }

    fun returnToMenu() {
        state = GameState.MENU
        isFiringHeld = false
        moveForwardInput = 0f
        moveStrafeInput = 0f
    }

    fun toggleMute() {
        soundEngine.isMuted = !soundEngine.isMuted
    }

    fun rotatePlayer(deltaRadians: Float) {
        if (state != GameState.PLAYING) return
        playerAngle = normalizeAngle(playerAngle + deltaRadians)
    }

    fun switchWeapon(index: Int? = null) {
        if (state != GameState.PLAYING) return
        val next = index?.coerceIn(0, weapons.lastIndex)
            ?: ((currentWeaponIndex + 1) % weapons.size)
        if (next != currentWeaponIndex) {
            currentWeaponIndex = next
            isReloading = false
            reloadTimer = 0f
            fireCooldownTimer = 0.18f
            soundEngine.play(SoundEngine.Sfx.RELOAD)
        }
    }

    fun triggerReload() {
        if (state != GameState.PLAYING || isReloading) return
        val w = currentWeapon
        if (w.ammoInMag >= w.type.magSize || w.reserveAmmo <= 0) return
        isReloading = true
        reloadTimer = w.type.reloadTime
        soundEngine.play(SoundEngine.Sfx.RELOAD)
    }

    private fun loadLevel(index: Int, resetStats: Boolean) {
        currentLevelIndex = index
        level = LevelManager.getLevel(index)
        playerX = level.spawnX
        playerY = level.spawnY
        playerAngle = level.spawnAngle

        if (resetStats) {
            health = 100
            armor = 40
            score = 0
            totalKills = 0
        }

        isReloading = false
        reloadTimer = 0f
        fireCooldownTimer = 0f
        muzzleFlashTimer = 0f
        recoilAmount = 0f
        hitMarkerTimer = 0f
        damageFlashTimer = 0f
        pickupFlashTimer = 0f
        isFiringHeld = false

        enemies.clear()
        level.enemies.forEachIndexed { idx, spawn ->
            enemies.add(
                Enemy(
                    id = idx,
                    type = spawn.type,
                    x = spawn.x,
                    y = spawn.y,
                    attackTimer = 0.6f + rng.nextFloat() * 0.7f,
                    animPhase = rng.nextFloat() * 6.28f
                )
            )
        }

        pickups.clear()
        level.pickups.forEachIndexed { idx, spawn ->
            pickups.add(
                Pickup(
                    id = idx,
                    type = spawn.type,
                    x = spawn.x,
                    y = spawn.y,
                    bobPhase = rng.nextFloat() * 6.28f
                )
            )
        }

        showBanner("${level.title}: ${level.subtitle}", 2.6f)
    }

    fun update(dt: Float) {
        if (state != GameState.PLAYING) return

        // Decay visual timers
        if (fireCooldownTimer > 0f) fireCooldownTimer = (fireCooldownTimer - dt).coerceAtLeast(0f)
        if (muzzleFlashTimer > 0f) muzzleFlashTimer = (muzzleFlashTimer - dt).coerceAtLeast(0f)
        if (hitMarkerTimer > 0f) hitMarkerTimer = (hitMarkerTimer - dt).coerceAtLeast(0f)
        if (damageFlashTimer > 0f) damageFlashTimer = (damageFlashTimer - dt).coerceAtLeast(0f)
        if (pickupFlashTimer > 0f) pickupFlashTimer = (pickupFlashTimer - dt).coerceAtLeast(0f)
        if (bannerTimer > 0f) bannerTimer = (bannerTimer - dt).coerceAtLeast(0f)
        recoilAmount = (recoilAmount - dt * 4.5f).coerceAtLeast(0f)

        // Reload progression
        if (isReloading) {
            reloadTimer -= dt
            if (reloadTimer <= 0f) {
                finishReload()
            }
        }

        // Player movement with circular wall collision
        updatePlayerMovement(dt)

        // Weapon firing
        updateWeaponFiring()

        // Pickups
        updatePickups(dt)

        // Enemy AI
        updateEnemies(dt)

        // Level clear check
        if (remainingEnemies == 0 && state == GameState.PLAYING) {
            soundEngine.play(SoundEngine.Sfx.LEVEL_CLEAR)
            state = if (currentLevelIndex >= LevelManager.levels.lastIndex) {
                GameState.VICTORY
            } else {
                GameState.LEVEL_CLEAR
            }
        }
    }

    private fun updatePlayerMovement(dt: Float) {
        val moveSpeed = 3.65f
        val dirX = cos(playerAngle)
        val dirY = sin(playerAngle)
        val rightX = -dirY
        val rightY = dirX

        val rawDx = dirX * moveForwardInput + rightX * moveStrafeInput
        val rawDy = dirY * moveForwardInput + rightY * moveStrafeInput
        val mag = hypot(rawDx, rawDy)
        val normScale = if (mag > 1f) 1f / mag else 1f

        val stepX = rawDx * normScale * moveSpeed * dt
        val stepY = rawDy * normScale * moveSpeed * dt

        val radius = 0.26f
        if (!collidesWithWall(playerX + stepX, playerY, radius)) {
            playerX += stepX
        }
        if (!collidesWithWall(playerX, playerY + stepY, radius)) {
            playerY += stepY
        }

        if (mag > 0.08f) {
            walkBobPhase += dt * 9.5f * mag.coerceAtMost(1f)
        }
    }

    private fun updateWeaponFiring() {
        val weapon = currentWeapon
        val shouldFire = if (weapon.type.autoFire) {
            isFiringHeld
        } else {
            isFiringHeld && !triggerWasPressed
        }
        triggerWasPressed = isFiringHeld

        if (!shouldFire || fireCooldownTimer > 0f || isReloading) return

        if (weapon.ammoInMag <= 0) {
            soundEngine.play(SoundEngine.Sfx.EMPTY)
            fireCooldownTimer = 0.28f
            if (weapon.reserveAmmo > 0) {
                triggerReload()
            }
            return
        }

        // Fire weapon
        weapon.ammoInMag -= 1
        fireCooldownTimer = weapon.type.fireCooldown
        muzzleFlashTimer = 0.075f
        recoilAmount = 1f

        val sfx = when (weapon.type) {
            WeaponType.PISTOL -> SoundEngine.Sfx.PISTOL
            WeaponType.SHOTGUN -> SoundEngine.Sfx.SHOTGUN
            WeaponType.RIFLE -> SoundEngine.Sfx.RIFLE
        }
        soundEngine.play(sfx)
        onHaptic(if (weapon.type == WeaponType.SHOTGUN) 35L else 18L)

        var landedAnyHit = false
        for (p in 0 until weapon.type.pellets) {
            val spreadOffset = (rng.nextFloat() * 2f - 1f) * weapon.type.spread
            val shotAngle = normalizeAngle(playerAngle + spreadOffset)
            if (castBulletRay(shotAngle, weapon.type.range, weapon.type.damage)) {
                landedAnyHit = true
            }
        }

        if (landedAnyHit) {
            hitMarkerTimer = 0.16f
            soundEngine.play(SoundEngine.Sfx.HIT)
        }

        if (weapon.ammoInMag == 0 && weapon.reserveAmmo > 0) {
            triggerReload()
        }
    }

    private fun castBulletRay(angle: Float, maxRange: Float, damage: Int): Boolean {
        val wallDist = castWallDistance(playerX, playerY, angle, maxRange)

        var bestEnemy: Enemy? = null
        var bestDist = wallDist

        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            val dx = enemy.x - playerX
            val dy = enemy.y - playerY
            val dist = hypot(dx, dy)
            if (dist >= bestDist || dist > maxRange || dist < 0.15f) continue

            val angleToEnemy = atan2(dy, dx)
            val diff = abs(normalizeAngle(angleToEnemy - angle))
            val hitRadius = when (enemy.type) {
                EnemyType.DRONE -> 0.36f
                EnemyType.TROOPER -> 0.40f
                EnemyType.MECH -> 0.50f
            }
            val angularHalfWidth = atan2(hitRadius, dist)
            if (diff <= angularHalfWidth) {
                bestDist = dist
                bestEnemy = enemy
            }
        }

        if (bestEnemy != null) {
            bestEnemy.hp -= damage
            bestEnemy.hitFlashTimer = 0.14f
            if (bestEnemy.state == EnemyState.IDLE) {
                bestEnemy.state = EnemyState.CHASE
            }
            if (bestEnemy.hp <= 0) {
                bestEnemy.hp = 0
                bestEnemy.state = EnemyState.DEAD
                bestEnemy.deathTimer = 0f
                score += bestEnemy.type.scoreValue
                totalKills += 1
                soundEngine.play(SoundEngine.Sfx.ENEMY_DEATH)
            }
            return true
        }
        return false
    }

    private fun finishReload() {
        isReloading = false
        reloadTimer = 0f
        val w = currentWeapon
        val needed = w.type.magSize - w.ammoInMag
        val toLoad = needed.coerceAtMost(w.reserveAmmo)
        w.ammoInMag += toLoad
        w.reserveAmmo -= toLoad
    }

    private fun updatePickups(dt: Float) {
        for (pickup in pickups) {
            if (pickup.collected) continue
            pickup.bobPhase += dt * 3.5f
            val dist = hypot(pickup.x - playerX, pickup.y - playerY)
            if (dist < 0.65f) {
                val consumed = when (pickup.type) {
                    PickupType.HEALTH -> {
                        if (health < 100) {
                            health = (health + 35).coerceAtMost(100)
                            showBanner("+35 HEALTH", 1.2f)
                            true
                        } else false
                    }
                    PickupType.ARMOR -> {
                        if (armor < 100) {
                            armor = (armor + 35).coerceAtMost(100)
                            showBanner("+35 ARMOR", 1.2f)
                            true
                        } else false
                    }
                    PickupType.AMMO -> {
                        weapons.forEach { w ->
                            val add = (w.type.magSize * 2)
                            w.reserveAmmo = (w.reserveAmmo + add).coerceAtMost(w.type.maxReserve)
                        }
                        showBanner("+AMMO RESUPPLY", 1.2f)
                        true
                    }
                }
                if (consumed) {
                    pickup.collected = true
                    pickupFlashTimer = 0.25f
                    score += 25
                    soundEngine.play(SoundEngine.Sfx.PICKUP)
                }
            }
        }
    }

    private fun updateEnemies(dt: Float) {
        for (enemy in enemies) {
            if (!enemy.isAlive) {
                enemy.deathTimer += dt
                continue
            }

            if (enemy.hitFlashTimer > 0f) enemy.hitFlashTimer = (enemy.hitFlashTimer - dt).coerceAtLeast(0f)
            if (enemy.attackFlashTimer > 0f) enemy.attackFlashTimer = (enemy.attackFlashTimer - dt).coerceAtLeast(0f)
            enemy.animPhase += dt * 4f

            val dx = playerX - enemy.x
            val dy = playerY - enemy.y
            val dist = hypot(dx, dy)
            val canSeePlayer = dist < 12.5f && hasLineOfSight(enemy.x, enemy.y, playerX, playerY)

            if (enemy.state == EnemyState.IDLE) {
                if (canSeePlayer || dist < 4.5f) {
                    enemy.state = EnemyState.CHASE
                }
                continue
            }

            if (canSeePlayer && dist <= enemy.type.attackRange) {
                enemy.state = EnemyState.ATTACK
                enemy.attackTimer -= dt
                if (enemy.attackTimer <= 0f) {
                    enemy.attackTimer = enemy.type.attackCooldown * (0.85f + rng.nextFloat() * 0.3f)
                    enemy.attackFlashTimer = 0.16f
                    val hitChance = (0.76f - dist * 0.038f).coerceIn(0.28f, 0.78f)
                    if (rng.nextFloat() < hitChance) {
                        applyPlayerDamage(enemy.type.attackDamage)
                    }
                }
            } else {
                enemy.state = EnemyState.CHASE
            }

            // Move toward player if not too close
            if (dist > 1.85f) {
                val speed = enemy.type.speed * (if (enemy.state == EnemyState.ATTACK) 0.45f else 1f)
                val stepX = (dx / dist) * speed * dt
                val stepY = (dy / dist) * speed * dt
                val radius = 0.28f
                if (!collidesWithWall(enemy.x + stepX, enemy.y, radius)) {
                    enemy.x += stepX
                }
                if (!collidesWithWall(enemy.x, enemy.y + stepY, radius)) {
                    enemy.y += stepY
                }
            }
        }
    }

    private fun applyPlayerDamage(amount: Int) {
        if (state != GameState.PLAYING) return
        val absorbed = if (armor > 0) {
            (amount * 0.55f).toInt().coerceAtMost(armor)
        } else 0
        armor -= absorbed
        health = (health - (amount - absorbed)).coerceAtLeast(0)
        damageFlashTimer = 0.34f
        soundEngine.play(SoundEngine.Sfx.PLAYER_HURT)
        onHaptic(45L)

        if (health <= 0) {
            state = GameState.GAME_OVER
            isFiringHeld = false
        }
    }

    private fun showBanner(text: String, duration: Float) {
        bannerText = text
        bannerTimer = duration
    }

    private fun collidesWithWall(x: Float, y: Float, radius: Float): Boolean {
        val minX = floor(x - radius).toInt()
        val maxX = floor(x + radius).toInt()
        val minY = floor(y - radius).toInt()
        val maxY = floor(y + radius).toInt()
        for (gy in minY..maxY) {
            for (gx in minX..maxX) {
                if (level.isWall(gx, gy)) return true
            }
        }
        return false
    }

    private fun hasLineOfSight(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1
        val dist = hypot(dx, dy)
        if (dist < 0.05f) return true
        val angle = atan2(dy, dx)
        val wallDist = castWallDistance(x1, y1, angle, dist)
        return wallDist >= dist - 0.12f
    }

    private fun castWallDistance(startX: Float, startY: Float, angle: Float, maxDist: Float): Float {
        val rayDirX = cos(angle)
        val rayDirY = sin(angle)

        var mapX = floor(startX).toInt()
        var mapY = floor(startY).toInt()

        val deltaDistX = if (abs(rayDirX) < 1e-6f) 1e6f else abs(1f / rayDirX)
        val deltaDistY = if (abs(rayDirY) < 1e-6f) 1e6f else abs(1f / rayDirY)

        val stepX: Int
        var sideDistX: Float
        if (rayDirX < 0f) {
            stepX = -1
            sideDistX = (startX - mapX) * deltaDistX
        } else {
            stepX = 1
            sideDistX = (mapX + 1f - startX) * deltaDistX
        }

        val stepY: Int
        var sideDistY: Float
        if (rayDirY < 0f) {
            stepY = -1
            sideDistY = (startY - mapY) * deltaDistY
        } else {
            stepY = 1
            sideDistY = (mapY + 1f - startY) * deltaDistY
        }

        var dist = 0f
        var steps = 0
        while (dist < maxDist && steps < 48) {
            steps++
            if (sideDistX < sideDistY) {
                dist = sideDistX
                sideDistX += deltaDistX
                mapX += stepX
            } else {
                dist = sideDistY
                sideDistY += deltaDistY
                mapY += stepY
            }
            if (level.isWall(mapX, mapY)) {
                return dist
            }
        }
        return maxDist
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle
        val twoPi = (2.0 * PI).toFloat()
        while (a > PI) a -= twoPi
        while (a < -PI) a += twoPi
        return a
    }
}
