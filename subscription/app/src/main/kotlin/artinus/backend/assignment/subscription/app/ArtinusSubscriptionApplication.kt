package artinus.backend.assignment.subscription.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ArtinusSubscriptionApplication

fun main(args: Array<String>) {
    runApplication<ArtinusSubscriptionApplication>(*args)
}
