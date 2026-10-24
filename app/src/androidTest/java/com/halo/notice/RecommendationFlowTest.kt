package com.halo.notice

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecommendationFlowTest {
    @Before fun keepTestInForeground() {
        val inst=InstrumentationRegistry.getInstrumentation()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("am start -W -n com.halo.notice/.MainActivity")).use{it.readBytes()}
    }

    @Test fun yunqueReviewsEveryChannelAndPersistsAnExecutableRecommendation() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val app=inst.targetContext.applicationContext as Application
        val storage=Storage(app);assertTrue(storage.apiKey().isNotBlank())
        val previous=storage.read("recommendations.json");val previousProgress=storage.read("recommendation-progress.json")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        val fixtures=(0 until 105).map{i->Channel("fixture$i",12000+i,"测试应用$i",i%2==0,"test$i",
            when(i){0,104->"营销推广";1->"交易物流";else->"账户安全"},"测试元数据",if(i==2)0 else 4,critical=i==3)}
        lateinit var agent:RecommendationAgent
        try {
            inst.runOnMainSync{agent=RecommendationAgent(storage);agent.analyze(fixtures,"只推荐关闭名称精确等于营销推广的通道；其他全部保留。","{}",123,scope)}
            val until=System.currentTimeMillis()+180000
            while(agent.running&&System.currentTimeMillis()<until)Thread.sleep(100)
            assertFalse("分析超时",agent.running)
            assertEquals("分析失败：${agent.error}","",agent.error)
            assertEquals(105,agent.analyzed)
            val bundle=agent.bundle!!
            assertEquals(105,bundle.channelCount)
            val cs=bundle.advice.flatMap{it.channels}
            assertEquals(setOf(fixtures[0].key,fixtures[104].key),cs.map{it.key}.toSet())
            assertTrue(bundle.advice.all{it.mode==ChangeMode.CLOSE})
            assertEquals(bundle,AdviceBundle.from(storage.read("recommendations.json")))
            assertTrue(fixtures.all{it.importance==if(it.id=="test2")0 else 4}) // Analysis never writes system settings.
            val loaded=RecommendationAgent(storage)
            assertEquals(bundle,loaded.bundle)
            val suggestion=bundle.advice.single()
            assertEquals(2,RecommendationRules.remaining(suggestion,fixtures){c,m->Planning.target(c,m,null)}!!.size)
        }finally {inst.runOnMainSync{agent.cancel()};runBlocking{scope.coroutineContext[Job]!!.cancelAndJoin()};storage.write("recommendations.json",previous);storage.write("recommendation-progress.json",previousProgress)}
    }
    @Test fun resumesOnlyUnfinishedPagesFromValidatedCheckpoint() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val app=inst.targetContext.applicationContext as Application
        val storage=Storage(app)
        val previous=storage.read("recommendations.json");val previousProgress=storage.read("recommendation-progress.json")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        val fixtures=(0 until 105).map{i->Channel("resume$i",12000+i,"测试应用$i",false,"test$i",if(i==0||i==104)"营销推广" else "账户安全","测试数据",4)}
        val pref="只推荐关闭名称精确等于营销推广的通道，其他全部保留。"
        val signature=RecommendationRules.fingerprint(fixtures,pref+"{}"+JSONArray(fixtures.map{it.key}))
        val validated=Advice(theme=AdviceTheme.PROMOTIONS,mode=ChangeMode.CLOSE,reason="此前批次已分析的营销通道",channels=listOf(fixtures[0]))
        val checkpoint=AnalysisProgress(signature,listOf((70 until 105).toList()),(0 until 70).toList(),listOf(validated),1)
        lateinit var agent:RecommendationAgent
        try {
            storage.write("recommendation-progress.json",checkpoint.json())
            inst.runOnMainSync{agent=RecommendationAgent(storage);assertTrue(agent.resumable);assertEquals(70,agent.analyzed);agent.analyze(fixtures,pref,"{}",123,scope)}
            val until=System.currentTimeMillis()+180000
            while(agent.running&&System.currentTimeMillis()<until)Thread.sleep(100)
            assertFalse(agent.running);assertEquals("分析错误：${agent.error}","",agent.error)
            assertEquals(105,agent.analyzed)
            assertEquals(setOf(fixtures[0].key,fixtures[104].key),agent.bundle!!.advice.flatMap{it.channels}.map{it.key}.toSet())
            assertFalse(agent.resumable)
            assertEquals(0,storage.read("recommendation-progress.json").length())
        }finally{inst.runOnMainSync{agent.cancel()};runBlocking{scope.coroutineContext[Job]!!.cancelAndJoin()};storage.write("recommendations.json",previous);storage.write("recommendation-progress.json",previousProgress)}
    }

}
