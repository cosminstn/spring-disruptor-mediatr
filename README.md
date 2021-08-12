# About
Mediator implementation that uses the Disruptor to handle commands, queries and events.

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

The main piece of the puzzle is the Mediator, which must be registered as a spring bean. Just for simplicity, I've registered it in

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

