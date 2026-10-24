package com.halo.notice

import android.app.Application
import android.content.*
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.*

class NoticeViewModel(app:Application):AndroidViewModel(app) {
    val storage=Storage(app)
    var channels by mutableStateOf<List<Channel>>(emptyList());private set
    var connected by mutableStateOf(false);private set
    var bridgeStatus by mutableStateOf("请连接 Shizuku");private set
    var scanning by mutableStateOf(false);private set
    var scanProgress by mutableStateOf("");private set
    var busy by mutableStateOf(false);private set
    var message by mutableStateOf("")
    var proposal by mutableStateOf<Proposal?>(null)
    var includeSystem by mutableStateOf(false)
    var dark by mutableStateOf(false)
    var lastScan by mutableLongStateOf(0L);private set
    var history by mutableStateOf<List<Batch>>(emptyList());private set
    var hasKey by mutableStateOf(false);private set
    private val remembered=mutableMapOf<String,Int>()
    val bridge=Bridge(app) { text,ok ->
        val wasConnected=connected;bridgeStatus=text;connected=ok
        if(ok&&!wasConnected&&!scanning)scan()
    }
    val ai=AiAgent(storage,{channels},{p->proposal=p})
    val recommendations=RecommendationAgent(storage)
    init {
        if(BuildConfig.DEBUG) {
            val imported=java.io.File(app.filesDir,"key-import")
            if(imported.exists())try {storage.saveKey(imported.readText())}finally{imported.delete()}
        }
        val settings=storage.read("preferences.json")
        dark=settings.optBoolean("dark");includeSystem=settings.optBoolean("system")
        val j=storage.read("state.json")
        channels=j.optJSONArray("channels")?.objects()?.map{Channel.from(it)}?:emptyList()
        history=storage.read("history.json").optJSONArray("batches")?.objects()?.map{Batch.from(it)}?:emptyList()
        lastScan=j.optLong("lastScan")
        j.optJSONObject("remembered")?.let{obj->obj.keys().forEach{k->remembered[k]=obj.getInt(k)}}
        hasKey=storage.apiKey().isNotBlank()
    }
    val visible get()=channels.filter{includeSystem || !it.system}
    fun preferences(){storage.write("preferences.json",JSONObject().put("dark",dark).put("system",includeSystem))}
    fun analyzeRecommendations() {
        if(busy||scanning||ai.running||recommendations.running){message="请等待当前操作完成";return}
        val context=JSONObject().put("conversation",JSONObject(ai.recommendationContext())).put("recentOperations",JSONArray(history.take(20).map{b->
            JSONObject().put("title",b.title).put("time",b.time).put("edits",JSONArray(b.edits.map{e->JSONObject().put("app",e.channel.app).put("channel",e.channel.name).put("before",e.before).put("after",e.after).put("state",e.state)}))
        })).toString()
        recommendations.analyze(channels,ai.preferences,context,lastScan,viewModelScope)
    }
    fun adviceRemaining(advice:Advice):List<Channel>? {
        if(recommendations.bundle?.preferences!=ai.preferences)return null
        return RecommendationRules.remaining(advice,channels,::target)
    }
    fun reviewAdvice(advice:Advice) {
        val remaining=adviceRemaining(advice)
        if(remaining==null){message="相关设置或通知偏好已变化，请重新分析";return}
        if(remaining.isEmpty()){message="这条建议已经达成";return}
        prepare("云雀建议：${advice.title}",remaining,advice.mode,advice.reason)
    }
    private fun saveState(){storage.write("state.json",JSONObject().put("lastScan",lastScan).put("channels",JSONArray(channels.map{it.json()})).put("remembered",JSONObject(remembered.toMap())))}
    private fun saveHistory(){storage.write("history.json",JSONObject().put("batches",JSONArray(history.map{it.json()})))}
    fun scan() {
        if(scanning||busy||recommendations.running)return
        if(!connected){message="请先连接 Shizuku，扫描手机的真实通知通道";return}
        viewModelScope.launch {
            scanning=true
            try {
                val apps=withContext(Dispatchers.IO){getApplication<Application>().packageManager.getInstalledApplications(0).sortedBy{it.packageName}}
                val result=mutableListOf<Channel>();var failures=0
                for((index,app) in apps.withIndex()) {
                    ensureActive();scanProgress="${index+1} / ${apps.size}"
                    try {
                        val label=withContext(Dispatchers.IO){app.loadLabel(getApplication<Application>().packageManager).toString()}
                        val items=bridge.channels(app.packageName,app.uid)
                        for(j in items.objects()) {
                            j.put("pkg",app.packageName).put("uid",app.uid).put("app",label)
                                .put("system",app.flags and ApplicationInfo.FLAG_SYSTEM!=0)
                            result+=Channel.from(j)
                        }
                    }catch(e:CancellationException){throw e}catch(_:Exception){failures++}
                }
                check(result.isNotEmpty()){ "未读到通知通道，请检查 Shizuku 与 USB 调试（安全设置）" }
                channels=result;lastScan=System.currentTimeMillis();saveState()
                recoverPending()
                message="已读取 ${result.map{it.pkg}.distinct().size} 个应用的 ${result.size} 个通道"+if(failures>0)"；$failures 个应用读取失败，可重试" else ""
            }catch(e:Exception){message=e.message?:"扫描失败"}finally{scanning=false;scanProgress=""}
        }
    }
    private fun recoverPending() {
        var changed=false
        for(batch in history) for(e in batch.edits) if(e.state in listOf("prepared","undo_pending")) {
            val c=channels.find{it.key==e.channel.key&&it.uid==e.channel.uid}
            if(e.state=="undo_pending") {
                if(c?.importance==e.before){e.state="undone";e.error="恢复中断后已回读"}else{e.state="done";e.error="撤销曾中断，请重新撤销"}
            }else {
                e.state=if(c?.importance==e.after)"done" else "failed"
                e.error=if(e.state=="done")"中断后已回读目标状态" else "上次操作中断，未确认写入"
            }
            changed=true
        }
        if(changed){history=history.toList();saveHistory()}
    }
    fun prepare(title:String,list:List<Channel>,mode:ChangeMode,reason:String="") {
        if(busy||scanning||recommendations.running)return
        if(!connected){message="请先连接 Shizuku";return}
        if(list.isEmpty()){message="没有匹配的通知通道";return}
        proposal=Proposal(title,list,mode,reason)
    }
    fun target(c:Channel,mode:ChangeMode)=Planning.target(c,mode,remembered[c.key])
    fun applyProposal(selected:Set<String>) {
        val p=proposal?:return
        if(busy||scanning||!connected||ai.running||recommendations.running)return
        proposal=null
        val edits=Planning.edits(p.copy(channels=p.channels.filter{it.key in selected}),remembered)
        if(edits.isEmpty()){message="所选通道已经是目标状态";return}
        viewModelScope.launch {
            busy=true
            try {
                val batch=Batch(title=p.title,edits=edits)
                history=listOf(batch)+history;saveHistory() // write-ahead journal precedes every side effect
                for(e in edits) {
                    try {
                        if(e.before>0) {remembered[e.channel.key]=e.before;saveState()}
                        bridge.change(e.channel,e.before,e.after)
                        e.state="done"
                        channels=channels.map{if(it.key==e.channel.key)it.copy(importance=e.after) else it}
                    }catch(ex:Exception){e.state="failed";e.error=ex.message?:"修改失败"}
                    saveHistory();saveState()
                }
                history=history.toList()
                message="已验证修改 ${edits.count{it.state=="done"}} 项"+if(edits.any{it.state=="failed"})"，${edits.count{it.state=="failed"}} 项失败，详见记录" else "，可在记录中撤销"
                ai.recordAction(batch)
            }catch(e:Exception){message="保存操作记录失败，已停止：${e.message}"}finally{busy=false}
        }
    }
    fun undo(batch:Batch) {
        if(busy||scanning||!connected||ai.running||recommendations.running){message="请连接 Shizuku 并等待当前操作完成";return}
        viewModelScope.launch {
            busy=true;var restored=0
            try {
                for(e in batch.edits.filter{it.state=="done"}) {
                    e.state="undo_pending";saveHistory()
                    try {
                        bridge.change(e.channel,e.after,e.before)
                        e.state="undone";e.error="";restored++
                        channels=channels.map{if(it.key==e.channel.key)it.copy(importance=e.before) else it}
                    }catch(ex:Exception){e.state="done";e.error="撤销失败：${ex.message}"}
                    saveHistory();saveState()
                }
                history=history.toList();message="已验证恢复 $restored 项；已被其他程序改变的设置会保留"
            }catch(e:Exception){message=e.message?:"撤销中断"}finally{busy=false}
        }
    }
    fun saveKey(key:String){storage.saveKey(key);hasKey=storage.apiKey().isNotBlank();message=if(hasKey)"API Key 已加密保存" else "API Key 已移除"}
    fun openSettings(c:Channel) {
        runCatching { getApplication<Application>().startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE,c.pkg).putExtra(Settings.EXTRA_CHANNEL_ID,c.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure{message="无法打开此通道设置"}
    }
    override fun onCleared(){bridge.close();ai.cancel();recommendations.cancel()}
}
