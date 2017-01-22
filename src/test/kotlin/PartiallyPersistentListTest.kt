import org.junit.Assert.*
import org.junit.Test

class PartiallyPersistentListTest {

    @Test fun basicAdd() {
        val list = PartiallyPersistentList<Int>()
        list.add(0)
        list.add(1)

        val result = list.toList()
        assertEquals(listOf(0, 1), result)
    }

    @Test fun testOlderVersion() {
        val list = PartiallyPersistentList<Int>()
        val h0 = list.add(0)
        val h1 = list.add(1)
        val h2 = list.add(2)

        val r0 = list.snapshot(h0.version)
        val r1 = list.snapshot(h1.version)
        val r2 = list.snapshot(h2.version)

        assertEquals(listOf(0), r0)
        assertEquals(listOf(0, 1), r1)
        assertEquals(listOf(0, 1, 2), r2)
    }

    @Test fun severalVersionsDistance() {
        val list = PartiallyPersistentList<Int>()
        list.add(1)
        list.add(2)
        list.add(3)

        val h4 = list.add(4)

        list.add(5)
        list.add(6)
        list.add(7)

        val h8 = list.add(8)

        assertEquals(listOf(1, 2, 3, 4), list.snapshot(h4.version))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), list.snapshot(h8.version))
    }

    @Test fun addFirst() {
        val list = PartiallyPersistentList<Int>()
        list.add(1)
        list.add(2)
        list.add(3)

        list.addFirst(-1)
        list.addFirst(-2)
        list.addFirst(-3)

        assertEquals(listOf(-3, -2, -1, 1, 2, 3), list.toList())
    }

    @Test fun set() {
        val list = PartiallyPersistentList<Int>()
        list.add(1)
        list.add(2)
        val h3 = list.add(3)
        val h4 = list.set(h3, 4)
        list.add(5)
        assertEquals(listOf(1, 2, 3), list.snapshot(h3.version))
        assertEquals(listOf(1, 2, 4), list.snapshot(h4.version))
        assertEquals(listOf(1, 2, 4, 5), list.toList())
    }

    @Test fun contains() {
        val list = PartiallyPersistentList<Int>()
        list.add(1)
        list.add(2)
        val h3 = list.add(3)
        list.add(4)
        list.add(5)
        list.add(6)
        assertTrue(list.find(4).nodeIsPresent)
        assertFalse(list.find(4, h3.version).nodeIsPresent)
    }

    @Test fun delete() {
        val list = partlyPersistentListOf(1, 2, 3, 4, 5, 6)
        val h = list.find(3)
        list.remove(h)
        assertEquals(listOf(1, 2, 4, 5, 6), list.toList())
    }

    @Test fun addAfter() {
        val list = PartiallyPersistentList<Int>()
        for (i in 1..10) list.add(i)
        val h = list.find(5)
        val h1 = list.addAfter(h, 100)
        list.addAfter(h1, 101)
        assertEquals(listOf(1, 2, 3, 4, 5, 100, 101, 6, 7, 8, 9, 10), list.snapshot())
    }

    @Test(timeout = 5000) fun benchmarkAdd() {
        val list = PartiallyPersistentList<Int>()
        for (i in 1..1 * 1000 * 1000)
            list.add(i)
        assertEquals((1..1 * 1000 * 1000).toList(), list.toList())
    }

    @Test(timeout = 5000) fun benchmarkAddFirst() {
        val list = PartiallyPersistentList<Int>()
        for (i in 1..1 * 1000 * 1000)
            list.addFirst(i)
        assertEquals((1..1 * 1000 * 1000).toList().reversed(), list.toList())
    }

    @Test(timeout = 5000) fun benchmarkRemoveByHandle() {
        val list = PartiallyPersistentList<Int>()
        val handlesToRemove = mutableListOf<NodeHandle<Int>>()
        for (i in 1..1 * 1000 * 1000) {
            val h = list.add(i)
            if (i % 5 == 0)
                handlesToRemove.add(h)
        }
        for (h in handlesToRemove) {
            list.remove(list.actualHandleFor(h))
        }
        assertEquals((1..1 * 1000 * 1000).toList().filter { it % 5 != 0 }, list.toList())
    }
}