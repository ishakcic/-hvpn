package com.ihvpn.app

import android.app.Application
import com.ihvpn.app.data.db.AppDatabase
import com.ihvpn.app.repository.ProfileRepository

class IhvpnApplication : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var profileRepository: ProfileRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        profileRepository = ProfileRepository(database)
    }
}
