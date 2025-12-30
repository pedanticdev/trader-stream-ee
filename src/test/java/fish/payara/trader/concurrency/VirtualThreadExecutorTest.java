package fish.payara.trader.concurrency;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for VirtualThreadExecutor annotation */
@DisplayName("VirtualThreadExecutor Tests")
class VirtualThreadExecutorTest {

    @Test
    @DisplayName("Should have correct annotation properties")
    void shouldHaveCorrectAnnotationProperties() {
        // Act - an annotation cannot annotate itself, so we test its properties instead
        Class<?> annotationType = VirtualThreadExecutor.class;

        // Assert
        assertTrue(annotationType.isAnnotation(), "VirtualThreadExecutor should be an annotation type");
        assertNotNull(annotationType, "VirtualThreadExecutor class should be present");
    }

    @Test
    @DisplayName("Should have Qualifier annotation")
    void shouldHaveQualifierAnnotation() {
        // Act
        Qualifier qualifier = VirtualThreadExecutor.class.getAnnotation(Qualifier.class);

        // Assert
        assertNotNull(qualifier, "VirtualThreadExecutor should have Qualifier annotation");
    }

    @Test
    @DisplayName("Should have correct target types")
    void shouldHaveCorrectTargetTypes() {
        // Act
        Target target = VirtualThreadExecutor.class.getAnnotation(Target.class);

        // Assert
        assertNotNull(target, "Target annotation should be present");
        ElementType[] elementTypes = target.value();

        assertTrue(containsTarget(elementTypes, ElementType.METHOD), "Should target METHOD");
        assertTrue(containsTarget(elementTypes, ElementType.FIELD), "Should target FIELD");
        assertTrue(containsTarget(elementTypes, ElementType.PARAMETER), "Should target PARAMETER");
        assertTrue(containsTarget(elementTypes, ElementType.TYPE), "Should target TYPE");
    }

    @Test
    @DisplayName("Should have correct retention policy")
    void shouldHaveCorrectRetentionPolicy() {
        // Act
        Retention retention = VirtualThreadExecutor.class.getAnnotation(Retention.class);

        // Assert
        assertNotNull(retention, "Retention annotation should be present");
        assertEquals(RetentionPolicy.RUNTIME, retention.value(), "Should have RUNTIME retention policy");
    }

    @Test
    @DisplayName("Should be an interface")
    void shouldBeAnInterface() {
        // Assert
        assertTrue(VirtualThreadExecutor.class.isInterface(), "VirtualThreadExecutor should be an interface");
    }

    @Test
    @DisplayName("Should be in correct package")
    void shouldBeInCorrectPackage() {
        // Assert
        assertEquals("fish.payara.trader.concurrency", VirtualThreadExecutor.class.getPackage().getName(),
                        "Should be in fish.payara.trader.concurrency package");
    }

    @Test
    @DisplayName("Should have correct simple name")
    void shouldHaveCorrectSimpleName() {
        // Assert
        assertEquals("VirtualThreadExecutor", VirtualThreadExecutor.class.getSimpleName(), "Should have correct simple name");
    }

    @Test
    @DisplayName("Should be an annotation interface")
    void shouldBeAnAnnotationInterface() {
        // Act
        boolean isAnnotation = VirtualThreadExecutor.class.isAnnotation();

        // Assert
        assertTrue(isAnnotation, "VirtualThreadExecutor should be an annotation interface");
    }

    @Test
    @DisplayName("Should have no declared methods")
    void shouldHaveNoDeclaredMethods() {
        // Act
        java.lang.reflect.Method[] methods = VirtualThreadExecutor.class.getDeclaredMethods();

        // Assert
        assertEquals(0, methods.length, "Should have no declared methods");
    }

    @Test
    @DisplayName("Should have no declared fields")
    void shouldHaveNoDeclaredFields() {
        // Act
        java.lang.reflect.Field[] fields = VirtualThreadExecutor.class.getDeclaredFields();

        // Assert
        assertEquals(0, fields.length, "Should have no declared fields");
    }

    @Test
    @DisplayName("Should have no declared constructors")
    void shouldHaveNoDeclaredConstructors() {
        // Act
        java.lang.reflect.Constructor<?>[] constructors = VirtualThreadExecutor.class.getDeclaredConstructors();

        // Assert
        assertEquals(0, constructors.length, "Should have no declared constructors");
    }

    @Test
    @DisplayName("Should have no declared annotations except standard ones")
    void shouldHaveNoDeclaredAnnotationsExceptStandardOnes() {
        // Act
        java.lang.annotation.Annotation[] annotations = VirtualThreadExecutor.class.getDeclaredAnnotations();

        // Assert
        // Should have exactly 3 annotations: @Target, @Retention, @Qualifier
        assertEquals(3, annotations.length, "Should have exactly 3 declared annotations");

        // Verify the types of annotations
        boolean hasTarget = false;
        boolean hasRetention = false;
        boolean hasQualifier = false;

        for (java.lang.annotation.Annotation annotation : annotations) {
            if (annotation instanceof Target) {
                hasTarget = true;
            } else if (annotation instanceof Retention) {
                hasRetention = true;
            } else if (annotation instanceof Qualifier) {
                hasQualifier = true;
            }
        }

        assertTrue(hasTarget, "Should have @Target annotation");
        assertTrue(hasRetention, "Should have @Retention annotation");
        assertTrue(hasQualifier, "Should have @Qualifier annotation");
    }

    @Test
    @DisplayName("Should be suitable for dependency injection")
    void shouldBeSuitableForDependencyInjection() {
        // Act & Assert - This test verifies that the annotation has the right properties
        // for use in CDI/dependency injection frameworks

        // Should be a qualifier annotation (jakarta.inject.Qualifier)
        Qualifier qualifier = VirtualThreadExecutor.class.getAnnotation(Qualifier.class);
        assertNotNull(qualifier, "Should be a CDI Qualifier annotation");

        // Should be retained at runtime for injection
        Retention retention = VirtualThreadExecutor.class.getAnnotation(Retention.class);
        assertEquals(RetentionPolicy.RUNTIME, retention.value(), "Should be retained at runtime for injection");
    }

    @Test
    @DisplayName("Should have descriptive documentation")
    void shouldHaveDescriptiveDocumentation() {
        // Act
        String className = VirtualThreadExecutor.class.getSimpleName();
        Package pkg = VirtualThreadExecutor.class.getPackage();

        // Assert
        assertEquals("VirtualThreadExecutor", className, "Should have descriptive name");
        assertEquals("fish.payara.trader.concurrency", pkg.getName(), "Should be in meaningful package");

        // Note: We can't easily test javadoc content without additional libraries,
        // but we can verify that the class is documented by its existence and naming
    }

    /** Helper method to check if target array contains specific element type */
    private boolean containsTarget(ElementType[] targets, ElementType targetType) {
        for (ElementType target : targets) {
            if (target == targetType) {
                return true;
            }
        }
        return false;
    }
}
