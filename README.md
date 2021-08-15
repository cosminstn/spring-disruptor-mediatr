![tests](https://github.com/cosmyn9708/spring-disruptor-mediatr/actions/workflows/gradle-test.yml/badge.svg)

# About

Performant Mediator implementation that uses the [Disruptor](https://github.com/LMAX-Exchange/disruptor) to handle commands, queries and events.  
This library is heavily inspired by [github.com/jkratz55/spring-mediatR](https://github.com/jkratz55/spring-mediatR).

## Installation

The maven package is currently published only on GitHub Packages Registry, and you can check it out
[here](https://github.com/cosmyn9708/spring-disruptor-mediatr/packages).  
Installing maven packages might be a might cumbersome at first, but don't get bogged down. I'll list the steps below,
but for more info go check out
the [docs](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#installing-a-package)
.

### Basic steps:

1. Generate a personal access token for `GitHub Package Registry`.

   More info here
   [https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#authenticating-to-github-packages](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#authenticating-to-github-packages)
1. Register the repository of this project

   The only important thing to note is that you need to register the maven repository of this project.  
   Also, GitHub requires you to be authenticated to access that repository, even though both, the project and the package, are public.  
   I strongly recommend you go read the docs [here](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry), and [here](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)  
   Gradle:   
   *build.gradle*
    ```
   ...
   repositories {
      maven {
         url = "https://github.com/cosmyn9708/tech"
         username = YOUR_GITHUB_USERNAME
         password = THE_TOKEN_GENERATED_AT_STEP_1
      }
   }
   ...
   ```
   **WARNING**: The `/tech` in the repository url is **NOT** a mistake! For whatever reason GitHub does that to my domain. Trying to use the repository without the `/tech` at the end will not work!!
1. 
# How to use

For each request (command/query) two classes should be defined:

- One that describes the request itself (Command/Query)
- One that describes how that all instances of that request will be handled (CommandHandler/QueryHandler)

## Kotlin samples

*StoreNumberCommand.kt*

```kotlin
/**
 * This bean is obviously implemented just to illustrate a basic system state.
 * How the state is implemented is up to the developer.
 */
@Component
class NumberStore {

    private val cache = HashSet<Int>()

    fun store(number: Int) {
        cache.add(number)
    }

}

/**
 * Command that changes the system's state by storing the specified number's increment.
 */
class StoreNumberIncrementCommand(val number: Int) : Command<Unit>

@Component
class StoreNumberIncrementCommandHandler(
    @Autowired
    private val store: NumberStore
) : CommandHandler<StoreNumberIncrementCommand, Int> {


    override fun handle(request: StoreNumberIncrementCommand) {
        val increment = request.number + 1
        store.store(increment)
        return increment
    }

}
```

The main piece of the puzzle is the Mediator, which must be registered as a spring bean. Just for simplicity, I've
registered it in

*App.kt*

```kotlin
@SpringBootApplication
class App {
    //...

    @Bean
    fun mediator(@Autowired val context: ApplicationContext): Mediator {
        return DisrutorMediatorImpl(context)
    }
}
```

## Java Samples

