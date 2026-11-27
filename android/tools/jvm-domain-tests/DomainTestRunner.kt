// Runs every @org.junit.Test method in the given test classes and reports results.
// Exit code 1 if any test fails.
import java.lang.reflect.InvocationTargetException

fun main(args: Array<String>) {
    var passed = 0
    val failures = mutableListOf<String>()
    for (className in args) {
        val clazz = Class.forName(className)
        val methods = clazz.declaredMethods
            .filter { it.isAnnotationPresent(org.junit.Test::class.java) }
            .sortedBy { it.name }
        for (method in methods) {
            val label = "${clazz.simpleName}.${method.name}"
            try {
                val instance = clazz.getDeclaredConstructor().newInstance()
                method.isAccessible = true
                method.invoke(instance)
                passed++
                println("PASS  $label")
            } catch (e: InvocationTargetException) {
                val cause = e.targetException ?: e
                failures += "$label -> ${cause::class.java.simpleName}: ${cause.message}"
                println("FAIL  $label -> ${cause::class.java.simpleName}: ${cause.message}")
            } catch (e: Throwable) {
                failures += "$label -> ${e::class.java.simpleName}: ${e.message}"
                println("FAIL  $label -> ${e::class.java.simpleName}: ${e.message}")
            }
        }
    }
    println("\n${passed} passed, ${failures.size} failed")
    if (failures.isNotEmpty()) {
        failures.forEach { println(" - $it") }
        kotlin.system.exitProcess(1)
    }
}
