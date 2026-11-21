package ir.hiwa.neonstrike.level

import android.graphics.Color
import ir.hiwa.neonstrike.model.EnemySpawn
import ir.hiwa.neonstrike.model.EnemyType
import ir.hiwa.neonstrike.model.LevelConfig
import ir.hiwa.neonstrike.model.PickupSpawn
import ir.hiwa.neonstrike.model.PickupType

object LevelManager {

    val levels: List<LevelConfig> by lazy {
        listOf(
            buildSectorOne(),
            buildSectorTwo(),
            buildSectorThree()
        )
    }

    fun getLevel(index: Int): LevelConfig {
        return levels[index.coerceIn(0, levels.lastIndex)]
    }

    // Wall IDs:
    // 0 = Empty space
    // 1 = Industrial Concrete
    // 2 = Neon Cyber Panel (Cyan)
    // 3 = Hazard Steel (Yellow/Black)
    // 4 = Armored Bulkhead (Slate)
    // 5 = Crimson Reactor Core

    private fun buildSectorOne(): LevelConfig {
        val map = arrayOf(
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 2, 2, 1, 1, 1, 1, 1, 1, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 1, 0, 0, 2, 2, 0, 0, 3, 0, 1),
            intArrayOf(1, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 1),
            intArrayOf(1, 0, 0, 2, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 1, 1, 0, 0, 4, 4, 0, 0, 0, 1),
            intArrayOf(1, 1, 3, 0, 0, 1, 1, 0, 0, 0, 4, 0, 0, 0, 0, 1),
            intArrayOf(2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2),
            intArrayOf(2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(1, 0, 4, 4, 0, 0, 3, 0, 0, 3, 0, 0, 1, 0, 0, 1),
            intArrayOf(1, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1),
            intArrayOf(1, 0, 0, 2, 2, 0, 0, 4, 4, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 1),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 2, 2, 1, 1, 1, 1, 1, 1, 1)
        )

        return LevelConfig(
            index = 0,
            title = "SECTOR 01",
            subtitle = "NEON OUTPOST",
            map = map,
            spawnX = 2.5f,
            spawnY = 2.5f,
            spawnAngle = 0.25f,
            ceilingTopColor = Color.rgb(10, 16, 30),
            ceilingHorizonColor = Color.rgb(20, 30, 50),
            floorHorizonColor = Color.rgb(22, 26, 34),
            floorBottomColor = Color.rgb(42, 48, 60),
            enemies = listOf(
                EnemySpawn(EnemyType.DRONE, 8.5f, 3.5f),
                EnemySpawn(EnemyType.DRONE, 13.5f, 5.5f),
                EnemySpawn(EnemyType.TROOPER, 7.5f, 8.5f),
                EnemySpawn(EnemyType.DRONE, 2.5f, 13.5f),
                EnemySpawn(EnemyType.TROOPER, 12.5f, 13.5f),
                EnemySpawn(EnemyType.TROOPER, 10.5f, 10.5f)
            ),
            pickups = listOf(
                PickupSpawn(PickupType.AMMO, 5.0f, 3.5f),
                PickupSpawn(PickupType.HEALTH, 8.5f, 6.5f),
                PickupSpawn(PickupType.ARMOR, 14.0f, 2.5f),
                PickupSpawn(PickupType.AMMO, 2.5f, 11.5f),
                PickupSpawn(PickupType.HEALTH, 14.0f, 13.5f)
            )
        )
    }

    private fun buildSectorTwo(): LevelConfig {
        val map = arrayOf(
            intArrayOf(4, 4, 4, 4, 3, 3, 4, 4, 4, 4, 3, 3, 4, 4, 4, 4),
            intArrayOf(4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4),
            intArrayOf(4, 0, 3, 3, 0, 0, 2, 0, 0, 2, 0, 0, 3, 3, 0, 4),
            intArrayOf(4, 0, 3, 0, 0, 0, 2, 0, 0, 2, 0, 0, 0, 3, 0, 4),
            intArrayOf(3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3),
            intArrayOf(3, 0, 0, 0, 4, 4, 0, 0, 0, 0, 4, 4, 0, 0, 0, 3),
            intArrayOf(4, 0, 2, 0, 4, 5, 0, 0, 0, 0, 5, 4, 0, 2, 0, 4),
            intArrayOf(4, 0, 0, 0, 0, 0, 0, 5, 5, 0, 0, 0, 0, 0, 0, 4),
            intArrayOf(4, 0, 0, 0, 0, 0, 0, 5, 5, 0, 0, 0, 0, 0, 0, 4),
            intArrayOf(4, 0, 2, 0, 4, 5, 0, 0, 0, 0, 5, 4, 0, 2, 0, 4),
            intArrayOf(3, 0, 0, 0, 4, 4, 0, 0, 0, 0, 4, 4, 0, 0, 0, 3),
            intArrayOf(3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3),
            intArrayOf(4, 0, 3, 0, 0, 0, 2, 0, 0, 2, 0, 0, 0, 3, 0, 4),
            intArrayOf(4, 0, 3, 3, 0, 0, 2, 0, 0, 2, 0, 0, 3, 3, 0, 4),
            intArrayOf(4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4),
            intArrayOf(4, 4, 4, 4, 3, 3, 4, 4, 4, 4, 3, 3, 4, 4, 4, 4)
        )

        return LevelConfig(
            index = 1,
            title = "SECTOR 02",
            subtitle = "REACTOR CORE",
            map = map,
            spawnX = 1.8f,
            spawnY = 7.8f,
            spawnAngle = 0f,
            ceilingTopColor = Color.rgb(22, 12, 18),
            ceilingHorizonColor = Color.rgb(42, 22, 28),
            floorHorizonColor = Color.rgb(28, 24, 26),
            floorBottomColor = Color.rgb(52, 42, 44),
            enemies = listOf(
                EnemySpawn(EnemyType.DRONE, 5.5f, 3.5f),
                EnemySpawn(EnemyType.TROOPER, 8.0f, 4.5f),
                EnemySpawn(EnemyType.DRONE, 12.5f, 4.5f),
                EnemySpawn(EnemyType.TROOPER, 12.5f, 8.0f),
                EnemySpawn(EnemyType.MECH, 13.8f, 7.8f),
                EnemySpawn(EnemyType.TROOPER, 8.0f, 11.5f),
                EnemySpawn(EnemyType.DRONE, 5.5f, 13.5f),
                EnemySpawn(EnemyType.TROOPER, 11.5f, 13.5f)
            ),
            pickups = listOf(
                PickupSpawn(PickupType.AMMO, 3.5f, 4.5f),
                PickupSpawn(PickupType.ARMOR, 8.0f, 2.0f),
                PickupSpawn(PickupType.HEALTH, 8.0f, 14.0f),
                PickupSpawn(PickupType.AMMO, 14.2f, 2.0f),
                PickupSpawn(PickupType.HEALTH, 14.2f, 14.0f),
                PickupSpawn(PickupType.AMMO, 6.5f, 7.8f)
            )
        )
    }

    private fun buildSectorThree(): LevelConfig {
        val map = arrayOf(
            intArrayOf(5, 5, 2, 2, 5, 5, 4, 4, 4, 4, 5, 5, 2, 2, 5, 5),
            intArrayOf(5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5),
            intArrayOf(2, 0, 0, 4, 4, 0, 0, 2, 2, 0, 0, 4, 4, 0, 0, 2),
            intArrayOf(2, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 2),
            intArrayOf(5, 0, 4, 0, 0, 3, 0, 0, 0, 0, 3, 0, 0, 4, 0, 5),
            intArrayOf(5, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 5),
            intArrayOf(4, 0, 0, 0, 0, 0, 5, 0, 0, 5, 0, 0, 0, 0, 0, 4),
            intArrayOf(4, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 4),
            intArrayOf(4, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 4),
            intArrayOf(4, 0, 0, 0, 0, 0, 5, 0, 0, 5, 0, 0, 0, 0, 0, 4),
            intArrayOf(5, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 5),
            intArrayOf(5, 0, 4, 0, 0, 3, 0, 0, 0, 0, 3, 0, 0, 4, 0, 5),
            intArrayOf(2, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 2),
            intArrayOf(2, 0, 0, 4, 4, 0, 0, 2, 2, 0, 0, 4, 4, 0, 0, 2),
            intArrayOf(5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5),
            intArrayOf(5, 5, 2, 2, 5, 5, 4, 4, 4, 4, 5, 5, 2, 2, 5, 5)
        )

        return LevelConfig(
            index = 2,
            title = "SECTOR 03",
            subtitle = "CRIMSON CITADEL",
            map = map,
            spawnX = 8.0f,
            spawnY = 14.2f,
            spawnAngle = -1.5708f,
            ceilingTopColor = Color.rgb(12, 8, 22),
            ceilingHorizonColor = Color.rgb(30, 16, 44),
            floorHorizonColor = Color.rgb(22, 18, 30),
            floorBottomColor = Color.rgb(44, 34, 56),
            enemies = listOf(
                EnemySpawn(EnemyType.DRONE, 3.5f, 11.5f),
                EnemySpawn(EnemyType.DRONE, 12.5f, 11.5f),
                EnemySpawn(EnemyType.TROOPER, 5.5f, 8.0f),
                EnemySpawn(EnemyType.TROOPER, 10.5f, 8.0f),
                EnemySpawn(EnemyType.MECH, 8.0f, 7.8f),
                EnemySpawn(EnemyType.TROOPER, 3.5f, 4.5f),
                EnemySpawn(EnemyType.TROOPER, 12.5f, 4.5f),
                EnemySpawn(EnemyType.DRONE, 8.0f, 3.5f),
                EnemySpawn(EnemyType.MECH, 8.0f, 1.8f)
            ),
            pickups = listOf(
                PickupSpawn(PickupType.AMMO, 5.5f, 13.5f),
                PickupSpawn(PickupType.ARMOR, 10.5f, 13.5f),
                PickupSpawn(PickupType.HEALTH, 1.8f, 8.0f),
                PickupSpawn(PickupType.AMMO, 14.2f, 8.0f),
                PickupSpawn(PickupType.HEALTH, 4.5f, 1.8f),
                PickupSpawn(PickupType.ARMOR, 11.5f, 1.8f),
                PickupSpawn(PickupType.AMMO, 8.0f, 5.2f)
            )
        )
    }
}
