package iecxiv.meshfabric.ui.management
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class) class ProfileDisplayNamesTest {
    private fun record(id:String,name:String)=JSONObject().put("_id",id).put("name",name)
    @Test fun removedRecordKeepsItsReceivedNameForFeedback() {
        val names=ProfileDisplayNames();val id="ugrp//very-long-internal-id"
        names.observe(listOf(record(id,"Oficina Madrid")))
        names.observe(emptyList())
        assertEquals("«Oficina Madrid»",names.label(id,true))
        assertFalse(names.label(id,true).contains(id))
    }
    @Test fun missingNamesNeverFallBackToInternalIdentifiers() {
        for(id in listOf("mesh//long","ugrp//long","node//long","user//long")) {
            val names=ProfileDisplayNames();names.observe(listOf(record(id,id)))
            assertFalse(names.label(id,true).contains(id));assertFalse(names.label(id,false).contains(id))
        }
    }
    @Test fun independentContextsDoNotReuseNamesAndDuplicateNamesKeepDistinctKeys() {
        val a=ProfileDisplayNames();val b=ProfileDisplayNames()
        a.observe(listOf(record("mesh//one","Oficina"),record("mesh//two","Oficina")))
        assertEquals("«Oficina»",a.label("mesh//one",true));assertEquals("«Oficina»",a.label("mesh//two",true))
        assertNotEquals(a.label("mesh//one",true),b.label("mesh//one",true))
        a.observe(listOf(record("mesh//two","Almacén")))
        assertEquals("«Oficina»",a.label("mesh//one",true));assertEquals("«Almacén»",a.label("mesh//two",true))
    }
}
