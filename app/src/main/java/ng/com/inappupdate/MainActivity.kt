package ng.com.inappupdate

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.MutableLiveData
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

class MainActivity : AppCompatActivity() {

    private var firebaseRemoteConfig: FirebaseRemoteConfig?=null
    val appUpdateManager: AppUpdateManager by lazy { AppUpdateManagerFactory.create(this) }
    var updateType: String? = FLEXIBLE


    private val listener: InstallStateUpdatedListener? = InstallStateUpdatedListener { installState ->
        if (installState.installStatus() == InstallStatus.DOWNLOADING) {
            val bytesDownloaded = installState.bytesDownloaded()
            val totalBytesToDownload = installState.totalBytesToDownload()
            // Show update progress bar.
        } else if (installState.installStatus() == InstallStatus.DOWNLOADED){
            showDialogForCompleteUpdate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setUpRemoteConfig()
    }

    private fun setUpRemoteConfig() {
        firebaseRemoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 120 //7200
        }
        firebaseRemoteConfig?.setConfigSettingsAsync(configSettings)
        firebaseRemoteConfig?.setDefaultsAsync(R.xml.remote_config_defaults)

        firebaseRemoteConfig?.fetchAndActivate()
                ?.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val updated = task.result
                        Log.d(TAG, "Config params updated: $updated")

                        updateType = firebaseRemoteConfig?.getString("in_app_update_type")
                        if(updateType == FLEXIBLE) {
                            checkForFlexibleInappUpdate()
                        } else {
                            checkForImmediateAppUpdate()
                        }
                    } else {
                        Log.d(TAG, "Config params update failed")
                    }
                }
    }

    private fun checkForFlexibleInappUpdate() {

        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        // Checks that the platform will allow the specified type of update.
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                appUpdateManager.registerListener(listener)
                // Request the update.
                appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.FLEXIBLE, this, FLEXIBLE_UPDATE_REQUEST_CODE)
            }
        }
    }

    private fun checkForImmediateAppUpdate() {
        // Each AppUpdateInfo instance can be used to start an update only once

        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        // Checks that the platform will allow the specified type of update.
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                // Request the update.
                appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE, this, IMMEDIATE_UPDATE_REQUEST_CODE)
            }
        }
    }

    fun showDialogForCompleteUpdate() {

        val builder = AlertDialog.Builder(this)
        builder.setTitle("App Update")
        builder.setMessage("\nAn update has just been downloaded, you need to restart the application for the update to take effect")
        builder.setPositiveButton("Restart") { dialog, which ->
            appUpdateManager.completeUpdate()
        }
//        builder?.setNegativeButton("No") { dialog, which -> dialog.cancel() }

        builder.show()
    }

    override fun onResume() {
        super.onResume()


        appUpdateManager
                .appUpdateInfo
                .addOnSuccessListener { appUpdateInfo ->

                    when(updateType){
                        FLEXIBLE -> {
                            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                                showDialogForCompleteUpdate()
                            }
                        }

                        IMMEDIATE -> {
                            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                            ) {
                                // If an in-app update is already running, resume the update.
                                appUpdateManager.startUpdateFlowForResult(
                                        appUpdateInfo,
                                        AppUpdateType.IMMEDIATE,
                                        this,
                                        IMMEDIATE_UPDATE_REQUEST_CODE
                                );
                            }
                        }
                    }
                }
    }

    override fun onDestroy() {
        if(updateType == FLEXIBLE) {
            appUpdateManager.unregisterListener(listener)
        }
        super.onDestroy()
    }

    companion object {
        val TAG = MainActivity::class.java.simpleName
        val FLEXIBLE_UPDATE_REQUEST_CODE = 3500
        val IMMEDIATE_UPDATE_REQUEST_CODE = 4500
        val IMMEDIATE = "immediate"
        val FLEXIBLE = "flexible"


    }
}