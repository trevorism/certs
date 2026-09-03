package com.trevorism.controller

import com.trevorism.secure.Secure
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import org.junit.jupiter.api.Test

import java.lang.reflect.Method

import static org.junit.jupiter.api.Assertions.assertTrue

class SecureAnnotationTest {

    private static final List<Class> PUBLIC_BY_DESIGN = [RootController, NotFoundRouteController]
    private static final List<Class> HTTP_METHOD_ANNOTATIONS = [Get, Post, Put, Delete, Patch]

    @Test
    void testEveryRouteDeclaresSecure() {
        findRoutes().each { Method route ->
            assertTrue(route.isAnnotationPresent(Secure),
                    "${route.declaringClass.simpleName}.${route.name} exposes a route with no @Secure annotation")
        }
    }

    @Test
    void testTheReflectionActuallyFindsRoutes() {
        assertTrue(findRoutes().size() >= 6, "expected to find the certificate and rotation routes")
    }

    @Test
    void testEverySecuredControllerOnTheClasspathIsScanned() {
        List<Class> scanned = findSecuredControllers()
        assertTrue(scanned.contains(CertificateController), "CertificateController was not discovered")
        assertTrue(scanned.contains(RotationController), "RotationController was not discovered")
    }

    @Test
    void testOnlyDeliberatelyPublicControllersAreExempt() {
        List<Class> exempt = findControllers().findAll { PUBLIC_BY_DESIGN.contains(it) }
        assertTrue(exempt.size() == PUBLIC_BY_DESIGN.size(),
                "the public-by-design allowlist names a controller that no longer exists: ${PUBLIC_BY_DESIGN - exempt}")
    }

    private static List<Method> findRoutes() {
        return findSecuredControllers().collectMany { Class controller ->
            controller.declaredMethods.findAll { Method method ->
                HTTP_METHOD_ANNOTATIONS.any { method.isAnnotationPresent(it) }
            }
        }
    }

    private static List<Class> findSecuredControllers() {
        return findControllers().findAll { !PUBLIC_BY_DESIGN.contains(it) }
    }

    private static List<Class> findControllers() {
        File packageDirectory = new File(CertificateController.getResource("CertificateController.class").toURI()).parentFile
        return packageDirectory.listFiles()
                .findAll { it.name.endsWith(".class") && !it.name.contains('$') }
                .collect { Class.forName("com.trevorism.controller.${it.name - '.class'}") }
                .findAll { it.isAnnotationPresent(Controller) }
    }
}
