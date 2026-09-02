package com.trevorism.controller

import com.trevorism.secure.Secure
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import org.junit.jupiter.api.Test

import java.lang.reflect.Method

import static org.junit.jupiter.api.Assertions.assertTrue

class SecureAnnotationTest {

    private static final List<Class> SECURED_CONTROLLERS = [CertificateController, RotationController]
    private static final List<Class> HTTP_METHOD_ANNOTATIONS = [Get, Post, Put, Delete, Patch]

    @Test
    void testEveryRouteDeclaresSecure() {
        List<Method> routes = findRoutes()
        routes.each { Method route ->
            assertTrue(route.isAnnotationPresent(Secure),
                    "${route.declaringClass.simpleName}.${route.name} exposes a route with no @Secure annotation")
        }
    }

    @Test
    void testTheReflectionActuallyFindsRoutes() {
        assertTrue(findRoutes().size() >= 6, "expected to find the certificate and rotation routes")
    }

    private static List<Method> findRoutes() {
        return SECURED_CONTROLLERS.collectMany { Class controller ->
            controller.declaredMethods.findAll { Method method ->
                HTTP_METHOD_ANNOTATIONS.any { method.isAnnotationPresent(it) }
            }
        }
    }
}
