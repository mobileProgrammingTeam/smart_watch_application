package com.example.workersafetywatch.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.workersafetywatch.model.WorkerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore 인스턴스를 Context에 바인딩 */
private val Context.workerDataStore: DataStore<Preferences> by preferencesDataStore(name = "worker_prefs")

/**
 * 작업자 정보를 로컬에 저장하고 관리하는 Repository.
 *
 * 스마트폰에서 Data Layer로 전달받은 [WorkerInfo]를
 * DataStore에 영속적으로 저장한다.
 */
class WorkerRepository(private val context: Context) {

    companion object {
        private const val TAG = "WorkerRepository"

        private val KEY_WORKER_ID = stringPreferencesKey("worker_id")
        private val KEY_WORKER_NAME = stringPreferencesKey("worker_name")
        private val KEY_DEPARTMENT = stringPreferencesKey("department")
    }

    /**
     * 저장된 작업자 정보를 Flow로 관찰한다.
     * 저장된 값이 없으면 [WorkerInfo.DEFAULT]를 반환한다.
     */
    val workerInfo: Flow<WorkerInfo> = context.workerDataStore.data.map { prefs ->
        val workerId = prefs[KEY_WORKER_ID]
        val workerName = prefs[KEY_WORKER_NAME]

        if (workerId != null && workerName != null) {
            WorkerInfo(
                workerId = workerId,
                workerName = workerName,
                department = prefs[KEY_DEPARTMENT]
            )
        } else {
            WorkerInfo.DEFAULT
        }
    }

    /**
     * 작업자 정보를 저장한다.
     */
    suspend fun saveWorkerInfo(info: WorkerInfo) {
        Log.d(TAG, "Saving worker info: $info")
        context.workerDataStore.edit { prefs ->
            prefs[KEY_WORKER_ID] = info.workerId
            prefs[KEY_WORKER_NAME] = info.workerName
            info.department?.let { prefs[KEY_DEPARTMENT] = it }
                ?: prefs.remove(KEY_DEPARTMENT)
        }
    }
}
