package com.halo.notice

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RecommendationRulesTest {
    private fun channel(i:Int,importance:Int=4)=Channel("app$i",12000+i,"应用$i",i%2==0,"channel$i","营销推广","促销通道$i",importance)
    private fun response(vararg refs:Int,mode:String="CLOSE")=JSONObject().put("recommendations",JSONArray().put(JSONObject().put("theme","PROMOTIONS").put("mode",mode).put("reason","明确的促销提醒，保留交易物流").put("refs",JSONArray(refs.toList()))))
    @Test fun partitionsCoverAllChannelsIncludingDisabledAndSystem() {
        val cs=(0 until 251).map{channel(it,if(it%3==0)0 else 4)}
        val pages=RecommendationRules.partition(cs)
        assertTrue(pages.size>1)
        assertEquals(cs,pages.flatten().map{it.second})
        assertEquals((0 until cs.size).toList(),pages.flatten().map{it.first})
        assertTrue(pages.all{it.size<=100})
    }
    @Test fun overflowSplittingPreservesEveryOriginalReference() {
        val page=(0 until 7).map{it to channel(it)}
        val (a,b)=RecommendationRules.split(page)
        assertTrue(a.isNotEmpty());assertTrue(b.isNotEmpty())
        assertEquals(page,a+b)
        assertThrows(IllegalArgumentException::class.java){RecommendationRules.split(page.take(1))}
    }
    @Test fun checkpointsCoverCompletedAndPendingRefsExactlyOnce() {
        val progress=AnalysisProgress("sig",listOf(listOf(2,3),listOf(4)),listOf(0,1),emptyList(),1)
        assertTrue(progress.validFor("sig",5))
        assertFalse(progress.validFor("changed",5))
        assertFalse(progress.copy(pending=listOf(listOf(1,2,3,4))).validFor("sig",5))
        assertFalse(progress.copy(pending=listOf(listOf(3,4))).validFor("sig",5))
        assertEquals(progress,AnalysisProgress.from(progress.json()))
    }
    @Test fun parseAcceptsOnlyRefsInItsOwnPage() {
        assertEquals(listOf(channel(0)),RecommendationRules.parse(response(0),listOf(0 to channel(0))).single().channels)
        assertThrows(IllegalStateException::class.java){RecommendationRules.parse(response(101),listOf(0 to channel(0)))}
    }
    @Test fun protectedAndReactivatingQuietRecommendationsAreRejected() {
        assertThrows(IllegalArgumentException::class.java){RecommendationRules.parse(response(0),listOf(0 to channel(0).copy(critical=true)))}
        assertThrows(IllegalArgumentException::class.java){RecommendationRules.parse(response(0,mode="QUIET"),listOf(0 to channel(0,0)))}
    }
    @Test fun duplicatesAcrossConflictingGroupsAreRejected() {
        val j=response(0);j.getJSONArray("recommendations").put(response(0,mode="QUIET").getJSONArray("recommendations").getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java){RecommendationRules.parse(j,listOf(0 to channel(0)))}
    }
    @Test fun mergeKeepsAllMatchingChannelsAcrossPages() {
        val a=RecommendationRules.parse(response(0),listOf(0 to channel(0)))
        val b=RecommendationRules.parse(response(100),listOf(100 to channel(100)))
        assertEquals(listOf(channel(0),channel(100)),RecommendationRules.merge(a+b).single().channels)
    }
    @Test fun achievedAdviceDisappearsFromPendingWithoutLosingOtherWork() {
        val c1=channel(0);val c2=channel(1)
        val a=Advice(theme=AdviceTheme.PROMOTIONS,mode=ChangeMode.CLOSE,reason="促销",channels=listOf(c1,c2))
        assertEquals(listOf(c2),RecommendationRules.remaining(a,listOf(c1.copy(importance=0),c2)){c,m->Planning.target(c,m,null)})
        assertTrue(RecommendationRules.remaining(a,listOf(c1.copy(importance=0),c2.copy(importance=0))){c,m->Planning.target(c,m,null)}!!.isEmpty())
        assertNull(RecommendationRules.remaining(a,listOf(c1.copy(importance=2),c2)){c,m->Planning.target(c,m,null)})
        assertNull(RecommendationRules.remaining(a,listOf(c1.copy(uid=999),c2)){c,m->Planning.target(c,m,null)})
    }
    @Test fun preferenceAndInventoryChangesInvalidateFingerprints() {
        val cs=listOf(channel(0),channel(1))
        val one=RecommendationRules.fingerprint(cs,"保留物流")
        assertEquals(one,RecommendationRules.fingerprint(cs.reversed(),"保留物流"))
        assertNotEquals(one,RecommendationRules.fingerprint(cs,"保留所有提醒"))
        assertNotEquals(one,RecommendationRules.fingerprint(cs+channel(2),"保留物流"))
    }
    @Test fun bundlesPersistReasonOriginalStateAndIdentity() {
        val a=Advice(theme=AdviceTheme.PROMOTIONS,mode=ChangeMode.CLOSE,reason="促销",channels=listOf(channel(0)))
        val b=AdviceBundle("hash","偏好",123,channelCount=1,appCount=1,advice=listOf(a))
        assertEquals(b,AdviceBundle.from(b.json()))
    }
    @Test fun quietPresetDoesNotReopenDisabledChannelsOrMatchOtherNames() {
        val cs=listOf(channel(0).copy(name="互动消息"),channel(1,0).copy(name="互动消息"),channel(2).copy(name="交易物流"),channel(3,1).copy(name="互动消息"))
        assertEquals(listOf(cs[0]),HomePreset.SOCIAL.matches(cs))
    }
}
