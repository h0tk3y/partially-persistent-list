import kotlin.system.measureTimeMillis

/**
 * Created by igushs on 1/22/17.
 */

fun prepareN(n: Int, list: PartiallyPersistentList<Int>) {
    for (i in 1..n) {
        list.add(i)
    }
}

fun runN(n: Int, list: PartiallyPersistentList<Int>) {
    val parts = 10
    for (i in 1..parts) {
        list.versionIterator(n / parts * i).asSequence().toList()
    }
}

fun benchmarkN(n: Int): Long {
    val list = PartiallyPersistentList<Int>()
    prepareN(n, list)
    return measureTimeMillis { runN(n, list) }
}

fun main(args: Array<String>) {
//    for (n in (1..19).map { it * 50000 }) {
//        val time = benchmarkN(n)
//        println("$n, $time")
//    }
//
//    for (n in (1..19).map { it * 50000 }) {
//        val time = benchmarkN(n)
//        println("$n, $time")
//    }

    for (n in (1..40).map { it * 5000000 }) {
        val time = benchmarkN(n)
        println("$n, $time")
    }
}