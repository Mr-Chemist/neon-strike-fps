package ir.hiwa.neonstrike.model

enum class GameState {
    MENU,
    PLAYING,
    PAUSED,
    LEVEL_CLEAR,
    VICTORY,
    GAME_OVER
}

enum class WeaponType(
    val displayName: String,
    val damage: Int,
    val fireCooldown: Float,
    val magSize: Int,
    val maxReserve: Int,
    val reloadTime: Float,
    val pellets: Int,
    val spread: Float,
    val range: Float,
    val autoFire: Boolean
) {
    PISTOL(
        displayName = "M9 PULSE",
        damage = 28,
        fireCooldown = 0.28f,
        magSize = 12,
        maxReserve = 96,
        reloadTime = 1.15f,
        pellets = 1,
        spread = 0.018f,
        range = 14f,
        autoFire = false
    ),
    SHOTGUN(
        displayName = "SG-8 BREACHER",
        damage = 19,
        fireCooldown = 0.78f,
        magSize = 6,
        maxReserve = 42,
        reloadTime = 1.65f,
        pellets = 6,
        spread = 0.085f,
        range = 9.5f,
        autoFire = false
    ),
    RIFLE(
        displayName = "VX-47 RIFLE",
        damage = 20,
        fireCooldown = 0.11f,
        magSize = 30,
        maxReserve = 150,
        reloadTime = 1.45f,
        pellets = 1,
        spread = 0.032f,
        range = 16f,
        autoFire = true
    )
}

data class WeaponState(
    val type: WeaponType,
    var ammoInMag: Int = type.magSize,
    var reserveAmmo: Int = type.maxReserve / 2
)

enum class EnemyType(
    val displayName: String,
    val maxHp: Int,
    val speed: Float,
    val attackRange: Float,
    val attackCooldown: Float,
    val attackDamage: Int,
    val scoreValue: Int,
    val spriteScale: Float
) {
    DRONE(
        displayName = "Recon Drone",
        maxHp = 55,
        speed = 2.15f,
        attackRange = 7.5f,
        attackCooldown = 1.35f,
        attackDamage = 8,
        scoreValue = 120,
        spriteScale = 0.82f
    ),
    TROOPER(
        displayName = "Armored Trooper",
        maxHp = 95,
        speed = 1.55f,
        attackRange = 9.0f,
        attackCooldown = 1.15f,
        attackDamage = 13,
        scoreValue = 220,
        spriteScale = 1.0f
    ),
    MECH(
        displayName = "Heavy Mech",
        maxHp = 210,
        speed = 1.05f,
        attackRange = 10.5f,
        attackCooldown = 1.45f,
        attackDamage = 20,
        scoreValue = 450,
        spriteScale = 1.18f
    )
}

enum class EnemyState {
    IDLE,
    CHASE,
    ATTACK,
    DEAD
}

data class Enemy(
    val id: Int,
    val type: EnemyType,
    var x: Float,
    var y: Float,
    var hp: Int = type.maxHp,
    var state: EnemyState = EnemyState.IDLE,
    var attackTimer: Float = 0.6f,
    var hitFlashTimer: Float = 0f,
    var attackFlashTimer: Float = 0f,
    var animPhase: Float = 0f,
    var deathTimer: Float = 0f
) {
    val isAlive: Boolean
        get() = state != EnemyState.DEAD && hp > 0
}

enum class PickupType {
    HEALTH,
    AMMO,
    ARMOR
}

data class Pickup(
    val id: Int,
    val type: PickupType,
    val x: Float,
    val y: Float,
    var collected: Boolean = false,
    var bobPhase: Float = 0f
)

data class EnemySpawn(
    val type: EnemyType,
    val x: Float,
    val y: Float
)

data class PickupSpawn(
    val type: PickupType,
    val x: Float,
    val y: Float
)

data class LevelConfig(
    val index: Int,
    val title: String,
    val subtitle: String,
    val map: Array<IntArray>,
    val spawnX: Float,
    val spawnY: Float,
    val spawnAngle: Float,
    val ceilingTopColor: Int,
    val ceilingHorizonColor: Int,
    val floorHorizonColor: Int,
    val floorBottomColor: Int,
    val enemies: List<EnemySpawn>,
    val pickups: List<PickupSpawn>
) {
    val width: Int get() = map[0].size
    val height: Int get() = map.size

    fun isWall(gridX: Int, gridY: Int): Boolean {
        if (gridY < 0 || gridY >= height || gridX < 0 || gridX >= width) return true
        return map[gridY][gridX] > 0
    }

    fun wallAt(gridX: Int, gridY: Int): Int {
        if (gridY < 0 || gridY >= height || gridX < 0 || gridX >= width) return 1
        return map[gridY][gridX]
    }
}
