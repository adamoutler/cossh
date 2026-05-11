tasks.register("resolveAndLockAll") {
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks)
    }
    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}
