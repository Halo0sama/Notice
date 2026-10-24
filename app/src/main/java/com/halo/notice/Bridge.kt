package com.halo.notice

import android.content.*
import android.content.pm.PackageManager
import android.os.*
import kotlinx.coroutines.*
import org.json.*
import rikka.shizuku.Shizuku

class Bridge(private val context:Context,private val state:(String,Boolean)->Unit) {
    @Volatile private var service:INoticeService?=null
    private var binding=false
    private var closed=false
    private val handler=Handler(Looper.getMainLooper())
    private val args get()=Shizuku.UserServiceArgs(ComponentName(context,NoticeService::class.java))
        .daemon(false).processNameSuffix("notice").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)
    private val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName?,binder:IBinder?) {
            if(closed)return
            binding=false
            if(binder==null || !binder.pingBinder()){state("系统服务连接失败",false);return}
            service=INoticeService.Stub.asInterface(binder)
            runCatching { binder.linkToDeath({handler.post{if(service?.asBinder()==binder)lost()}},0) }
            state("已连接 Shizuku",true)
        }
        override fun onServiceDisconnected(name:ComponentName?)=lost()
    }
    private val received=Shizuku.OnBinderReceivedListener{connect(false)}
    private val dead=Shizuku.OnBinderDeadListener{handler.post{lost()}}
    private val permission=Shizuku.OnRequestPermissionResultListener { code,result -> if(code==903) {
        if(result==PackageManager.PERMISSION_GRANTED) connect(false) else state("需要 Shizuku 授权才能批量管理",false)
    }}
    init {
        Shizuku.addRequestPermissionResultListener(permission)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addBinderReceivedListenerSticky(received)
    }
    private fun lost(){service=null;binding=false;state("连接已断开，请重新连接",false)}
    fun connect(request:Boolean=true) {
        if(closed)return
        if(Looper.myLooper()!=Looper.getMainLooper()){handler.post{connect(request)};return}
        runCatching {
            check(Shizuku.pingBinder()){ "请先启动 Shizuku" }
            if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) {
                state("等待 Shizuku 授权",false)
                if(request)Shizuku.requestPermission(903)
                return
            }
            if(service?.asBinder()?.pingBinder()==true){state("已连接 Shizuku",true);return}
            if(binding)return
            binding=true;state("正在连接系统服务…",false)
            Shizuku.bindUserService(args,connection)
            handler.postDelayed({if(binding){binding=false;state("连接超时，请重试",false)}},15000)
        }.onFailure { binding=false;state(it.message?:"连接失败",false) }
    }
    suspend fun channels(pkg:String,uid:Int):JSONArray = withContext(Dispatchers.IO) {
        val s=service?:error("请先连接 Shizuku")
        val raw=s.channels(pkg,uid)
        if(raw.startsWith("{"))error(JSONObject(raw).optString("error","读取失败"))
        JSONArray(raw)
    }
    suspend fun change(c:Channel,expected:Int,target:Int) = withContext(Dispatchers.IO) {
        val s=service?:error("Shizuku 已断开")
        val result=JSONObject(s.change(c.pkg,c.uid,c.id,expected,target))
        check(result.optBoolean("verified")){result.optString("error","修改未通过回读校验")}
    }
    fun close() {
        closed=true;handler.removeCallbacksAndMessages(null)
        Shizuku.removeBinderReceivedListener(received);Shizuku.removeBinderDeadListener(dead);Shizuku.removeRequestPermissionResultListener(permission)
        runCatching{Shizuku.unbindUserService(args,connection,false)}
        service=null
    }
}
