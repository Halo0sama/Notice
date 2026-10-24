package com.halo.notice

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class DeepSeekClient(private val storage:Storage) {
    @Volatile private var currentCall:Call?=null
    private val client=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS)
        .callTimeout(180,TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).build()
    fun cancel(){currentCall?.cancel()}
    suspend fun request(input:JSONArray,tools:JSONArray?,stream:Boolean,onDelta:(suspend(String)->Unit)?,instructions:String,format:JSONObject?=null,maxOutputTokens:Int=4096):JSONObject = withContext(Dispatchers.IO) {
        val body=JSONObject().put("model","deepseek-flash").put("instructions",instructions).put("input",input)
            .put("stream",stream).put("max_output_tokens",maxOutputTokens).put("reasoning",JSONObject().put("effort","low"))
        if(tools!=null)body.put("tools",tools)
        if(format!=null)body.put("text",JSONObject().put("format",format))
        val request=Request.Builder().url("https://api.deepseek.com/responses")
            .header("Authorization","Bearer ${storage.apiKey()}")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        var attempt=0
        while(true) {
            ensureActive()
            val call=client.newCall(request);currentCall=call
            val response=call.execute()
            response.use { res ->
                if(!res.isSuccessful) {
                    val raw=res.body?.string().orEmpty()
                    if((res.code==429||res.code in 500..599)&&attempt++<2){delay(attempt*1500L);return@use}
                    throw ApiError(res.code,raw.contains("context",true)||raw.contains("token limit",true))
                }
                val source=res.body?:error("API 响应体为空")
                if(!stream)return@withContext JSONObject(source.string())
                var terminal:JSONObject?=null;val data=StringBuilder()
                suspend fun dispatch() {
                    if(data.isEmpty())return
                    val j=JSONObject(data.toString());data.setLength(0)
                    when(j.optString("type")) {
                        "response.output_text.delta"->onDelta?.invoke(j.optString("delta"))
                        "response.completed"->terminal=j.getJSONObject("response")
                        "response.incomplete"->throw IncompleteResponse()
                        "response.failed","error"->error("DeepSeek 生成失败，请重试")
                    }
                }
                source.charStream().buffered().use { reader ->
                    while(true) {
                        ensureActive();val line=reader.readLine()?:break
                        if(line.isEmpty()){dispatch();if(terminal!=null)break}
                        else if(line.startsWith("data:")){if(data.isNotEmpty())data.append('\n');data.append(line.removePrefix("data:").trimStart())}
                    }
                    dispatch()
                }
                return@withContext terminal?:throw IOException("流式连接提前结束，请重试")
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }
    class ApiError(val code:Int,val contextOverflow:Boolean):IOException(when(code){401->"API Key 无效，请检查设置";402->"DeepSeek 余额不足";429->"请求过于频繁，请稍后重试";400->if(contextOverflow)"上下文超限" else "API 请求参数不被支持";else->"DeepSeek 请求失败（HTTP $code）"})
    class IncompleteResponse:IOException("AI 输出达到上限，未执行修改。请缩小范围后重试。")
}
