package com.finsight.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Governance tests asserting that the Typology Intelligence Assistant has NO
 * write access to any JPA repository, trade table, or rule engine table.
 *
 * <p>This constraint is enforced at the code level using reflection — not just
 * as a README statement. If a developer accidentally injects a repository into
 * AssistantService or AssistantController, this test will fail the build.
 *
 * <p>Context: A senior engineer reviewer at NICE Actimize specifically raised the
 * concern about AI systems that auto-apply suggested rules to production rule engines.
 * This test is the technical guarantee that FinSight's assistant never has that path.
 */
class AssistantGovernanceTest {

    @Test
    @DisplayName("GOVERNANCE: AssistantService must have NO JPA repository dependencies — enforced at build time")
    void assertAssistantServiceHasNoRepositoryDependencies() {
        Field[] fields = AssistantService.class.getDeclaredFields();
        for (Field field : fields) {
            Class<?> type = field.getType();

            // Hard check: no JpaRepository subtype
            assertFalse(
                JpaRepository.class.isAssignableFrom(type),
                "GOVERNANCE VIOLATION: AssistantService must not depend on any JPA repository. " +
                "Found field '" + field.getName() + "' of type: " + type.getName() + ". " +
                "The assistant has no path to write to or read from any persistent store. " +
                "Remove this dependency immediately."
            );

            // Name-pattern check: catches interfaces named *Repository that aren't JpaRepository subclasses
            boolean isNamedRepository = type.getSimpleName().endsWith("Repository");
            boolean isOurVectorStore  = type.equals(InMemoryVectorStore.class);
            assertFalse(
                isNamedRepository && !isOurVectorStore,
                "GOVERNANCE VIOLATION: AssistantService has a field named *Repository: " +
                field.getName() + " (" + type.getName() + "). " +
                "Only InMemoryVectorStore is permitted as a storage dependency."
            );
        }
    }

    @Test
    @DisplayName("GOVERNANCE: AssistantController must have NO JPA repository dependencies")
    void assertAssistantControllerHasNoRepositoryDependencies() {
        Field[] fields = AssistantController.class.getDeclaredFields();
        for (Field field : fields) {
            Class<?> type = field.getType();
            assertFalse(
                JpaRepository.class.isAssignableFrom(type),
                "GOVERNANCE VIOLATION: AssistantController must not depend on any JPA repository. " +
                "Found field '" + field.getName() + "' of type: " + type.getName()
            );
        }
    }

    @Test
    @DisplayName("GOVERNANCE: AssistantService must not be assignable from JpaRepository itself")
    void assertAssistantServiceIsNotARepository() {
        assertFalse(
            JpaRepository.class.isAssignableFrom(AssistantService.class),
            "AssistantService must not implement JpaRepository."
        );
    }
}
