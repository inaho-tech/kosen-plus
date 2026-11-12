package com.webclass.plus.util

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

object NetworkUtils {
    fun getIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "Unavailable"
                    }
                }
            }
        } catch (e: SocketException) {
            e.printStackTrace()
        }
        return "Unavailable"
    }
}
