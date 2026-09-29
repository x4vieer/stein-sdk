# Stein SDK — Maven repository

The Gradle plugin `dev.xavier.stein.mod` and `dev.xavier.stein:stein-api`, served from this branch.
Use it from `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        maven { url = uri("https://raw.githubusercontent.com/x4vieer/stein-sdk/maven") }
        gradlePluginPortal()
    }
}
```

See the guide on the [main branch](https://github.com/x4vieer/stein-sdk).
