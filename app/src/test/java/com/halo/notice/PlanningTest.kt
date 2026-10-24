package com.halo.notice
import org.junit.Assert.*
import org.junit.Test
class PlanningTest {
    private fun channel(id:String,importance:Int=4,critical:Boolean=false)=Channel("fixture",12345,"测试应用",false,id,"营销推广","优惠活动",importance,critical=critical)
    @Test fun closeDoesNotTouchAlreadyClosedOrProtected() {
        val cs=listOf(channel("a"),channel("b",0),channel("c",critical=true))
        val edits=Planning.edits(Proposal("关闭",cs,ChangeMode.CLOSE),emptyMap())
        assertEquals(1,edits.size);assertEquals("a",edits[0].channel.id);assertEquals(4,edits[0].before);assertEquals(0,edits[0].after)
    }
    @Test fun openRestoresExactSavedImportance() {
        val c=channel("a",0)
        assertEquals(4,Planning.target(c,ChangeMode.OPEN,mapOf(c.key to 4)[c.key]))
        assertEquals(3,Planning.target(c,ChangeMode.OPEN,null))
        assertEquals(3,Planning.target(c,ChangeMode.OPEN,0))
    }
    @Test fun duplicateReferencesAreAppliedOnce() {
        val c=channel("a")
        assertEquals(1,Planning.edits(Proposal("关闭",listOf(c,c),ChangeMode.CLOSE),emptyMap()).size)
    }
    @Test fun silenceKeepsAnUndoableOriginalLevel() {
        val edit=Planning.edits(Proposal("静默",listOf(channel("a")),ChangeMode.QUIET),emptyMap()).single()
        assertEquals(4,edit.before);assertEquals(2,edit.after)
    }
    @Test fun appAndGroupGatesAreSeparateFromChannelSwitch() {
        assertTrue(channel("a").enabled)
        assertFalse(channel("a").copy(appEnabled=false).effective)
        assertFalse(channel("a").copy(groupBlocked=true).effective)
        assertFalse(channel("a",0).effective)
    }
    @Test fun journalRoundTripRetainsIdentityOriginalAndOutcome() {
        val batch=Batch(title="可撤销",edits=listOf(Edit(channel("a"),4,0,"done")))
        val copy=Batch.from(batch.json())
        assertEquals(batch.id,copy.id);assertEquals(batch.edits[0].channel,copy.edits[0].channel)
        assertEquals(4,copy.edits[0].before);assertEquals("done",copy.edits[0].state)
    }
    @Test fun searchMatchesDescriptionAndApp() {
        val c=channel("a");assertTrue(Planning.matches(c,"优惠"));assertTrue(Planning.matches(c,"测试应用"));assertFalse(Planning.matches(c,"物流"))
    }
}
