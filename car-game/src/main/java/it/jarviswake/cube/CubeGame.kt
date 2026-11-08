package it.jarviswake.cube

import kotlin.math.*

internal data class Vec3(val x: Double, val y: Double, val z: Double)

/** Real game rules and geometry, independent of an Activity or a Jarvis session. */
internal class CubeGame {
    var yaw = 0.5
        private set
    var pitch = -0.4
        private set
    var score = 0
        private set
    var target = 4
        private set
    var manual = false
        private set

    companion object {
        val vertices = listOf(Vec3(-1.0,-1.0,-1.0), Vec3(1.0,-1.0,-1.0), Vec3(1.0,1.0,-1.0), Vec3(-1.0,1.0,-1.0),
            Vec3(-1.0,-1.0,1.0), Vec3(1.0,-1.0,1.0), Vec3(1.0,1.0,1.0), Vec3(-1.0,1.0,1.0))
        val faces = listOf(intArrayOf(0,1,2,3), intArrayOf(4,5,6,7), intArrayOf(0,3,7,4),
            intArrayOf(1,5,6,2), intArrayOf(0,4,5,1), intArrayOf(3,2,6,7))
        val normals = listOf(Vec3(0.0,0.0,-1.0), Vec3(0.0,0.0,1.0), Vec3(-1.0,0.0,0.0),
            Vec3(1.0,0.0,0.0), Vec3(0.0,-1.0,0.0), Vec3(0.0,1.0,0.0))
        private fun angle(n: Double) = if (n.isFinite()) n % (2.0 * PI) else 0.0
    }

    fun rotate(dx: Double, dy: Double, byPlayer: Boolean = true) {
        yaw = angle(yaw + dx); pitch = angle(pitch + dy)
        if (byPlayer) manual = true
    }
    fun spin(seconds: Double) {
        if (!manual && seconds.isFinite()) rotate(seconds.coerceIn(0.0, 0.05) * 0.38, 0.0, false)
    }
    fun transformed(v: Vec3): Vec3 {
        val x = v.x * cos(yaw) + v.z * sin(yaw)
        val z = -v.x * sin(yaw) + v.z * cos(yaw)
        return Vec3(x, v.y * cos(pitch) - z * sin(pitch), v.y * sin(pitch) + z * cos(pitch))
    }
    fun front() = normals.indices.maxBy { transformed(normals[it]).z }
    fun aligned() = front() + 1 == target && transformed(normals[target - 1]).z >= 0.92
    fun confirm(): Boolean {
        if (!aligned()) return false
        score = (score + 1).coerceAtMost(9999)
        target = target % 6 + 1
        manual = true
        return true
    }
    fun shuffle() {
        rotate(1.1, 0.7, false); manual = false
        if (front() + 1 == target) target = target % 6 + 1
    }
    fun restore(y: Double, p: Double, points: Int, face: Int, touched: Boolean) {
        yaw = angle(y); pitch = angle(p); score = points.coerceIn(0, 9999)
        target = face.coerceIn(1, 6); manual = touched
    }
}
