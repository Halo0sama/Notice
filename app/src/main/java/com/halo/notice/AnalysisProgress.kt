package com.halo.notice

import org.json.JSONArray
import org.json.JSONObject

/** Checkpoints contain only validated pages; pending refs cover the remainder exactly once. */
data class AnalysisProgress(val signature:String,val pending:List<List<Int>>,val completed:List<Int>,val collected:List<Advice>,val completedPages:Int) {
    fun json()=JSONObject().put("signature",signature).put("pending",JSONArray(pending.map{JSONArray(it)})).put("completed",JSONArray(completed))
        .put("collected",JSONArray(collected.map{it.json()})).put("completedPages",completedPages)
    fun validFor(signature:String,count:Int):Boolean {
        val refs=pending.flatten()+completed
        return this.signature==signature&&refs.size==count&&refs.toSet()==(0 until count).toSet()&&refs.distinct().size==refs.size&&pending.all{it.isNotEmpty()}
    }
    companion object {
        fun from(j:JSONObject):AnalysisProgress {
            fun ints(a:JSONArray)=(0 until a.length()).map{a.getInt(it)}
            val p=j.getJSONArray("pending")
            return AnalysisProgress(j.getString("signature"),(0 until p.length()).map{ints(p.getJSONArray(it))},ints(j.getJSONArray("completed")),
                j.getJSONArray("collected").objects().map{Advice.from(it)},j.getInt("completedPages"))
        }
    }
}
