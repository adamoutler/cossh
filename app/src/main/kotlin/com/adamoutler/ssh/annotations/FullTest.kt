package com.adamoutler.ssh.annotations

/**
 * Marks a test as a long-running Full Test.
 * Full tests are excluded from the standard CI/CD pipeline to ensure fast feedback loops.
 * They should be executed periodically or before major releases.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class FullTest
