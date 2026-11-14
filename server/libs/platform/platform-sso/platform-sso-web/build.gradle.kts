dependencies {
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework:spring-webmvc")
    implementation("org.springframework.boot:spring-boot")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.security:spring-security-config")
    implementation("org.springframework.security:spring-security-saml2-service-provider")
    implementation("org.springframework.security:spring-security-web")
    implementation(project(":server:libs:core:tenant:tenant-api"))
    implementation(project(":server:libs:platform:platform-api"))
    implementation(project(":server:libs:platform:platform-security-web:platform-security-web-api"))
    implementation(project(":server:libs:platform:platform-sso:platform-sso-api"))
    implementation(project(":server:libs:platform:platform-user:platform-user-api"))

    testImplementation("jakarta.servlet:jakarta.servlet-api")
}
