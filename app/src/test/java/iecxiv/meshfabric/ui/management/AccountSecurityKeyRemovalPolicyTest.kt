package iecxiv.meshfabric.ui.management

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class AccountSecurityKeyRemovalPolicyTest {
    private fun parse(s:String)=accountSecurityKeyChoices(JSONArray(s))
    @Test fun duplicateNamesRemainDistinctById() {val rows=requireNotNull(parse("""[{"i":7,"name":"Llave"},{"i":9,"name":"Llave"}]"""));assertEquals(listOf(7L,9L),rows.map {it.id});assertTrue(accountSecurityKeyCanRemove(rows,rows[1],true))}
    @Test fun invalidAndDuplicateIdsBlockTheList() {for(s in listOf("""[{"name":"A"}]""","""[{"i":1},{"i":1}]""","""[{"i":-1}]""","""[{"i":1.5}]""")) assertNull(parse(s))}
    @Test fun missingListIsNotEmptyList() {assertNull(accountSecurityKeyChoices(null));assertEquals(emptyList<AccountSecurityKeyChoice>(),parse("[]"))}
    @Test fun noSelectionCannotBeConfirmed() {assertFalse(accountSecurityKeyCanRemove(parse("""[{"i":7}]"""),null,true))}
    @Test fun removedOrRenamedSelectionCannotBeConfirmed() {val selected=AccountSecurityKeyChoice(7,"A");assertFalse(accountSecurityKeyCanRemove(parse("[]"),selected,true));assertFalse(accountSecurityKeyCanRemove(parse("""[{"i":7,"name":"B"}]"""),selected,true))}
    @Test fun busyOrDisconnectedStateBlocksRemoval() {val rows=requireNotNull(parse("""[{"i":7,"name":"A"}]"""));assertFalse(accountSecurityKeyCanRemove(rows,rows[0],false))}
    @Test fun unnamedKeyIsStillSelectableById() {val rows=requireNotNull(parse("""[{"i":0}]"""));assertEquals(AccountSecurityKeyChoice(0,""),rows.single());assertTrue(accountSecurityKeyCanRemove(rows,rows[0],true))}
    @Test fun malformedNamesAreNotSilentlyRelabeled() {assertNull(parse("""[{"i":7,"name":123}]"""));assertNull(parse("""[{"i":7,"name":"bad\nname"}]"""))}
}
