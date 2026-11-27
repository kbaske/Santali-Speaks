// Minimal stand-in for org.junit.Test so the domain tests can run on a plain JVM without
// Gradle or the Android SDK. Android Studio uses the real JUnit 4 annotation instead.
package org.junit

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Test
