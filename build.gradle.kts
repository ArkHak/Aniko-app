// Все плагины подключаются через convention-плагины из included build `build-logic`,
// поэтому корневой скрипт намеренно пустой (никаких `alias(...) apply false`,
// иначе плагины попадут на classpath дважды).

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
