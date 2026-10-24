package com.halo.notice

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Channel(
    val pkg: String, val uid: Int, val app: String, val system: Boolean,
    val id: String, val name: String, val description: String, val importance: Int,
    val appEnabled: Boolean = true, val groupBlocked: Boolean = false, val critical: Boolean = false,
    val sound: Boolean = false, val vibration: Boolean = false, val badge: Boolean = true
) {
    val key get() = "$pkg\u001f$id"
    val enabled get() = importance > 0
    val effective get() = enabled && appEnabled && !groupBlocked
    fun json() = JSONObject().put("pkg",pkg).put("uid",uid).put("app",app).put("system",system)
        .put("id",id).put("name",name).put("description",description).put("importance",importance)
        .put("appEnabled",appEnabled).put("groupBlocked",groupBlocked).put("critical",critical)
        .put("sound",sound).put("vibration",vibration).put("badge",badge)
    companion object {
        fun from(j:JSONObject) = Channel(j.getString("pkg"),j.getInt("uid"),j.getString("app"),j.optBoolean("system"),j.getString("id"),
            j.optString("name"),j.optString("description"),j.getInt("importance"),j.optBoolean("appEnabled",true),j.optBoolean("groupBlocked"),
            j.optBoolean("critical"),j.optBoolean("sound"),j.optBoolean("vibration"),j.optBoolean("badge",true))
    }
}

enum class ChangeMode(val title:String) { CLOSE("关闭"), OPEN("开启"), QUIET("静默") }
data class Proposal(val title:String,val channels:List<Channel>,val mode:ChangeMode,val reason:String="")
data class Edit(val channel:Channel,val before:Int,val after:Int,var state:String="prepared",var error:String="") {
    fun json() = JSONObject().put("channel",channel.json()).put("before",before).put("after",after).put("state",state).put("error",error)
    companion object { fun from(j:JSONObject) = Edit(Channel.from(j.getJSONObject("channel")),j.getInt("before"),j.getInt("after"),j.getString("state"),j.optString("error")) }
}
data class Batch(val id:String=UUID.randomUUID().toString(),val title:String,val time:Long=System.currentTimeMillis(),val edits:List<Edit>) {
    fun json() = JSONObject().put("id",id).put("title",title).put("time",time).put("edits",JSONArray(edits.map{it.json()}))
    companion object { fun from(j:JSONObject) = Batch(j.getString("id"),j.getString("title"),j.getLong("time"),j.getJSONArray("edits").objects().map{Edit.from(it)}) }
}
data class ChatLine(val role:String,val text:String)
fun JSONArray.objects():List<JSONObject> = (0 until length()).mapNotNull{optJSONObject(it)}
fun importanceText(i:Int) = when(i) {0->"已关闭";1->"最低";2->"静默";3->"默认";4,5->"醒目提醒";else->"未知"}

object Planning {
    fun target(c:Channel,mode:ChangeMode,saved:Int?) = when(mode) {
        ChangeMode.CLOSE -> 0
        ChangeMode.QUIET -> 2
        ChangeMode.OPEN -> saved?.takeIf{it in 1..5} ?: 3
    }
    fun edits(p:Proposal,saved:Map<String,Int>):List<Edit> = p.channels.distinctBy{it.key}.filter{!it.critical}.mapNotNull {
        val after=target(it,p.mode,saved[it.key]); if(after==it.importance) null else Edit(it,it.importance,after)
    }
    fun matches(c:Channel,query:String) = query.isBlank() || listOf(c.name,c.description,c.app,c.pkg,c.id).any{it.contains(query,true)}
}
