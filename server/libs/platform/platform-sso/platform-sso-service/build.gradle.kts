dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
    implementation("org.springframework.data:spring-data-jdbc")
    implementation("org.springframework.security:spring-security-core")
    implementation(project(":server:libs:core:commons:commons-data"))
    implementation(project(":server:libs:core:tenant:tenant-api"))
    implementation(project(":server:libs:platform:platform-api"))
    implementation(project(":server:libs:platform:platform-sso:platform-sso-api"))
}
