package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class UserAdministrationPolicyTest {
@Test fun lockPreservesOtherRights() { assertEquals(1066L,userLockMask(1034L,true)); assertEquals(1034L,userLockMask(1066L,false)) }
@Test fun fullAdministratorsAreNotLockMasks() { assertNull(userLockMask(-1L,true)); assertNull(userLockMask(0xffffffffL,false)); assertFalse(userIsLocked(-1L)) }
@Test fun lockStateIsExplicit() { assertTrue(userIsLocked(32L)); assertFalse(userIsLocked(2L)); assertEquals(0L,userLockMask(32L,false)) }
@Test fun creationRequiresEmailAndMatchingPasswords() { assertTrue(validUserCreation("alice","alice@example.com","secret","secret")); assertFalse(validUserCreation("alice","alice@example.com","secret","different")); assertFalse(validUserCreation("alice","invalid","secret","secret")); assertFalse(validUserCreation("a/b","a@b.com","secret","secret")) }
@Test fun defaultDomainIsPreserved() { assertEquals("",userDomain("user//alice")); assertEquals("example",userDomain("user/example/alice")) }
}
