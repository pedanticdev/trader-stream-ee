package fish.payara.trader.concurrency;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.enterprise.concurrent.ManagedExecutorDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for ConcurrencyConfig class */
@DisplayName("ConcurrencyConfig Tests")
class ConcurrencyConfigTest {

    @Test
    @DisplayName("Should have ApplicationScoped annotation")
    void shouldHaveApplicationScopedAnnotation() {
        // Act
        ApplicationScoped annotation = ConcurrencyConfig.class.getAnnotation(ApplicationScoped.class);

        // Assert
        assertNotNull(annotation, "ApplicationScoped annotation should be present");
    }

    @Test
    @DisplayName("Should have ManagedExecutorDefinition annotation")
    void shouldHaveManagedExecutorDefinitionAnnotation() {
        // Act
        ManagedExecutorDefinition annotation = ConcurrencyConfig.class.getAnnotation(ManagedExecutorDefinition.class);

        // Assert
        assertNotNull(annotation, "ManagedExecutorDefinition annotation should be present");
    }

    @Test
    @DisplayName("Should have correct executor definition properties")
    void shouldHaveCorrectExecutorDefinitionProperties() {
        // Act
        ManagedExecutorDefinition annotation = ConcurrencyConfig.class.getAnnotation(ManagedExecutorDefinition.class);

        // Assert
        assertNotNull(annotation, "Annotation should be present");

        // Check executor name
        assertEquals("java:module/concurrent/VirtualThreadExecutor", annotation.name(), "Executor name should match expected value");

        // Check virtual threads enabled
        assertTrue(annotation.virtual(), "Virtual threads should be enabled");

        // Check qualifiers
        assertEquals(1, annotation.qualifiers().length, "Should have exactly one qualifier");
        assertEquals(VirtualThreadExecutor.class, annotation.qualifiers()[0], "Should use VirtualThreadExecutor qualifier");
    }

    @Test
    @DisplayName("Should be a concrete class")
    void shouldBeAConcreteClass() {
        // Assert
        assertFalse(ConcurrencyConfig.class.isInterface(), "ConcurrencyConfig should be a concrete class");
        assertFalse(java.lang.reflect.Modifier.isAbstract(ConcurrencyConfig.class.getModifiers()), "ConcurrencyConfig should not be abstract");
        assertTrue(ConcurrencyConfig.class.isSynthetic() == false, "ConcurrencyConfig should not be synthetic");
    }

    @Test
    @DisplayName("Should be in correct package")
    void shouldBeInCorrectPackage() {
        // Assert
        assertEquals("fish.payara.trader.concurrency", ConcurrencyConfig.class.getPackage().getName(), "Should be in fish.payara.trader.concurrency package");
    }

    @Test
    @DisplayName("Should have correct simple name")
    void shouldHaveCorrectSimpleName() {
        // Assert
        assertEquals("ConcurrencyConfig", ConcurrencyConfig.class.getSimpleName(), "Should have correct simple name");
    }

    @Test
    @DisplayName("Should have public default constructor")
    void shouldHavePublicDefaultConstructor() {
        // Act
        java.lang.reflect.Constructor<?>[] constructors = ConcurrencyConfig.class.getConstructors();

        // Assert
        assertTrue(constructors.length > 0, "Should have at least one constructor");

        // Should have a public no-args constructor
        boolean hasPublicNoArgsConstructor = false;
        for (java.lang.reflect.Constructor<?> constructor : constructors) {
            if (constructor.getParameterCount() == 0 && java.lang.reflect.Modifier.isPublic(constructor.getModifiers())) {
                hasPublicNoArgsConstructor = true;
                break;
            }
        }

        assertTrue(hasPublicNoArgsConstructor, "Should have a public no-args constructor");
    }

    @Test
    @DisplayName("Should create instances successfully")
    void shouldCreateInstancesSuccessfully() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            ConcurrencyConfig config = new ConcurrencyConfig();
            assertNotNull(config, "Should create instance without throwing exception");
        }, "Should be able to create instance");
    }

    @Test
    @DisplayName("Should not implement any interfaces")
    void shouldNotImplementAnyInterfaces() {
        // Act
        Class<?>[] interfaces = ConcurrencyConfig.class.getInterfaces();

        // Assert
        assertEquals(0, interfaces.length, "Should not implement any interfaces");
    }

    @Test
    @DisplayName("Should have correct inheritance hierarchy")
    void shouldHaveCorrectInheritanceHierarchy() {
        // Act
        Class<?> superClass = ConcurrencyConfig.class.getSuperclass();

        // Assert
        assertEquals(Object.class, superClass, "Should extend Object class directly");
    }

    @Test
    @DisplayName("Should be annotated with proper lifecycle scope")
    void shouldBeAnnotatedWithProperLifecycleScope() {
        // Act
        ApplicationScoped applicationScoped = ConcurrencyConfig.class.getAnnotation(ApplicationScoped.class);

        // Assert
        assertNotNull(applicationScoped, "Should have ApplicationScoped annotation");
        assertEquals("jakarta.enterprise.context.ApplicationScoped", applicationScoped.annotationType().getName(), "Should use Jakarta EE ApplicationScoped");
    }

    @Test
    @DisplayName("Should configure virtual threads for executor")
    void shouldConfigureVirtualThreadsForExecutor() {
        // Act
        ManagedExecutorDefinition annotation = ConcurrencyConfig.class.getAnnotation(ManagedExecutorDefinition.class);

        // Assert
        assertNotNull(annotation, "Should have ManagedExecutorDefinition");
        assertTrue(annotation.virtual(), "Should enable virtual threads");
        assertEquals("java:module/concurrent/VirtualThreadExecutor", annotation.name(), "Should use specific jndi name for virtual thread executor");
    }

    @Test
    @DisplayName("Should have VirtualThreadExecutor qualifier")
    void shouldHaveVirtualThreadExecutorQualifier() {
        // Act
        ManagedExecutorDefinition annotation = ConcurrencyConfig.class.getAnnotation(ManagedExecutorDefinition.class);

        // Assert
        assertNotNull(annotation.qualifiers(), "Qualifiers array should not be null");
        assertEquals(1, annotation.qualifiers().length, "Should have exactly one qualifier");
        assertEquals(VirtualThreadExecutor.class, annotation.qualifiers()[0], "Should use VirtualThreadExecutor as qualifier");
    }

    @Test
    @DisplayName("Should have correct JNDI name pattern")
    void shouldHaveCorrectJNDINamePattern() {
        // Act
        ManagedExecutorDefinition annotation = ConcurrencyConfig.class.getAnnotation(ManagedExecutorDefinition.class);
        String jndiName = annotation.name();

        // Assert
        assertNotNull(jndiName, "JNDI name should not be null");
        assertEquals("java:module/concurrent/VirtualThreadExecutor", jndiName, "JNDI name should follow expected pattern");
        assertTrue(jndiName.startsWith("java:module/concurrent/"), "JNDI name should start with expected prefix");
        assertTrue(jndiName.endsWith("VirtualThreadExecutor"), "JNDI name should end with expected suffix");
        assertFalse(jndiName.trim().isEmpty(), "JNDI name should not be empty");
    }

    @Test
    @DisplayName("Should have no declared fields")
    void shouldHaveNoDeclaredFields() {
        // Act
        java.lang.reflect.Field[] fields = ConcurrencyConfig.class.getDeclaredFields();

        // Assert
        assertEquals(0, fields.length, "Should have no declared fields");
    }

    @Test
    @DisplayName("Should have no declared methods")
    void shouldHaveNoDeclaredMethods() {
        // Act
        java.lang.reflect.Method[] methods = ConcurrencyConfig.class.getDeclaredMethods();

        // Assert
        // Java compiler may add synthetic methods, so we check for user-declared methods
        long userMethods = java.util.Arrays.stream(methods).filter(method -> !method.isSynthetic()).count();
        assertEquals(0, userMethods, "Should have no user-declared methods");
    }

    @Test
    @DisplayName("Should have exactly two annotations")
    void shouldHaveExactlyTwoAnnotations() {
        // Act
        java.lang.annotation.Annotation[] annotations = ConcurrencyConfig.class.getAnnotations();

        // Assert
        assertEquals(2, annotations.length, "Should have exactly two annotations");

        // Verify annotation types
        boolean hasApplicationScoped = false;
        boolean hasManagedExecutorDefinition = false;

        for (java.lang.annotation.Annotation annotation : annotations) {
            if (annotation instanceof ApplicationScoped) {
                hasApplicationScoped = true;
            } else if (annotation instanceof ManagedExecutorDefinition) {
                hasManagedExecutorDefinition = true;
            }
        }

        assertTrue(hasApplicationScoped, "Should have ApplicationScoped annotation");
        assertTrue(hasManagedExecutorDefinition, "Should have ManagedExecutorDefinition annotation");
    }

    @Test
    @DisplayName("Should be suitable for CDI bean registration")
    void shouldBeSuitableForCDIBeanRegistration() {
        // Act & Assert - This test verifies that the class has the right properties
        // for CDI bean registration

        // Should have ApplicationScoped annotation
        ApplicationScoped applicationScoped = ConcurrencyConfig.class.getAnnotation(ApplicationScoped.class);
        assertNotNull(applicationScoped, "Should have ApplicationScoped for CDI");

        // Should have ManagedExecutorDefinition for executor configuration
        ManagedExecutorDefinition managedExecutorDefinition = ConcurrencyConfig.class.getAnnotation(ManagedExecutorDefinition.class);
        assertNotNull(managedExecutorDefinition, "Should have ManagedExecutorDefinition for CDI");

        // Should be a concrete class with public constructor
        assertDoesNotThrow(() -> {
            new ConcurrencyConfig();
        }, "Should be instantiable by CDI");
    }

    @Test
    @DisplayName("Should be properly configured for virtual threads")
    void shouldBeProperlyConfiguredForVirtualThreads() {
        // Act
        ManagedExecutorDefinition definition = ConcurrencyConfig.class.getAnnotation(ManagedExecutorDefinition.class);

        // Assert
        assertTrue(definition.virtual(), "Should enable virtual threads");
        assertEquals("java:module/concurrent/VirtualThreadExecutor", definition.name(), "Should have correct JNDI name for virtual threads");

        // Verify it uses VirtualThreadExecutor qualifier
        assertEquals(1, definition.qualifiers().length);
        assertEquals(VirtualThreadExecutor.class, definition.qualifiers()[0], "Should use VirtualThreadExecutor qualifier");
    }
}
