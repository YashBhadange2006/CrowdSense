package com.example.ble.util

import kotlin.math.pow

object RssiDistanceCalculator {

    private const val DEFAULT_TX_POWER = -59
    private const val N = 2.0

    fun calculateDistance(rssi: Int, txPower: Int = DEFAULT_TX_POWER): Double {
        if (rssi == 0) return -1.0
        //Log-Distance Path Loss Model - Calculate the dist btw transmitter and receiver
        val distance =  10.0.pow((txPower - rssi) / (10.0 * N))

        return distance.coerceAtMost(100.0)

    }


//    /**
//     * Derives a crowd pressure score from cellular signal metrics.
//     * Called from BleScanner to combine with BLE crowd score.
//     *
//     * Logic: In crowded areas, more devices compete for the same cell tower,
//     * causing RSRP to drop and RSRQ to degrade. We measure that degradation
//     * relative to a baseline captured when the app first starts.
//     *
//     * @param currentRsrp   Current LTE/5G reference signal power (dBm)
//     * @param rsrq          Signal quality (dB), nullable
//     * @param baselineRsrp  First recorded RSRP value (uncrowded reference)
//     * @return              Crowd pressure score from 0.0 to 5.0
//     */
    fun calculateCellularCrowdPressure(
        currentRsrp: Int,
        rsrq: Int?,
        baselineRsrp: Int
    ): Float {

        // How much has signal dropped since baseline?
        // Positive value = signal degraded = more crowd pressure
        val rsrpDrop = (baselineRsrp - currentRsrp).toFloat()

        // RSRQ penalty — reflects channel congestion from many simultaneous users
        // > -10 dB  = good quality, no penalty
        // -10 to -15 = fair, slight penalty
        // < -15 dB  = poor, congestion likely
        val rsrqPenalty = when {
            rsrq == null -> 0f
            rsrq > -10   -> 0f
            rsrq > -15   -> 1f
            else         -> 2f
        }

        // Every 5 dBm drop = +1 pressure point, capped at 5
        val pressureFromRsrp = (rsrpDrop / 5f).coerceIn(0f, 5f)

        return (pressureFromRsrp + rsrqPenalty).coerceIn(0f, 5f)
    }

    /**
     * Fallback for 3G/legacy devices that don't expose RSRP/RSRQ.
     * Uses raw dBm value instead.
     */
    fun calculateCellularCrowdPressureLegacy(dbm: Int): Float {
        return when {
            dbm > -70  -> 0f
            dbm > -85  -> 1f
            dbm > -100 -> 2f
            else       -> 3f
        }
    }
}