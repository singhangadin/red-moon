/*
 * Copyright (c) 2016 Marien Raat <marienraat@riseup.net>
 *
 *  This file is free software: you may copy, redistribute and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation, either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This file is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 *  details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jmstudios.redmoon.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log

import com.jmstudios.redmoon.event.moveToState
import com.jmstudios.redmoon.helper.DismissNotificationRunnable
import com.jmstudios.redmoon.model.SettingsModel
import com.jmstudios.redmoon.presenter.ScreenFilterPresenter
import com.jmstudios.redmoon.service.ScreenFilterService

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

import org.greenrobot.eventbus.EventBus

class AutomaticFilterChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (DEBUG) Log.i(TAG, "Alarm received")

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val settingsModel = SettingsModel(context.resources, sharedPreferences)

        val turnOn = intent.data.toString() == "turnOnIntent"

        val command = if (turnOn) ScreenFilterService.COMMAND_ON
        else ScreenFilterService.COMMAND_OFF
        EventBus.getDefault().postSticky(moveToState(command))
        cancelAlarm(context, turnOn)
        scheduleNextCommand(context, turnOn)

        // We want to dismiss the notification if the filter is turned off
        // automatically.
        // However, the filter fades out and the notification is only
        // refreshed when this animation has been completed.  To make sure
        // that the new notification is removed we create a new runnable to
        // be excecuted 100 ms after the filter has faded out.
        val handler = Handler()

        val runnable = DismissNotificationRunnable(context)
        handler.postDelayed(runnable, (ScreenFilterPresenter.FADE_DURATION_MS + 100).toLong())


        // TODO: add "&& settingsModel.getUseLocation()"
        if (settingsModel.automaticFilter) {
            val updater = LocationUpdater(context, object : LocationUpdater.updateHandler {
                override fun handleFound() {
                }

                override fun handleFailed() {
                }
            })
            updater.update()
        }
    }


    companion object {
        private val TAG = "AutomaticFilterChange"
        private val DEBUG = false

        // Conveniences
        val scheduleNextOnCommand = { context: Context -> scheduleNextCommand(context, true) }
        val scheduleNextOffCommand = { context: Context -> scheduleNextCommand(context, false) }
        val cancelTurnOnAlarm = { context: Context -> cancelAlarm(context, true) }
        val cancelOffAlarm = { context: Context -> cancelAlarm(context, false) }
        val cancelAlarms = { context: Context ->
            cancelAlarm(context, true)
            cancelAlarm(context, false)
        }

        private fun scheduleNextCommand(context: Context, turnOn: Boolean) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val settingsModel = SettingsModel(context.resources, sharedPreferences)

            if (settingsModel.automaticFilter) {
                val time = if (turnOn) settingsModel.automaticTurnOnTime
                           else settingsModel.automaticTurnOffTime

                val intent = if (turnOn) Intent(context, AutomaticFilterChangeReceiver::class.java)
                             else Intent(context, AutomaticFilterChangeReceiver::class.java)
                intent.data = if (turnOn) Uri.parse("turnOnIntent")
                              else Uri.parse("offIntent")

                intent.putExtra("turn_on", turnOn)

                val calendar = GregorianCalendar()
                calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(time.split(":".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()[0]))
                calendar.set(Calendar.MINUTE, Integer.parseInt(time.split(":".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()[1]))

                val now = GregorianCalendar()
                now.add(Calendar.SECOND, 1)
                if (calendar.before(now)) {
                    calendar.add(Calendar.DATE, 1)
                }
                calendar.timeZone = TimeZone.getTimeZone("UTC")

                if (DEBUG) Log.i(TAG, "Scheduling alarm for " + calendar.toString())

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)

                if (android.os.Build.VERSION.SDK_INT >= 19) {
                    alarmManager.setExact(AlarmManager.RTC, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC, calendar.timeInMillis, pendingIntent)
                }

            }
        }

        private fun cancelAlarm(context: Context, turnOn: Boolean) {
            val commands = Intent(context, AutomaticFilterChangeReceiver::class.java)
            commands.data = if (turnOn) Uri.parse("turnOnIntent")
                            else Uri.parse("offIntent")
            val pendingIntent = PendingIntent.getBroadcast(context, 0, commands, 0)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
        }
    }
}
