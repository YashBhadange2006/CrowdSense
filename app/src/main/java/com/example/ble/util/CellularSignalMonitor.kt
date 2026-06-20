package com.example.ble.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.*
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.ble.util.RssiDistanceCalculator

// All the RSRP, RSSI and cellular values are collected from here and sent to the Crowd Prediction Function.

data class CellularSignalData(
    val rsrp: Int?,
    val rsrq: Int?,
    val sinr: Int?,
    val signalStrength: Int?,
    val crowdPressureScore: Float
)

class CellularSignalMonitor(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    // Baseline captured on first reading — represents uncrowded reference point
    private var baselineRsrp: Int? = null

    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("MissingPermission")
    fun getCurrentSignalData(): CellularSignalData? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getSignalDataModern()
            } else {
                getSignalDataLegacy()
            }
        } catch (e: SecurityException) {
            Log.e("CellularMonitor", "Permission denied", e)
            null
        } catch (e: Exception) {
            Log.e("CellularMonitor", "Error reading cellular signal", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun getSignalDataModern(): CellularSignalData? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val cellInfoList = telephonyManager.allCellInfo ?: return null

        for (cellInfo in cellInfoList) {
            if (!cellInfo.isRegistered) continue

            when (cellInfo) {

                // For 4G Calculation
                is CellInfoLte -> {
                    val signal = cellInfo.cellSignalStrength
                    val rsrp = signal.rsrp
                    val rsrq = signal.rsrq
                    val sinr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        signal.rssnr
                    } else null

                    // Set baseline on first reading
                    if (baselineRsrp == null) baselineRsrp = rsrp

                    // Delegate calculation to RssiDistanceCalculator
                    val crowdPressure = RssiDistanceCalculator.calculateCellularCrowdPressure(
                        currentRsrp = rsrp,
                        rsrq = rsrq,
                        baselineRsrp = baselineRsrp!!
                    )

                    return CellularSignalData(
                        rsrp = rsrp,
                        rsrq = rsrq,
                        sinr = sinr,
                        signalStrength = null,
                        crowdPressureScore = crowdPressure
                    )
                }

                // For 5G Calculation
                is CellInfoNr -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val signal = cellInfo.cellSignalStrength as CellSignalStrengthNr
                        val rsrp = signal.ssRsrp
                        val rsrq = signal.ssRsrq

                        if (baselineRsrp == null) baselineRsrp = rsrp

                        val crowdPressure = RssiDistanceCalculator.calculateCellularCrowdPressure(
                            currentRsrp = rsrp,
                            rsrq = rsrq,
                            baselineRsrp = baselineRsrp!!
                        )

                        return CellularSignalData(
                            rsrp = rsrp,
                            rsrq = rsrq,
                            sinr = signal.ssSinr,
                            signalStrength = null,
                            crowdPressureScore = crowdPressure
                        )
                    }
                }

                is CellInfoWcdma -> {
                    val dbm = cellInfo.cellSignalStrength.dbm

                    // Delegate legacy calculation to RssiDistanceCalculator
                    val crowdPressure =
                        RssiDistanceCalculator.calculateCellularCrowdPressureLegacy(dbm)

                    return CellularSignalData(
                        rsrp = null,
                        rsrq = null,
                        sinr = null,
                        signalStrength = dbm,
                        crowdPressureScore = crowdPressure
                    )
                }
            }
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun getSignalDataLegacy(): CellularSignalData {
        val dbm = (telephonyManager.signalStrength?.level ?: 0) * -20

        return CellularSignalData(
            rsrp = null,
            rsrq = null,
            sinr = null,
            signalStrength = dbm,
            crowdPressureScore = RssiDistanceCalculator.calculateCellularCrowdPressureLegacy(dbm)
        )
    }

    fun resetBaseline() {
        baselineRsrp = null
    }
}
