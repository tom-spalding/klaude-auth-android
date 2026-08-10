# klaude-auth-android

Android helpers for [klaude-auth](https://github.com/tom-spalding/klaude-auth):
`Context`-backed credential storage and the `INTERNET` permission.

OAuth PKCE, loopback callback, token exchange, and refresh live in the JVM core library.

## Install

```kotlin
// build.gradle.kts
implementation("io.github.tom-spalding:klaude-auth-android:0.1.0")
// transitively includes io.github.tom-spalding:klaude-auth
```

Local composite (sibling checkouts + opt-in property):

```kotlin
// settings.gradle.kts in this repo (when developing against local core)
// ./gradlew -PuseLocalKlaudeAuth=true …

// From an app that substitutes both modules:
includeBuild("../klaude-auth") {
    dependencySubstitution {
        substitute(module("io.github.tom-spalding:klaude-auth"))
            .using(project(":klaude-auth"))
    }
}
includeBuild("../klaude-auth-android") {
    dependencySubstitution {
        substitute(module("io.github.tom-spalding:klaude-auth-android"))
            .using(project(":klaude-auth-android"))
    }
}
```

## Usage

```kotlin
val store = FileClaudeCredentialsStore(context)
val repo = DefaultClaudeAuthRepository(store)
val flow = ClaudeAuthFlow()

val creds = flow.refreshOrSignIn(repo.loadCredentials()) { authorizeUrl ->
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl)))
}
repo.saveCredentials(creds)

repo.clearCredentials()
```

Package: `com.tomspalding.klaudeauth` (same as core; Android types live in `…storage`).

## License

MIT
