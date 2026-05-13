package de.codevoid.usbcopy.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.codevoid.usbcopy.model.TaskState
import de.codevoid.usbcopy.service.TransferService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransferViewModel(app: Application) : AndroidViewModel(app) {

    private val _tasks = MutableStateFlow<List<TaskState>>(emptyList())
    val tasks: StateFlow<List<TaskState>> = _tasks.asStateFlow()

    private var service: TransferService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as TransferService.LocalBinder).service()
            service = svc
            viewModelScope.launch {
                svc.tasks.collect { _tasks.value = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    fun bind() {
        val intent = Intent(getApplication(), TransferService::class.java)
        getApplication<Application>().bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun cancel() {
        service?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { getApplication<Application>().unbindService(connection) }
    }
}
