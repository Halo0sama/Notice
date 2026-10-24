package com.halo.notice

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

enum class AdviceTheme(val label:String) { PROMOTIONS("营销与促销"), SOCIAL("互动消息"), DISTRACTIONS("频繁打扰"), OTHER("其他通知") }
data class Advice(val id:String=UUID.randomUUID().toString(),val theme:AdviceTheme,val mode:ChangeMode,val reason:String,val channels:List<Channel>) {
    val title get()="${mode.title}${theme.label}"
    fun json()=JSONObject().put("id",id).put("theme",theme.name).put("mode",mode.name).put("reason",reason).put("channels",JSONArray(channels.map{it.json()}))
    companion object {fun from(j:JSONObject)=Advice(j.getString("id"),AdviceTheme.valueOf(j.getString("theme")),ChangeMode.valueOf(j.getString("mode")),j.getString("reason"),j.getJSONArray("channels").objects().map{Channel.from(it)})}
}
data class AdviceBundle(val fingerprint:String,val preferences:String,val scannedAt:Long,val createdAt:Long=System.currentTimeMillis(),val channelCount:Int,val appCount:Int,val advice:List<Advice>) {
    fun json()=JSONObject().put("fingerprint",fingerprint).put("preferences",preferences).put("scannedAt",scannedAt).put("createdAt",createdAt)
        .put("channelCount",channelCount).put("appCount",appCount).put("advice",JSONArray(advice.map{it.json()}))
    companion object {fun from(j:JSONObject)=AdviceBundle(j.getString("fingerprint"),j.getString("preferences"),j.getLong("scannedAt"),j.getLong("createdAt"),j.getInt("channelCount"),j.getInt("appCount"),j.getJSONArray("advice").objects().map{Advice.from(it)})}
}

object RecommendationRules {
    fun split(page:List<Pair<Int,Channel>>):Pair<List<Pair<Int,Channel>>,List<Pair<Int,Channel>>> {
        require(page.size>1)
        val middle=page.size/2
        return page.take(middle) to page.drop(middle)
    }
    fun fingerprint(channels:List<Channel>,preferences:String):String {
        val text=preferences+JSONArray(channels.sortedBy{it.key}.map{it.json()}).toString()
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}
    }
    /** Every channel appears exactly once, including disabled and system channels. */
    fun partition(channels:List<Channel>):List<List<Pair<Int,Channel>>> {
        val pages=mutableListOf<List<Pair<Int,Channel>>>()
        var page=mutableListOf<Pair<Int,Channel>>();var length=0
        channels.forEachIndexed{index,c->
            val size=c.json().toString().length+32
            require(size<=48000){"单个通道的描述过长，无法完整分析"}
            if(page.isNotEmpty()&&(page.size>=100||length+size>24000)){pages+=page;page=mutableListOf();length=0}
            page+=index to c;length+=size
        }
        if(page.isNotEmpty())pages+=page
        return pages
    }
    fun parse(j:JSONObject,page:List<Pair<Int,Channel>>):List<Advice> {
        val refs=page.toMap();val claimed=mutableSetOf<Int>()
        return j.getJSONArray("recommendations").objects().mapNotNull { item ->
            val mode=ChangeMode.valueOf(item.getString("mode"));val theme=AdviceTheme.valueOf(item.getString("theme"))
            val indices=item.getJSONArray("refs").let{a->(0 until a.length()).map{a.getInt(it)}.distinct()}
            val cs=indices.map { ref ->
                require(claimed.add(ref)){"云雀返回了重复的通道建议，请重试"}
                val c=refs[ref]?:error("云雀返回了本批次之外的通道，请重试")
                require(!c.critical){"云雀建议涉及系统保护通道，请重试"}
                require(mode!=ChangeMode.QUIET||c.enabled){"不能通过静默重新打开已关闭通道"}
                c
            }.filter{c->when(mode){ChangeMode.CLOSE->c.enabled;ChangeMode.QUIET->c.importance>2;ChangeMode.OPEN->!c.enabled}}
            if(cs.isEmpty())null else Advice(theme=theme,mode=mode,reason=item.getString("reason").take(1500),channels=cs)
        }
    }
    fun merge(items:List<Advice>)=items.groupBy{it.theme to it.mode}.map{(group,parts)->Advice(theme=group.first,mode=group.second,
        reason=parts.map{it.reason}.distinct().joinToString("\n\n"),channels=parts.flatMap{it.channels}.distinctBy{it.key})}
    /** Empty means achieved; null means changed outside the suggested transition. */
    fun remaining(advice:Advice,live:List<Channel>,target:(Channel,ChangeMode)->Int):List<Channel>? {
        val byKey=live.associateBy{it.key};val pending=mutableListOf<Channel>()
        for(before in advice.channels) {
            val now=byKey[before.key]?:return null
            if(now.uid!=before.uid||now.critical||now.copy(importance=before.importance)!=before)return null
            val after=target(before,advice.mode)
            if(now.importance==after)continue
            if(now.importance!=before.importance)return null
            pending+=now
        }
        return pending
    }
}

enum class HomePreset(val title:String,val category:String,val mode:ChangeMode,val action:String) {
    MARKETING("和营销推广\n说声再见","营销推广",ChangeMode.CLOSE,"关闭营销推广"),
    OFFERS("优惠促销\n少一点打扰","优惠促销",ChangeMode.CLOSE,"关闭优惠促销"),
    SOCIAL("互动消息\n安静一点到达","互动消息",ChangeMode.QUIET,"静默互动消息"),
    HOT("热门推广\n不必响起","热门推广",ChangeMode.QUIET,"静默热门推广");
    fun matches(channels:List<Channel>)=channels.filter{it.name==category&&(mode!=ChangeMode.QUIET||it.importance>2)}
}
