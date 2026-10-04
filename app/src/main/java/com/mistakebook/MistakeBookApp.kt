package com.mistakebook

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.mistakebook.di.AppContainer
import kotlinx.coroutines.launch

/**
 * Application 入口：通知渠道、依赖容器、冷启动时的残留任务清理与学科预置。
 */
class MistakeBookApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        createReviewChannel()
        container.appScope.launch {
            container.captureTaskRepository.failUnfinished(RecognitionEngineInterruptedMessage)
        }
    }

    private fun createReviewChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_REVIEW,
                getString(R.string.channel_review_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.channel_review_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_REVIEW = "review"
        const val RecognitionEngineInterruptedMessage = "任务被中断"

        @Volatile
        private var instance: MistakeBookApp? = null

        fun from(context: Context): MistakeBookApp =
            instance ?: (context.applicationContext as MistakeBookApp)
    }
}
